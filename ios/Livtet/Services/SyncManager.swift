import Combine
import Foundation
import UIKit

enum SyncState: Equatable {
    case disconnected
    case pairing
    case paired(PairedDesktop)
    case syncing(progress: Double)
    case error(String)

    var isPaired: Bool {
        if case .paired = self { return true }
        return false
    }
}

/// App-facing sync/pairing state machine.
///
/// The old UniFFI pairing/sync free functions were removed when the app moved
/// to the `LivtetStore` facade, which does not yet expose a pairing or sync
/// API. This type keeps the surface the app still uses — the state machine
/// consumed by ``SyncStatusView``/``PairingSheet``, the keychain-backed
/// session flag, and deep-link routing — while pairing requests surface an
/// explicit `.error` instead of pretending to succeed.
final class SyncManager: ObservableObject {
    static let shared = SyncManager()

    @Published var state: SyncState = .disconnected

    private let keychain: KeychainService
    private var cancellables = Set<AnyCancellable>()

    private init(keychain: KeychainService = .shared) {
        self.keychain = keychain
        restoreSession()
        observeDeepLinks()
    }

    /// Entry point for `livtet://sync?...` deep links.
    func pairWithURL(_ url: URL) async {
        do {
            guard try url.parseLivtetSyncURL() != nil else {
                state = .error(PairingError.invalidURL.localizedDescription)
                return
            }
        } catch {
            state = .error(PairingError.invalidURL.localizedDescription)
            return
        }
        state = .error(PairingError.syncUnavailable.localizedDescription)
    }

    func disconnect() {
        try? keychain.delete(key: "session_token")
        state = .disconnected
    }

    var hasSavedSession: Bool {
        keychain.exists(key: "session_token")
    }

    // MARK: - Private

    private func restoreSession() {
        let token: String
        do {
            token = try keychain.retrieve(key: "session_token")
        } catch {
            return
        }
        state = .paired(PairedDesktop(
            id: "",
            name: UIDevice.current.name,
            host: "",
            port: 0,
            sessionToken: token,
            lastSeenAt: Date()
        ))
    }

    private func observeDeepLinks() {
        DeepLinkRouter.shared.$pendingRoute
            .compactMap { $0 }
            .sink { [weak self] route in
                // swiftlint:disable:next identifier_name
                guard case let .pair(desktopId, ip, port, token) = route else { return }
                var components = URLComponents()
                components.scheme = "livtet"
                components.host = "sync"
                components.queryItems = [
                    URLQueryItem(name: "v", value: "1"),
                    URLQueryItem(name: "desktopId", value: desktopId),
                    URLQueryItem(name: "ip", value: ip),
                    URLQueryItem(name: "port", value: String(port)),
                    URLQueryItem(name: "token", value: token)
                ]
                guard let url = components.url else { return }
                Task { [weak self] in
                    await self?.pairWithURL(url)
                }
            }
            .store(in: &cancellables)
    }
}
