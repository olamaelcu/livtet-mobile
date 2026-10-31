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

/// The storage `SyncManager` needs; `KeychainService` in the app, an
/// in-memory double in tests.
protocol SessionStore: AnyObject {
    func store(key: String, value: String) throws
    func retrieve(key: String) throws -> String
    func delete(key: String) throws
    func exists(key: String) -> Bool
}

extension KeychainService: SessionStore {
    func store(key: String, value: String) throws {
        try store(key: key, value: value, accessGroup: nil)
    }
}

/// Peer lookup the pairing flow needs; `DiscoveryService` in the app, a fake in
/// tests.
protocol PeerDiscovery: AnyObject {
    func desktop(forNodeId nodeId: String, timeout: TimeInterval) async -> Desktop?
}

extension DiscoveryService: PeerDiscovery {}

/// App-facing sync/pairing state machine.
///
/// Pairing is HTTP: the device files a claim (`POST /sync/pair`) carrying its
/// own node id, waits on the claim-gated status stream, and stores the session
/// token the desktop mints on approval. Data routes then present that token in
/// `x-livtet-session`.
@MainActor
final class SyncManager: ObservableObject {
    static let shared = SyncManager()

    @Published var state: SyncState = .disconnected

    private static let sessionKey = "session_token"
    private static let desktopKey = "paired_desktop"
    /// How long the desktop has to approve once a claim is filed.
    private static let approvalTimeout: TimeInterval = 180
    /// How long a LAN peer lookup may take before pairing gives up on it.
    private static let discoveryTimeout: TimeInterval = 5

    private let keychain: SessionStore
    private let network: SyncHTTPClient
    private let discovery: PeerDiscovery
    private let deviceId: String

    private var cancellables = Set<AnyCancellable>()
    /// The last target, so the pairing sheet can retry.
    private var lastTarget: LivtetSyncURL?

    init(
        keychain: SessionStore = KeychainService.shared,
        network: SyncHTTPClient = NetworkClient(),
        discovery: PeerDiscovery = DiscoveryService(),
        deviceId: String = DeviceIdentityService.shared.deviceId
    ) {
        self.keychain = keychain
        self.network = network
        self.discovery = discovery
        self.deviceId = deviceId
        restoreSession()
        observeDeepLinks()
    }

    /// The session token for data routes, when paired.
    var sessionToken: String? {
        if case let .paired(desktop) = state {
            return desktop.sessionToken
        }
        return try? keychain.retrieve(key: Self.sessionKey)
    }

    var hasSavedSession: Bool {
        keychain.exists(key: Self.sessionKey)
    }

    /// Entry point for `livtet://sync?v=2&…` deep links.
    func pairWithURL(_ url: URL) async {
        do {
            guard let target = try url.parseLivtetSyncURL() else {
                throw PairingError.invalidURL
            }
            await pair(with: target)
        } catch {
            state = .error(message(for: error))
        }
    }

    /// Entry point for text from a scanner or paste field: a v2 URL or a bare
    /// node id.
    func pair(withRawInput raw: String) async {
        guard let target = PairingInput.target(from: raw) else {
            state = .error(PairingError.invalidURL.localizedDescription)
            return
        }
        await pair(with: target)
    }

    /// Re-run the last pairing attempt.
    func retry() async {
        guard let target = lastTarget else { return }
        await pair(with: target)
    }

    func disconnect() {
        try? keychain.delete(key: Self.sessionKey)
        try? keychain.delete(key: Self.desktopKey)
        lastTarget = nil
        state = .disconnected
    }

