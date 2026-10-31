@testable import Livtet
import Combine
import XCTest

final class FakeSyncHTTPClient: SyncHTTPClient {
    var identityResult: Result<SyncIdentity, Error>
    var pairResult: Result<PairStatus, Error>
    var decision: PairingDecision

    var pairedNodeId: String?
    var pairedBody: (nodeId: String, deviceId: String, deviceType: String)?
    var statusRequest: (nodeId: String, claim: String)?

    init(nodeId: String) {
        identityResult = .success(SyncIdentity(nodeId: nodeId, deviceId: "desktop-device"))
        pairResult = .success(PairStatus(status: "pending", claim: "claim-1"))
        decision = PairingDecision(event: "approved", deviceId: "desktop-device", sessionToken: "session-1")
    }

    func pair(
        nodeId: String,
        deviceId: String,
        name: String,
        deviceType: String,
        host: String,
        port: UInt16
    ) async throws -> PairStatus {
        pairedNodeId = nodeId
        pairedBody = (nodeId, deviceId, deviceType)
        return try pairResult.get()
    }

    func pairStatus(
        nodeId: String,
        claim: String,
        host: String,
        port: UInt16
    ) -> AnyPublisher<PairingDecision, Error> {
        statusRequest = (nodeId, claim)
        return Just(decision)
            .setFailureType(to: Error.self)
            .eraseToAnyPublisher()
    }

    func identity(host: String, port: UInt16) async throws -> SyncIdentity {
        try identityResult.get()
    }
}

final class FakeDiscovery: PeerDiscovery {
    var peer: Desktop?

    func desktop(forNodeId nodeId: String, timeout: TimeInterval) async -> Desktop? {
        peer
    }
}

final class MemorySessionStore: SessionStore {
    var values: [String: String] = [:]

    func store(key: String, value: String) throws {
        values[key] = value
    }

    func retrieve(key: String) throws -> String {
        guard let value = values[key] else { throw KeychainServiceError.itemNotFound }
        return value
    }

    func delete(key: String) throws {
        values[key] = nil
    }

    func exists(key: String) -> Bool {
        values[key] != nil
    }
}

@MainActor
final class SyncManagerTests: XCTestCase {
    private let nodeId = String(repeating: "ab", count: 32)

    private func makeManager(
        host: String? = "10.0.0.5"
    ) -> (SyncManager, FakeSyncHTTPClient, FakeDiscovery, MemorySessionStore) {
        let client = FakeSyncHTTPClient(nodeId: nodeId)
        let discovery = FakeDiscovery()
        let store = MemorySessionStore()
        let manager = SyncManager(
            keychain: store,
            network: client,
            discovery: discovery,
            deviceId: "phone-device"
        )
        return (manager, client, discovery, store)
    }

    private func target(host: String? = "10.0.0.5") -> LivtetSyncURL {
        LivtetSyncURL(
            nodeId: nodeId,
            host: host,
            port: host == nil ? nil : 3120,
            name: "Nimble Quail",
            addresses: host.map { [$0] } ?? []
        )
    }

    func testAnApprovedClaimPairsAndPersistsTheSession() async throws {
        let (manager, client, _, store) = makeManager()

        await manager.pair(with: target())

        guard case let .paired(desktop) = manager.state else {
            return XCTFail("expected a paired state, got \(manager.state)")
        }
        XCTAssertEqual(desktop.id, nodeId)
        XCTAssertEqual(desktop.sessionToken, "session-1")
        XCTAssertEqual(desktop.name, "Nimble Quail")
        XCTAssertEqual(store.values["session_token"], "session-1")
        XCTAssertNotNil(store.values["paired_desktop"])

        XCTAssertEqual(client.pairedBody?.nodeId, nodeId)
        XCTAssertEqual(client.pairedBody?.deviceId, "phone-device")
        XCTAssertEqual(client.pairedBody?.deviceType, "ios")
        XCTAssertEqual(client.statusRequest?.claim, "claim-1")
        XCTAssertEqual(client.statusRequest?.nodeId, nodeId)
    }

    func testARejectedClaimDoesNotPair() async {
        let (manager, client, _, store) = makeManager()
        client.decision = PairingDecision(
            event: "rejected",
            deviceId: nil,
            sessionToken: nil
        )

        await manager.pair(with: target())

        guard case .error(let message) = manager.state else {
            return XCTFail("expected an error state, got \(manager.state)")
        }
        XCTAssertTrue(message.lowercased().contains("rejected"), message)
        XCTAssertNil(store.values["session_token"])
    }

    func testAnIdentityMismatchStopsBeforeClaiming() async {
        let (manager, client, _, _) = makeManager()
        client.identityResult = .success(
            SyncIdentity(nodeId: String(repeating: "cd", count: 32), deviceId: "other")
        )

        await manager.pair(with: target())

        guard case .error(let message) = manager.state else {
            return XCTFail("expected an error state, got \(manager.state)")
        }
        XCTAssertTrue(message.contains("did not match"), message)
        XCTAssertNil(client.pairedNodeId, "a mismatched peer must not be claimed")
    }

    func testAMissingAddressIsResolvedOverDiscovery() async {
        let (manager, client, discovery, _) = makeManager(host: nil)
        discovery.peer = Desktop(
            id: nodeId,
            name: "Nimble Quail",
            host: "192.168.1.20",
            port: 4123,
            version: "0.1.0",
            deviceFlavor: "desktop",
            hostname: "livtet-ab"
        )

        await manager.pair(with: target(host: nil))

        guard case .paired = manager.state else {
            return XCTFail("expected a paired state, got \(manager.state)")
        }
        XCTAssertEqual(client.pairedNodeId, nodeId)
    }

    func testAnAddresslessTargetWithNoPeerIsAnError() async {
        let (manager, _, _, _) = makeManager(host: nil)

        await manager.pair(with: target(host: nil))

        guard case .error(let message) = manager.state else {
            return XCTFail("expected an error state, got \(manager.state)")
        }
        XCTAssertTrue(message.contains("Could not find"), message)
    }

    func testScannedNodeIdIsAPairingTarget() async {
        let (manager, client, _, _) = makeManager()

        await manager.pair(withRawInput: "  \(nodeId)\n")

        XCTAssertEqual(client.pairedNodeId, nodeId)
    }

    func testDisconnectClearsTheSession() async {
        let (manager, _, _, store) = makeManager()
        await manager.pair(with: target())

        manager.disconnect()

        XCTAssertEqual(manager.state, .disconnected)
        XCTAssertTrue(store.values.isEmpty)
    }
}
