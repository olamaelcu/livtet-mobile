import Combine
import Foundation
import LivtetKitFFI
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

final class SyncManager: ObservableObject {
    static let shared = SyncManager()

    @Published var state: SyncState = .disconnected

    private let pairingService: PairingServiceProtocol
    private let keychain: KeychainService
    private let deviceIdentity: DeviceIdentityService
    private var cancellables = Set<AnyCancellable>()

    private init(
        pairingService: PairingServiceProtocol = PairingService(),
        keychain: KeychainService = .shared,
        deviceIdentity: DeviceIdentityService = .shared
    ) {
        self.pairingService = pairingService
        self.keychain = keychain
        self.deviceIdentity = deviceIdentity
        restoreSession()
        observeDeepLinks()
    }

    func pairWithURL(_ url: URL) async {
        await MainActor.run { state = .pairing }
        do {
            let desktop = try await pairingService.pair(with: url)
            await MainActor.run { state = .paired(desktop) }
            // Auto-trigger sync so progress flows immediately after pairing.
            Task { await self.syncNow() }
        } catch {
            await MainActor.run { state = .error(error.localizedDescription) }
        }
    }

    func syncNow() async {
        guard case .paired(let desktop) = state else { return }

        await MainActor.run { state = .syncing(progress: 0) }

        let deviceName = await MainActor.run { UIDevice.current.name }
        let config = SyncConfig(
            host: desktop.host,
            port: desktop.port,
            deviceId: deviceIdentity.deviceId,
            deviceName: deviceName,
            sessionToken: desktop.sessionToken
        )

        let ffiResult = await Task.detached { () -> LivtetKitFFI.SyncState in
            syncOnce(config: config)
        }.value

        await MainActor.run {
            switch ffiResult {
            case .idle:
                state = .paired(desktop)
            case .syncing(let progress):
                state = .syncing(progress: progress)
            case .completed:
                var updated = desktop
                updated.lastSeenAt = Date()
                state = .paired(updated)
            case .failed(let error):
                state = .error(error)
            }
        }
    }

    func disconnect() {
        cancelSync(deviceId: deviceIdentity.deviceId)
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
