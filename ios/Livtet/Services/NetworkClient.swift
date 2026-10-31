import Combine
import Foundation

/// The sync HTTP calls the app makes during pairing.
///
/// A protocol so `SyncManager` can be driven by a fake in tests without a
/// socket.
protocol SyncHTTPClient {
    func pair(
        nodeId: String,
        deviceId: String,
        name: String,
        deviceType: String,
        host: String,
        port: UInt16
    ) async throws -> PairStatus

    func pairStatus(
        nodeId: String,
        claim: String,
        host: String,
        port: UInt16
    ) -> AnyPublisher<PairingDecision, Error>

    func identity(host: String, port: UInt16) async throws -> SyncIdentity
}

final class NetworkClient: SyncHTTPClient {
    static let defaultPort: UInt16 = 3120
    /// Header the sync server accepts for session-gated data routes.
    static let sessionHeader = "x-livtet-session"

    private let session: URLSession

    private let decoder: JSONDecoder = {
        // No `convertFromSnakeCase`: the wire models carry explicit
        // `CodingKeys`, and applying a strategy would rename the incoming keys
        // before they are matched.
        let decoder = JSONDecoder()
        return decoder
    }()

    private let encoder = JSONEncoder()

    init(session: URLSession = .shared) {
        self.session = session
    }

    /// File a claim. The desktop returns the secret the status stream needs.
    func pair(
        nodeId: String,
        deviceId: String,
        name: String,
        deviceType: String,
        host: String,
        port: UInt16 = defaultPort
    ) async throws -> PairStatus {
        let url = try baseURL(host: host, port: port, path: "/sync/pair")
        let body: [String: String] = [
            "device_id": deviceId,
            "name": name,
            "device_type": deviceType,
            "node_id": nodeId
        ]

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(body)

        let (data, response) = try await session.data(for: request)
        try Self.check(response)

        let status = try decoder.decode(PairStatus.self, from: data)
        guard status.status == "pending", let claim = status.claim, !claim.isEmpty else {
            throw AppError.network("The desktop did not return a pairing claim")
        }
        return status
    }

    /// Subscribe to the pairing decision stream.
    ///
    /// The claim is sent as a query parameter, not a header: the server opens
    /// the stream only for the device that filed the claim.
    func pairStatus(
        nodeId: String,
        claim: String,
        host: String,
        port: UInt16 = defaultPort
    ) -> AnyPublisher<PairingDecision, Error> {
        let subject = PassthroughSubject<PairingDecision, Error>()

        Task { [decoder, session] in
            do {
                guard let url = Self.pairStatusURL(
                    nodeId: nodeId,
                    claim: claim,
                    host: host,
                    port: port
                ) else {
                    throw AppError.network("Invalid pairing status URL")
                }
                var request = URLRequest(url: url)
                request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
                request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")

                let (bytes, response) = try await session.bytes(for: request)
                try Self.check(response)

                try await Self.readSSEStream(
                    bytes: bytes,
                    decoder: decoder,
                    subject: subject
                )
                subject.send(completion: .finished)
            } catch {
                subject.send(completion: .failure(error))
            }
        }

        return subject.eraseToAnyPublisher()
    }

    /// Confirm the peer's identity before claiming it.
    func identity(host: String, port: UInt16 = defaultPort) async throws -> SyncIdentity {
        let url = try baseURL(host: host, port: port, path: "/sync/identity")
        let (data, response) = try await session.data(from: url)
        try Self.check(response)
        return try decoder.decode(SyncIdentity.self, from: data)
    }

    // MARK: - Private

    /// URL of the claim-gated decision stream; internal so tests can assert the
    /// node id and claim land where the server expects them.
    static func pairStatusURL(nodeId: String, claim: String, host: String, port: UInt16) -> URL? {
        var components = URLComponents()
        components.scheme = "http"
        components.host = host
        components.port = Int(port)
        components.path = "/sync/pair/status/\(nodeId)"
        components.queryItems = [URLQueryItem(name: "claim", value: claim)]
        return components.url
    }

    private func baseURL(host: String, port: UInt16, path: String) throws -> URL {
        var components = URLComponents()
        components.scheme = "http"
        components.host = host
        components.port = Int(port)
        components.path = path
        guard let url = components.url else {
            throw AppError.network("Invalid sync URL for \(host):\(port)\(path)")
        }
        return url
    }

    private static func check(_ response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse else {
            throw AppError.network("Non-HTTP response")
        }
        guard (200...299).contains(http.statusCode) else {
            // 401 on a data route means the session token is missing or was
            // revoked; the caller has to pair again.
            throw AppError.network("HTTP \(http.statusCode)")
        }
    }

    private static func readSSEStream(
        bytes: URLSession.AsyncBytes,
        decoder: JSONDecoder,
        subject: PassthroughSubject<PairingDecision, Error>
    ) async throws {
        var buffer = ""
        for try await byte in bytes {
            guard let char = String(bytes: [byte], encoding: .utf8) else { continue }
            buffer.append(char)

            if buffer.hasSuffix("\n") {
                let line = buffer.trimmingCharacters(in: .newlines)
                buffer = ""

                if line.hasPrefix("data:") {
                    let jsonString = String(line.dropFirst(5))
                        .trimmingCharacters(in: .whitespaces)

                    guard !jsonString.isEmpty,
                          let jsonData = jsonString.data(using: .utf8) else {
                        continue
                    }

                    let decision = try decoder.decode(PairingDecision.self, from: jsonData)
                    subject.send(decision)

                    if decision.event == "approved" || decision.event == "rejected" {
                        return
                    }
                }
            }
        }
    }
}
