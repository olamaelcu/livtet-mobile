import Combine
import Foundation

final class NetworkClient {
    static let defaultPort: UInt16 = 3120

    private let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return decoder
    }()

    private let encoder = JSONEncoder()

    func pair(
        token: String,
        deviceId: String,
        name: String,
        deviceType: String,
        host: String,
        port: UInt16 = defaultPort
    ) async throws -> PairStatus {
        let urlString = "http://\(host):\(port)/sync/pair"
        guard let url = URL(string: urlString) else {
            throw AppError.network("Invalid pair URL: \(urlString)")
        }

        let body: [String: String] = [
            "device_id": deviceId,
            "name": name,
            "device_type": deviceType,
            "token": token
        ]

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(body)

        let (data, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw AppError.network("Non-HTTP response")
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            throw AppError.network("HTTP \(httpResponse.statusCode)")
        }

        return try decoder.decode(PairStatus.self, from: data)
    }

    func pairStatus(
        token: String,
        host: String,
        port: UInt16 = defaultPort
    ) -> AnyPublisher<PairingDecision, Error> {
        let subject = PassthroughSubject<PairingDecision, Error>()

        Task { [decoder] in
            do {
                let urlString = "http://\(host):\(port)/sync/pair/status/\(token)"
                guard let url = URL(string: urlString) else {
                    subject.send(completion: .failure(AppError.network("Invalid SSE URL: \(urlString)")))
                    return
                }

                var request = URLRequest(url: url)
                request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
                request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")

                let (bytes, response) = try await URLSession.shared.bytes(for: request)

                guard let httpResponse = response as? HTTPURLResponse,
                      (200...299).contains(httpResponse.statusCode) else {
                    subject.send(completion: .failure(AppError.network("SSE connection failed")))
                    return
                }

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
