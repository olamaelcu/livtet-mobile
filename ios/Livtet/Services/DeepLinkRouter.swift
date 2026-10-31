import Combine
import Foundation

enum DeepLinkRoute {
    /// A pairing target: the peer's node id plus the address the link carried,
    /// when it carried one.
    case pair(nodeId: String, host: String?, port: UInt16?, name: String?)
}

final class DeepLinkRouter: ObservableObject {
    static let shared = DeepLinkRouter()

    @Published var pendingRoute: DeepLinkRoute?

    /// A platform permalink (book, review, or profile) waiting to be presented.
    /// The root view renders it as a sheet; nothing about it changes the tab
    /// selection or the library state.
    @Published var presentedPublicRoute: PublicRoute?

    private init() {}

    @discardableResult
    func handleURL(_ url: URL) -> Bool {
        // Public permalinks first: they arrive as both the `livtet://` scheme
        // and Universal Links, and share the `livtet` scheme with pairing.
        if let publicRoute = url.parseLivtetPublicURL() {
            presentedPublicRoute = publicRoute
            return true
        }

        guard let route = try? url.parseLivtetSyncURL() else { return false }
        pendingRoute = .pair(
            nodeId: route.nodeId,
            host: route.host,
            port: route.port,
            name: route.name
        )

        return true
    }
}
