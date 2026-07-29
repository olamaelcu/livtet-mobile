import Combine
import Foundation

enum DeepLinkRoute {
    // swiftlint:disable:next identifier_name
    case pair(desktopId: String, ip: String, port: UInt16, token: String)
}

final class DeepLinkRouter: ObservableObject {
    static let shared = DeepLinkRouter()

    @Published var pendingRoute: DeepLinkRoute?

    private init() {}

    @discardableResult
    func handleURL(_ url: URL) -> Bool {
        guard let route = try? url.parseLivtetSyncURL() else { return false }
        pendingRoute = .pair(desktopId: route.desktopId, ip: route.ip, port: route.port, token: route.token)

        return true
    }
}
