import Foundation
import os.log

enum BiblioClientError: LocalizedError {
    case http(Int)
    case invalidResponse
    case notFound

    var errorDescription: String? {
        switch self {
        case let .http(status): return "The catalog answered HTTP \(status)."
        case .invalidResponse: return "The catalog answered with something unreadable."
        case .notFound: return "That record does not exist."
        }
    }
}

/// Read-only client for the data behind the platform's book, review, and
/// profile permalinks.
///
/// Catalog records come from Bibliograph, which answers
/// `com.atproto.repo.getRecord` with a hydrated edition view, and user-owned
/// records are read from the owner's PDS — the same authoritative paths the
/// platform uses. Nothing here writes, and nothing is cached beyond the PDS
/// lookup for a DID.
final class BiblioClient {
    static let biblioBase = URL(string: "https://biblio.livtet.olamaelcu.net")!
    static let bskyBase = URL(string: "https://public.api.bsky.app")!
    static let constellationBase = URL(string: "https://constellation.microcosm.blue")!

    static let editionCollection = "community.lexicon.book.edition"
    static let reviewCollection = "net.olamaelcu.livtet.bookReview"
    static let shelvingCollection = "net.olamaelcu.livtet.biblio.bookShelving"
    static let shelfCollection = "net.olamaelcu.livtet.biblio.shelf"

    /// Reviews shown on a book page, matching the platform's page size.
    static let bookReviewLimit = 25
    /// Reviews shown on a profile, matching the platform's page size.
    static let profileReviewLimit = 20

    private static let listPageSize = 100
    private static let maxShelvingPages = 10
    private static let timeout: TimeInterval = 15
    private static let maxConcurrency = 6

    private static let logger = Logger(subsystem: "net.olamaelcu.livtet", category: "BiblioClient")

    private let session: URLSession
    private let biblioBase: URL
    private let bskyBase: URL
    private let constellationBase: URL
    private let decoder = JSONDecoder()

    private let pdsLock = NSLock()
    private var pdsByDid: [String: URL] = [:]

    init(
        session: URLSession = .shared,
        biblioBase: URL = BiblioClient.biblioBase,
        bskyBase: URL = BiblioClient.bskyBase,
        constellationBase: URL = BiblioClient.constellationBase
    ) {
        self.session = session
        self.biblioBase = biblioBase
        self.bskyBase = bskyBase
        self.constellationBase = constellationBase
    }

    // MARK: - Catalog

    /// One catalog edition, as Bibliograph hydrates it.
    func getEdition(atUri: String) async throws -> BiblioBookResponse {
        guard let atUri = AtUri(string: atUri) else {
            throw BiblioClientError.invalidResponse
        }
        return try await get(
            BiblioBookResponse.self,
            base: biblioBase,
            path: "/xrpc/com.atproto.repo.getRecord",
            query: [
                URLQueryItem(name: "repo", value: atUri.did),
                URLQueryItem(name: "collection", value: atUri.collection),
                URLQueryItem(name: "rkey", value: atUri.rkey),
            ]
                )
    }

    /// Cover URL Bibliograph derives from the edition's identifiers.
    func getImageForBook(atUri: String) async throws -> URL? {
        let response = try await get(
            BiblioImageResponse.self,
            base: biblioBase,
            path: "/xrpc/net.olamaelcu.livtet.biblio.getImageForBook",
            query: [URLQueryItem(name: "uri", value: atUri)]
        )
        return response.url.flatMap { URL(string: $0) }
    }

    /// Reviews for one edition.
    ///
    /// The AppView's `getReviewsByBook` is asked first; when it fails or has
    /// nothing, the Constellation backlink index is used and each hit is
    /// hydrated from its author's PDS. The fallback is load-bearing: the
    /// AppView currently returns an empty list for real books.
    func getBookReviews(atUri: String) async throws -> [BiblioReview] {
        if let list = try? await get(
            BiblioReviewList.self,
            base: biblioBase,
            path: "/xrpc/net.olamaelcu.livtet.biblio.getReviewsByBook",
            query: [
                URLQueryItem(name: "book", value: atUri),
                URLQueryItem(name: "limit", value: String(Self.bookReviewLimit)),
            ]
        ), !list.reviews.isEmpty {
            return list.reviews
        }

        let primary = await backlinks(atUri, source: "\(Self.reviewCollection):book.uri")
        let references = primary.isEmpty
            ? await backlinks(atUri, source: "\(Self.reviewCollection):book")
            : primary

        var reviews: [BiblioReview] = []
        let matching = references.filter { $0.collection == Self.reviewCollection }
        for chunk in matching.chunked(into: Self.maxConcurrency) {
            let hydrated = await withTaskGroup(of: BiblioReview?.self) { group in
                for reference in chunk {
                    group.addTask { [self] in
                        try? await getReview(did: reference.did, rkey: reference.rkey)
                    }
                }
                var collected: [BiblioReview] = []
                for await review in group {
                    if let review { collected.append(review) }
                }
                return collected
            }
            reviews.append(contentsOf: hydrated)
        }
        return reviews
    }

