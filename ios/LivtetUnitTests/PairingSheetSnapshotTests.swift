@testable import Livtet
import XCTest

// MARK: - PairingSheetSnapshotTests
//
// Snapshot tests for the PairingSheet UI component. These tests
// capture rendered views and compare them against reference images
// to detect unintended visual changes.
//
// Prerequisites:
//   1. The PairingSheet view type must exist in the app target.
//   2. The pointfreeco/swift-snapshot-testing SPM package must be
//      configured and linked against LivtetUnitTests (it currently
//      is — see SnapshotTesting in project.yml).
//
// Reference images live in LivtetUnitTests/Snapshots/ and are
// tracked in git.  When a visual change is intentional, re-record
// snapshots by setting the recordMode flag or running tests with
// the RECORD_SNAPSHOTS environment variable.

final class PairingSheetSnapshotTests: XCTestCase {

    func testPairingSheetInitialLayout() throws {
        throw XCTSkip("""
            Snapshot tests require the PairingSheet view type. \
            Implement PairingSheet (expected in Wave 3), then \
            unskip and add: let vc = PairingSheet(...) \
            assertSnapshot(matching: vc, as: .image)
            """
        )
    }

    func testPairingSheetWithValidQRCode() throws {
        throw XCTSkip("Add after PairingSheet is implemented")
    }

    func testPairingSheetErrorState() throws {
        throw XCTSkip("Add after PairingSheet is implemented")
    }

    func testPairingSheetConnectedConfirmation() throws {
        throw XCTSkip("Add after PairingSheet is implemented")
    }
}
