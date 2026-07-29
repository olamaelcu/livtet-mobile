import Foundation
import LivtetKit
import LivtetKitFFI
import os.log

struct OpenLibraryClient {
    private static let logger = Logger(subsystem: "net.olamaelcu.livtet", category: "OpenLibraryClient")
    private static let endpoint = "https://openlibrary.org/search.json"
    private static let coverBase = "https://covers.openlibrary.org/b/id"

    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func search(query: String, limit: Int = 20) async throws -> [OpenLibraryDoc] {
        Self.logger.notice("search() called: query=\(query, privacy: .public), limit=\(limit, privacy: .public)")

        guard !query.isEmpty else {
            Self.logger.error("search() aborting: empty query; throwing badRequest")
            throw OpenLibraryError.badRequest
        }

        var components = URLComponents(string: Self.endpoint)
        components?.queryItems = [
            URLQueryItem(name: "q", value: query),
            URLQueryItem(name: "limit", value: String(limit)),
            URLQueryItem(name: "fields", value: "title,author_name,publisher,first_publish_year,isbn,cover_i,key"),
        ]
        guard let url = components?.url else {
            Self.logger.error("search() aborting: failed to build URL from components=\(String(describing: components), privacy: .public)")
            throw OpenLibraryError.providerDown
        }

        Self.logger.notice("Sending GET \(url.absoluteString, privacy: .public)")

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(from: url)
        } catch {
            Self.logger.error("Transport error during request: \(error.localizedDescription, privacy: .public); throwing providerDown")
            throw OpenLibraryError.providerDown
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            Self.logger.error("Response was not an HTTPURLResponse (got \(String(describing: type(of: response)), privacy: .public)); throwing providerDown")
            throw OpenLibraryError.providerDown
        }

        let statusCode = httpResponse.statusCode
        let contentType = httpResponse.value(forHTTPHeaderField: "Content-Type") ?? "<none>"
        let retryAfter = httpResponse.value(forHTTPHeaderField: "Retry-After")
        Self.logger.notice("Received response: status=\(statusCode, privacy: .public), contentType=\(contentType, privacy: .public), contentLength=\(data.count, privacy: .public), retryAfter=\(retryAfter ?? "<none>", privacy: .public)")

        switch statusCode {
        case 200:
            do {
                let decoded = try JSONDecoder().decode(OpenLibraryResponse.self, from: data)
                Self.logger.notice("Decoded OpenLibraryResponse: numFound=\(decoded.numFound, privacy: .public), docs.count=\(decoded.docs.count, privacy: .public)")
                return decoded.docs
            } catch {
                let preview = String(data: data.prefix(512), encoding: .utf8) ?? "<non-utf8>"
                Self.logger.error("Failed to decode OpenLibraryResponse: \(error.localizedDescription, privacy: .public); body preview=\(preview, privacy: .public); throwing providerDown")
                throw OpenLibraryError.providerDown
            }
        case 429:
            let seconds = retryAfter.flatMap(Int.init)
            Self.logger.notice("Rate limited: status=429, Retry-After=\(retryAfter ?? "<none>", privacy: .public)")
            throw OpenLibraryError.rateLimited(retryAfterSeconds: seconds)
        case 404:
            Self.logger.notice("Got 404 — treating as empty results")
            return []
        case 500...599:
            Self.logger.error("Server error: status=\(statusCode, privacy: .public)")
            throw OpenLibraryError.providerDown
        default:
            Self.logger.error("Unhandled status code: status=\(statusCode, privacy: .public); throwing providerDown")
            throw OpenLibraryError.providerDown
        }
    }

    static func coverURL(for coverID: Int, size: String = "M") -> URL? {
        URL(string: "\(coverBase)/\(coverID)-\(size).jpg")
    }
}

enum OpenLibraryError: Error, Equatable, LocalizedError {
    case badRequest
    case rateLimited(retryAfterSeconds: Int?)
    case providerDown

    var errorDescription: String? {
        switch self {
        case .badRequest:
            return "Invalid search query."
        case .rateLimited(let retryAfter):
            return retryAfter.map { "Rate limited — try again in \($0) seconds." }
                ?? "Rate limited — try again shortly."
        case .providerDown:
            return "OpenLibrary is having problems. Try again later."
        }
    }

    var category: ProviderErrorCategory {
        switch self {
        case .badRequest: return .notFound
        case .rateLimited: return .rateLimited
        case .providerDown: return .providerDown
        }
    }

    var retryAfterSeconds: Int? {
        if case .rateLimited(let seconds) = self { return seconds }
        return nil
    }
}