    // MARK: - User-owned records

    /// One review, read from its author's PDS. Returns nil when the DID, its
    /// PDS, or the record cannot be resolved.
    func getReview(did: String, rkey: String) async throws -> BiblioReview? {
        guard let pds = try await resolvePds(did: did) else { return nil }
        let response: BiblioReviewResponse
        do {
            response = try await get(
                BiblioReviewResponse.self,
                base: pds,
                path: "/xrpc/com.atproto.repo.getRecord",
                query: [
                    URLQueryItem(name: "repo", value: did),
                    URLQueryItem(name: "collection", value: Self.reviewCollection),
                    URLQueryItem(name: "rkey", value: rkey),
                ]
            )
        } catch {
            return nil
        }
        guard let record = response.value, !record.text.isEmpty else { return nil }
        return BiblioReview(
            uri: response.uri ?? "at://\(did)/\(Self.reviewCollection)/\(rkey)",
            did: did,
            record: record
        )
    }

    /// An actor's own reviews, newest first, read straight from their PDS.
    func listActorReviews(did: String, limit: Int = BiblioClient.profileReviewLimit) async throws -> [BiblioReview] {
        guard let pds = try await resolvePds(did: did) else { return [] }
        let listing: BiblioReviewListing
        do {
            listing = try await get(
                BiblioReviewListing.self,
                base: pds,
                path: "/xrpc/com.atproto.repo.listRecords",
                query: [
                    URLQueryItem(name: "repo", value: did),
                    URLQueryItem(name: "collection", value: Self.reviewCollection),
                    URLQueryItem(name: "limit", value: String(limit)),
                ]
            )
        } catch {
            return []
        }
        return listing.records.compactMap { envelope in
            guard !envelope.value.text.isEmpty else { return nil }
            return BiblioReview(uri: envelope.uri, did: did, record: envelope.value)
        }
    }

    /// Every `bookShelving` record the actor has, following cursors.
    func listActorShelving(did: String) async throws -> [BiblioShelving] {
        guard let pds = try await resolvePds(did: did) else { return [] }
        var out: [BiblioShelving] = []
        var cursor: String?
        var pages = 0
        while pages < Self.maxShelvingPages {
            var query = [
                URLQueryItem(name: "repo", value: did),
                URLQueryItem(name: "collection", value: Self.shelvingCollection),
                URLQueryItem(name: "limit", value: String(Self.listPageSize)),
            ]
            if let cursor { query.append(URLQueryItem(name: "cursor", value: cursor)) }
            guard let page = try? await get(
                BiblioShelvingListing.self,
                base: pds,
                path: "/xrpc/com.atproto.repo.listRecords",
                query: query
            ) else { break }
            out.append(contentsOf: page.records.map {
                BiblioShelving(uri: $0.uri, value: $0.value)
            })
            pages += 1
            guard let next = page.cursor, !next.isEmpty, !page.records.isEmpty else { break }
            cursor = next
        }
        return out
    }

    /// Shelf rkey → display name for an actor's shelves.
    func listActorShelves(did: String) async throws -> [String: String] {
        guard let pds = try await resolvePds(did: did) else { return [:] }
        guard let listing = try? await get(
            BiblioShelfListing.self,
            base: pds,
            path: "/xrpc/com.atproto.repo.listRecords",
            query: [
                URLQueryItem(name: "repo", value: did),
                URLQueryItem(name: "collection", value: Self.shelfCollection),
                URLQueryItem(name: "limit", value: String(Self.listPageSize)),
            ]
        ) else { return [:] }
        var out: [String: String] = [:]
        for envelope in listing.records {
            guard let name = envelope.value.name?.nilIfEmpty,
                  let rkey = AtUri(string: envelope.uri)?.rkey
            else { continue }
            out[rkey] = name
        }
        return out
    }

    /// Identity (handle, display name, avatar) for a DID or handle.
    func getActor(actor: String) async throws -> BiblioActor {
        try await get(
            BiblioActor.self,
            base: bskyBase,
            path: "/xrpc/app.bsky.actor.getProfile",
            query: [URLQueryItem(name: "actor", value: actor)]
        )
    }

    // MARK: - Identity