    /// Pair with `target`, driving `state` through the handshake.
    func pair(with target: LivtetSyncURL) async {
        lastTarget = target
        state = .pairing

        do {
            let address = try await resolve(target)
            // Confirm the peer before claiming it: a stale QR pointing at a
            // re-provisioned daemon must not pair the wrong device.
            let identity = try await network.identity(host: address.host, port: address.port)
            guard identity.nodeId == target.nodeId else {
                throw PairingError.identityMismatch
            }

            let claim = try await network.pair(
                nodeId: target.nodeId,
                deviceId: deviceId,
                name: UIDevice.current.name,
                deviceType: "ios",
                host: address.host,
                port: address.port
            )
            guard let claimToken = claim.claim else {
                throw PairingError.noClaim
            }

            let decision = try await firstDecision(
                nodeId: target.nodeId,
                claim: claimToken,
                host: address.host,
                port: address.port
            )
            guard decision.event == "approved",
                  let sessionToken = decision.sessionToken,
                  !sessionToken.isEmpty
            else {
                throw PairingError.rejected
            }

            let desktop = PairedDesktop(
                id: target.nodeId,
                name: target.name ?? identity.deviceId,
                host: address.host,
                port: address.port,
                sessionToken: sessionToken,
                lastSeenAt: Date()
            )
            persist(desktop)
            state = .paired(desktop)
        } catch {
            state = .error(message(for: error))
        }
    }

    // MARK: - Private

    private func resolve(_ target: LivtetSyncURL) async throws -> (host: String, port: UInt16) {
        if let host = target.host {
            return (host, target.port ?? NetworkClient.defaultPort)
        }
        guard let peer = await discovery.desktop(
            forNodeId: target.nodeId,
            timeout: Self.discoveryTimeout
        ) else {
            throw PairingError.peerNotFound
        }
        return (peer.host, peer.port)
    }

    /// The first decision on the claim-gated stream, with an approval deadline.
    ///
    /// The timeout is a racing task rather than Combine's operator: cancelling
    /// the group tears down the stream, and no continuation is left to leak if
    /// the publisher completes first.
    private func firstDecision(
        nodeId: String,
        claim: String,
        host: String,
        port: UInt16
    ) async throws -> PairingDecision {
        let publisher = network.pairStatus(nodeId: nodeId, claim: claim, host: host, port: port)
        let deadline = Self.approvalTimeout

        return try await withThrowingTaskGroup(of: PairingDecision.self) { group in
            group.addTask {
                for try await decision in publisher.values {
                    if decision.event == "approved" || decision.event == "rejected" {
                        return decision
                    }
                }
                throw PairingError.noClaim
            }
            group.addTask {
                try await Task.sleep(nanoseconds: UInt64(deadline * 1_000_000_000))
                throw PairingError.timedOut
            }

            guard let first = try await group.next() else {
                throw PairingError.timedOut
            }
            group.cancelAll()
            return first
        }
    }

    private func persist(_ desktop: PairedDesktop) {
        if let data = try? JSONEncoder().encode(desktop),
           let json = String(data: data, encoding: .utf8) {
            try? keychain.store(key: Self.desktopKey, value: json)
        }
        try? keychain.store(key: Self.sessionKey, value: desktop.sessionToken)
    }

    private func restoreSession() {
        if let json = try? keychain.retrieve(key: Self.desktopKey),
           let data = json.data(using: .utf8),
           let desktop = try? JSONDecoder().decode(PairedDesktop.self, from: data) {
            state = .paired(desktop)
            return
        }
        // A session token written before the desktop record existed: keep it so
        // a paired install does not look disconnected after upgrading.
        if let token = try? keychain.retrieve(key: Self.sessionKey) {
            state = .paired(PairedDesktop(
                id: "",
                name: UIDevice.current.name,
                host: "",
                port: 0,
                sessionToken: token,
                lastSeenAt: Date()
            ))
        }
    }

    private func observeDeepLinks() {
        DeepLinkRouter.shared.$pendingRoute
            .compactMap { $0 }
            .sink { [weak self] route in
                guard case let .pair(nodeId, host, port, name) = route else { return }
                let target = LivtetSyncURL(
                    nodeId: nodeId,
                    host: host,
                    port: port,
                    name: name,
                    addresses: host.map { [$0] } ?? []
                )
                Task { [weak self] in
                    await self?.pair(with: target)
                }
            }
            .store(in: &cancellables)
    }

    private func message(for error: Error) -> String {
        if let pairing = error as? PairingError {
            return pairing.localizedDescription
        }
        return error.localizedDescription
    }
}
