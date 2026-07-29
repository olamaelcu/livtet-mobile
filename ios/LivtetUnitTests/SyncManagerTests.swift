@testable import Livtet
import XCTest

// MARK: - SyncManagerTests
//
// Tests for the SyncManager state machine. SyncManager orchestrates
// the pairing lifecycle — connecting, disconnecting, error recovery,
// and session persistence.
//
// ⚠️  These tests require the SyncManager source type to exist.
//     If SyncManager has not been implemented yet, the compiler will
//     produce a "Cannot find 'SyncManager' in scope" error.  The
//     placeholder below documents the expected contract; uncomment
//     the test bodies once the type is available.
//
// Expected interface (not yet implemented):
//   - SyncManager.shared → singleton
//   - state: SyncState enum (.disconnected, .connecting, .connected, .error)
//   - hasSavedSession: Bool
//   - connect(url: LivtetSyncURL) async throws
//   - disconnect()
//   - reconnect() async throws

final class SyncManagerTests: XCTestCase {

    // MARK: Current state (placeholder)

    func testSyncManagerSourceNotYetAvailable() throws {
        throw XCTSkip("SyncManager has not been implemented yet — revisit after Wave 3")
    }

    // MARK: State machine transitions (documented, not yet runnable)

    /*
    var sut: SyncManager!
    var cancellables: Set<AnyCancellable>!

    override func setUp() {
        super.setUp()
        cancellables = []
        sut = SyncManager.shared
    }

    override func tearDown() {
        sut.disconnect()
        cancellables = nil
        super.tearDown()
    }

    func testInitialStateIsDisconnected() {
        switch sut.state {
        case .disconnected:
            break // expected
        default:
            XCTFail("Expected disconnected state, got \(sut.state)")
        }
    }

    func testHasNoSavedSessionInitially() {
        XCTAssertFalse(sut.hasSavedSession)
    }

    func testDisconnectReturnsToDisconnected() {
        sut.disconnect()
        switch sut.state {
        case .disconnected:
            break
        default:
            XCTFail("Expected disconnected after disconnect()")
        }
    }

    func testConnectTransitionsToConnecting() async throws {
        let url = LivtetSyncURL(
            desktopId: "01HXYZ123456789ABCDEFGHIJK",
            ip: "192.168.1.100",
            port: 12345,
            token: "01HABC9876543210ZYXWVUTSR",
            addresses: ["192.168.1.100"]
        )
        let task = Task { try await sut.connect(url: url) }
        // Give the state machine a moment to transition.
        try await Task.sleep(nanoseconds: 50_000_000)
        // Depending on implementation, the state might be .connecting
        // or already .connected — at minimum it must not be .disconnected.
        XCTAssertNotEqual(sut.state, .disconnected)
        _ = try await task.value
    }

    func testDoubleDisconnectIsIdempotent() {
        sut.disconnect()
        sut.disconnect()
        // No crash expected.
        switch sut.state {
        case .disconnected:
            break
        default:
            XCTFail("State should be disconnected after double disconnect")
        }
    }
    */
}
