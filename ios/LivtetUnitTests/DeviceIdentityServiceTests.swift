@testable import Livtet
import XCTest

// MARK: - DeviceIdentityServiceTests
//
// DeviceIdentityService generates a ULID on first access and persists
// it via KeychainService. These tests verify the ID is stable across
// calls and conforms to the Crockford base-32 ULID format.
//
// Because the singleton uses the shared KeychainService, running
// these tests on a simulator will persist a device ID. Run them in
// an isolated test host to avoid interfering with production data.

final class DeviceIdentityServiceTests: XCTestCase {

    private let sut = DeviceIdentityService.shared

    // MARK: Stability

    func testDeviceIdIsStableAcrossCalls() {
        let first = sut.deviceId
        let second = sut.deviceId
        XCTAssertEqual(first, second, "Device ID must be stable across calls")
    }

    func testDeviceIdIsStableAcrossMultipleReads() {
        let ids = (0..<10).map { _ in sut.deviceId }
        let unique = Set(ids)
        XCTAssertEqual(unique.count, 1, "Device ID must return the same value across 10 consecutive reads")
    }

    // MARK: ULID format

    func testDeviceIdIsULIDLength() {
        let id = sut.deviceId
        XCTAssertEqual(id.count, 26, "ULID must be exactly 26 characters")
    }

    func testDeviceIdUsesCrockfordBase32() {
        let id = sut.deviceId
        // Crockford base-32 alphabet (uppercase only — the generator
        // uses uppercase characters exclusively).
        let allowed = CharacterSet(charactersIn: "0123456789ABCDEFGHJKMNPQRSTVWXYZ")
        let idChars = CharacterSet(charactersIn: id)
        XCTAssertTrue(allowed.isSuperset(of: idChars),
                      "All ULID characters must be valid Crockford base-32: \(id)")
    }

    func testDeviceIdStartsWithTimestampComponent() {
        let id = sut.deviceId
        // The first 10 characters encode the Unix timestamp in
        // milliseconds (Crockford base-32). They should decode to a
        // reasonable recent date.
        let timestampPart = String(id.prefix(10))
        XCTAssertEqual(timestampPart.count, 10, "Timestamp component must be 10 characters")
        // The first character should be a digit for timestamps after
        // 2023 (roughly 0x17D or higher in base-32).
        let firstChar = timestampPart[timestampPart.startIndex]
        XCTAssertTrue(firstChar.isNumber || firstChar == "A" || firstChar == "B" || firstChar == "C",
                      "Expected timestamp prefix in [0-9A-C], got '\(firstChar)'")
    }

    // MARK: Uniqueness (random component)

    /// The random component (last 16 characters) ensures ULID
    /// uniqueness.  Even though DeviceIdentityService returns the
    /// same ID on repeated calls (it persists), we can verify the
    /// random portion looks like Crockford base-32.
    func testRandomComponentIsCrockfordBase32() {
        let id = sut.deviceId
        let randomPart = String(id.suffix(16))
        XCTAssertEqual(randomPart.count, 16, "Random component must be 16 characters")

        let allowed = CharacterSet(charactersIn: "0123456789ABCDEFGHJKMNPQRSTVWXYZ")
        let randomChars = CharacterSet(charactersIn: randomPart)
        XCTAssertTrue(allowed.isSuperset(of: randomChars),
                      "Random component must be valid Crockford base-32: \(randomPart)")
    }
}