    /// The actor's PDS endpoint from their DID document.
    ///
    /// `did:plc` documents live on the PLC directory; `did:web` documents live
    /// on the DID's own host, at `/.well-known/did.json` unless the DID carries
    /// a path.
    func resolvePds(did: String) async throws -> URL? {
        if let cached = cachedPds(for: did) { return cached }
        guard let documentURL = Self.didDocumentURL(did: did) else { return nil }

        struct DidDocument: Decodable {
            struct Service: Decodable {
                let id: String?
                let type: String?
                let serviceEndpoint: String?
            }

            let service: [Service]?
        }

        guard let document = try? await get(DidDocument.self, url: documentURL) else { return nil }
        guard let endpoint = document.service?.first(where: {
            $0.type == "AtprotoPersonalDataServer" && ($0.id ?? "").hasSuffix("#atproto_pds")
        })?.serviceEndpoint, let url = Self.normalizePds(endpoint) else { return nil }
        storePds(url, for: did)
        return url
    }

    /// Location of a DID's document, or nil for methods this app cannot read.
    static func didDocumentURL(did: String) -> URL? {
        if did.hasPrefix("did:plc:") { return URL(string: "https://plc.directory/\(did)") }
        let webPrefix = "did:web:"
        guard did.hasPrefix(webPrefix) else { return nil }
        let decoded = String(did.dropFirst(webPrefix.count)).removingPercentEncoding ?? String(did.dropFirst(webPrefix.count))
        let parts = decoded.split(separator: ":", omittingEmptySubsequences: false).map(String.init)
        guard let host = parts.first, !host.isEmpty else { return nil }
        let hasPort = parts.count > 1 && parts[1].allSatisfy(\.isNumber) && !parts[1].isEmpty
        let authority = hasPort ? "\(host):\(parts[1])" : host
        let tail = parts.dropFirst(hasPort ? 2 : 1).filter { !$0.isEmpty }
        let path = tail.isEmpty ? "/.well-known/did.json" : "/\(tail.joined(separator: "/"))/did.json"
        return URL(string: "https://\(authority)\(path)")
    }

    static func normalizePds(_ endpoint: String) -> URL? {
        var trimmed = endpoint.trimmingCharacters(in: .whitespacesAndNewlines)
        while trimmed.hasSuffix("/") { trimmed.removeLast() }
        guard !trimmed.isEmpty else { return nil }
        if !trimmed.lowercased().hasPrefix("http://") && !trimmed.lowercased().hasPrefix("https://") {
            trimmed = "https://\(trimmed)"
        }
        return URL(string: trimmed)
    }

    private func cachedPds(for did: String) -> URL? {
        pdsLock.lock()
        defer { pdsLock.unlock() }
        return pdsByDid[did]
    }

    private func storePds(_ url: URL, for did: String) {
        pdsLock.lock()
        if pdsByDid.count < 256 { pdsByDid[did] = url }
        pdsLock.unlock()
    }

    // MARK: - Transport

    private func backlinks(_ subject: String, source: String) async -> [BiblioBacklink] {
        let response = try? await get(
            BiblioBacklinks.self,
            base: constellationBase,
            path: "/xrpc/blue.microcosm.links.getBacklinks",
            query: [
                URLQueryItem(name: "subject", value: subject),
                URLQueryItem(name: "source", value: source),
                URLQueryItem(name: "limit", value: String(Self.bookReviewLimit)),
            ]
        )
        return response?.records ?? []
    }

    private func get<T: Decodable>(
        _ type: T.Type,
        base: URL,
        path: String,
        query: [URLQueryItem]
    ) async throws -> T {
        guard var components = URLComponents(url: base.appendingPathComponent(path), resolvingAgainstBaseURL: false) else {
            throw BiblioClientError.invalidResponse
        }
        components.queryItems = query
        guard let url = components.url else { throw BiblioClientError.invalidResponse }
        return try await get(type, url: url)
    }

    private func get<T: Decodable>(_ type: T.Type, url: URL) async throws -> T {
        let data = try await data(from: url)
        do {
            return try decoder.decode(type, from: data)
        } catch {
            Self.logger.debug("decode failed for \(url.absoluteString, privacy: .public): \(error.localizedDescription, privacy: .public)")
            throw BiblioClientError.invalidResponse
        }
    }

    private func data(from url: URL) async throws -> Data {
        var request = URLRequest(url: url)
        request.timeoutInterval = Self.timeout
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw BiblioClientError.invalidResponse }
        if http.statusCode == 404 { throw BiblioClientError.notFound }
        guard (200 ..< 300).contains(http.statusCode) else { throw BiblioClientError.http(http.statusCode) }
        return data
    }
}
