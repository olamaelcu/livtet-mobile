import Combine
import Foundation
import Network

final class DiscoveryService {
    private let browser: NWBrowser
    private let subject = CurrentValueSubject<[Desktop], Never>([])

    var desktops: AnyPublisher<[Desktop], Never> {
        subject.eraseToAnyPublisher()
    }

    init() {
        let parameters = NWParameters()
        parameters.includePeerToPeer = true

        browser = NWBrowser(
            for: .bonjour(type: "_livtet-sync._tcp", domain: nil),
            using: parameters
        )

        browser.browseResultsChangedHandler = { [weak self] results, _ in
            self?.resolveResults(results)
        }

        browser.stateUpdateHandler = { [weak self] state in
            switch state {
            case .failed:
                self?.subject.send([])
            default:
                break
            }
        }

        browser.start(queue: .main)
    }

    func stop() {
        browser.cancel()
    }

    private func resolveResults(_ results: Set<NWBrowser.Result>) {
        var desktops: [Desktop] = []
        let group = DispatchGroup()
        let lock = NSLock()

        for result in results {
            group.enter()

            let connection = NWConnection(to: result.endpoint, using: .tcp)
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    if let endpoint = connection.currentPath?.remoteEndpoint {
                        let desktop = Self.desktop(from: endpoint, metadata: result.metadata)
                        lock.lock()
                        desktops.append(desktop)
                        lock.unlock()
                    }
                    connection.cancel()
                    group.leave()
                case .failed, .cancelled:
                    connection.cancel()
                    group.leave()
                default:
                    break
                }
            }
            connection.start(queue: .global())
        }

        group.notify(queue: .main) { [weak self] in
            self?.subject.send(desktops)
        }
    }

    private static func desktop(
        from endpoint: NWEndpoint,
        metadata: NWBrowser.Result.Metadata
    ) -> Desktop {
        var name = ""
        var host = ""
        var port: UInt16 = 3120
        var version: String?
        var deviceId: String?
        var deviceFlavor: String?
        var hostname: String?

        if case let .bonjour(txtRecord) = metadata {
            version = txtRecord.dictionary["version"]
            deviceId = txtRecord.dictionary["id"]
            deviceFlavor = txtRecord.dictionary["deviceFlavor"]
            hostname = txtRecord.dictionary["hostname"]
        }

        if case let .hostPort(endpointHost, endpointPort) = endpoint {
            host = sanitizeHost(endpointHost.debugDescription)
            port = endpointPort.rawValue
        }

        if name.isEmpty {
            name = host
        }

        return Desktop(
            id: deviceId ?? "",
            name: name,
            host: host,
            port: port,
            version: version,
            deviceFlavor: deviceFlavor,
            hostname: hostname
        )
    }

    private static func sanitizeHost(_ host: String) -> String {
        var cleaned = host
        if cleaned.hasPrefix("[") && cleaned.hasSuffix("]") {
            cleaned = String(cleaned.dropFirst().dropLast())
        }
        if let colon = cleaned.lastIndex(of: ":") {
            cleaned = String(cleaned[..<colon])
        }
        return cleaned
    }
}
