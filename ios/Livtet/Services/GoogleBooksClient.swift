import Foundation
import LivtetKit
import LivtetKitFFI
import os.log

enum GoogleBooksSearchOrder: String {
    case relevance
    case newest
}

struct GoogleBooksClient {
    private static let logger = Logger(subsystem: "net.olamaelcu.livtet", category: "GoogleBooksClient")
    private static let endpoint = "https://www.googleapis.com/books/v1/volumes"
    private static let userAgent = "https://livtet.olamaelcu.net/kb/user-agent#search"
    static let maxSearchLimit = 40

    private let apiKey: String
    private let session: URLSession

    init(apiKey: String = BuildConfig.googleAPIKey, session: URLSession = .shared) {
        self.apiKey = apiKey
        self.session = session
        Self.logger.debug("GoogleBooksClient init: apiKeyLength=\(apiKey.count, privacy: .public), session=\(String(describing: session), privacy: .public)")
        if apiKey.isEmpty {
            Self.logger.error("GoogleBooksClient init: API key is EMPTY — all calls will throw needsAuth")
        }
    }

    func search(
        query: String,
        maxResults: Int = 20,
        startIndex: Int = 0,
        orderBy: GoogleBooksSearchOrder = .relevance
    ) async throws -> [GoogleBooksItem] {
        Self.logger.notice("search() called: query=\(query, privacy: .public), maxResults=\(maxResults, privacy: .public), startIndex=\(startIndex, privacy: .public), orderBy=\(orderBy.rawValue, privacy: .public), apiKeyPresent=\(!apiKey.isEmpty, privacy: .public)")

        guard !apiKey.isEmpty else {
            Self.logger.error("search() aborting: API key missing; throwing needsAuth")
            throw GoogleBooksError.needsAuth
        }

        let clampedMaxResults = max(1, min(maxResults, Self.maxSearchLimit))
        let clampedStartIndex = max(0, startIndex)

        var components = URLComponents(string: Self.endpoint)
        components?.queryItems = [
            URLQueryItem(name: "q", value: query),
            URLQueryItem(name: "maxResults", value: String(clampedMaxResults)),
            URLQueryItem(name: "startIndex", value: String(clampedStartIndex)),
            URLQueryItem(name: "printType", value: "books"),
            URLQueryItem(name: "projection", value: "lite"),
            URLQueryItem(name: "orderBy", value: orderBy.rawValue),
            URLQueryItem(name: "key", value: apiKey),
        ]
        guard let url = components?.url else {
            Self.logger.error("search() aborting: failed to build URL from components=\(String(describing: components), privacy: .public)")
            throw GoogleBooksError.providerDown
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        Self.logger.notice("Sending GET \(url.absoluteString, privacy: .public)")

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            Self.logger.error("Transport error during request: \(error.localizedDescription, privacy: .public); throwing providerDown")
            throw GoogleBooksError.providerDown
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            Self.logger.error("Response was not an HTTPURLResponse (got \(String(describing: type(of: response)), privacy: .public)); throwing providerDown")
            throw GoogleBooksError.providerDown
        }

        let statusCode = httpResponse.statusCode
        let contentType = httpResponse.value(forHTTPHeaderField: "Content-Type") ?? "<none>"
        let retryAfter = httpResponse.value(forHTTPHeaderField: "Retry-After")
            ?? httpResponse.value(forHTTPHeaderField: "retry-after")
        Self.logger.notice("Received response: status=\(statusCode, privacy: .public), contentType=\(contentType, privacy: .public), contentLength=\(data.count, privacy: .public), retryAfter=\(retryAfter ?? "<none>", privacy: .public)")

        switch statusCode {
        case 200:
            do {
                let decoded = try JSONDecoder().decode(GoogleBooksResponse.self, from: data)
                let count = decoded.items?.count ?? 0
                Self.logger.notice("Decoded GoogleBooksResponse: totalItems=\(decoded.totalItems ?? 0, privacy: .public), items.count=\(count, privacy: .public)")
                return decoded.items ?? []
            } catch {
                let preview = String(data: data.prefix(512), encoding: .utf8) ?? "<non-utf8>"
                Self.logger.error("Failed to decode GoogleBooksResponse: \(error.localizedDescription, privacy: .public); body preview=\(preview, privacy: .public); throwing providerDown")
                throw GoogleBooksError.providerDown
            }
        case 401, 403:
            Self.logger.error("Authentication error: status=\(statusCode, privacy: .public); throwing needsAuth")
            throw GoogleBooksError.needsAuth
        case 429:
            let retryAfterSeconds = retryAfter.flatMap(Int.init)
            Self.logger.notice("Rate limited: status=429, Retry-After=\(retryAfter ?? "<none>", privacy: .public); throwing rateLimited(retryAfterSeconds: \(retryAfterSeconds.map(String.init) ?? "nil", privacy: .public))")
            throw GoogleBooksError.rateLimited(retryAfterSeconds: retryAfterSeconds)
        case 408:
            Self.logger.error("Request timed out: status=408; throwing timeout")
            throw GoogleBooksError.timeout
        case 404:
            Self.logger.notice("Not found: status=404; throwing notFound")
            throw GoogleBooksError.notFound
        default:
            let bodyPreview = String(data: data.prefix(512), encoding: .utf8) ?? "<non-utf8>"
            Self.logger.error("Unhandled status code: status=\(statusCode, privacy: .public), body preview=\(bodyPreview, privacy: .public); throwing providerDown")
            throw GoogleBooksError.providerDown
        }
    }

    /// Normalize a cover URL returned by Google Books: rewrite `http://` to
    /// `https://` (their CDN supports TLS even though records occasionally
    /// serve plaintext URLs), and strip `edge=curl` + `source=gbs_api`
    /// query parameters that would churn caches and bake in a curl effect.
    static func cleanCoverURL(_ urlString: String?) -> String? {
        guard var s = urlString, !s.isEmpty else { return urlString }
        if s.hasPrefix("http://") {
            s = "https://" + String(s.dropFirst("http://".count))
        }
        guard let questionIndex = s.firstIndex(of: "?") else { return s }
        let path = String(s[..<questionIndex])
        let query = String(s[s.index(after: questionIndex)...])
        let kept = query.split(separator: "&").filter { param -> Bool in
            let key = param.split(separator: "=", maxSplits: 1).first.map(String.init) ?? ""
            return key != "edge" && key != "source"
        }
        if kept.isEmpty { return path }
        return path + "?" + kept.joined(separator: "&")
    }
}

enum GoogleBooksError: Error, Equatable, LocalizedError {
    case needsAuth
    case rateLimited(retryAfterSeconds: Int?)
    case timeout
    case notFound
    case providerDown

    var errorDescription: String? {
        switch self {
        case .needsAuth:
            return "Search needs authentication. Add an API key in Settings to use Google Books."
        case .rateLimited(let retryAfter):
            return retryAfter.map { "Rate limited — try again in \($0) seconds." }
                ?? "Rate limited — try again shortly."
        case .timeout: return "Search timed out. Try again."
        case .notFound: return "No results found."
        case .providerDown: return "Search is having problems. Try again later."
        }
    }

    var category: ProviderErrorCategory {
        switch self {
        case .needsAuth: return .needsAuth
        case .rateLimited: return .rateLimited
        case .timeout: return .timeout
        case .notFound: return .notFound
        case .providerDown: return .providerDown
        }
    }

    var retryAfterSeconds: Int? {
        if case .rateLimited(let seconds) = self { return seconds }
        return nil
    }
}
