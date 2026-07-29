@testable import Livtet
import XCTest

// MARK: - KeychainServiceIntegrationTests
//
// These tests interact with the system keychain and must run on a
// simulator or physical device. They are skipped automatically when
// the host environment does not have a usable keychain (CI, macOS).
// Each test uses an isolated service instance scoped to the test
// method to avoid polluting the shared keychain.

final class KeychainServiceIntegrationTests: XCTestCase {

    /// Returns a KeychainService instance scoped to the calling test
    /// method. The service name includes a UUID so concurrent test
    /// runs do not collide.
    private func makeIsolatedService(file: StaticString = #filePath, line: UInt = #line) -> KeychainService {
        let testName = self.name.replacingOccurrences(of: " ", with: "_")
        let serviceName = "net.olamaelcu.livtet.tests.\(testName).\(UUID().uuidString)"
        return KeychainService(service: serviceName)
    }

    // MARK: Store and retrieve

    func testStoreAndRetrieve() throws {
        try XCTSkipIf(!isKeychainAvailable(), "Keychain not available in this environment")
        let sut = makeIsolatedService()
        let key = "test_store_retrieve"

        try sut.store(key: key, value: "hello-world")
        let retrieved = try sut.retrieve(key: key)
        XCTAssertEqual(retrieved, "hello-world")

        // Cleanup
        try sut.delete(key: key)
    }

    func testStoreAndRetrieveEmptyString() throws {
        try XCTSkipIf(!isKeychainAvailable(), "Keychain not available in this environment")
        let sut = makeIsolatedService()
        let key = "test_empty"

        try sut.store(key: key, value: "")
        let retrieved = try sut.retrieve(key: key)
        XCTAssertEqual(retrieved, "")

        try sut.delete(key: key)
    }

    // MARK: Delete

    func testDeleteRemovesValue() throws {
        try XCTSkipIf(!isKeychainAvailable(), "Keychain not available in this environment")
        let sut = makeIsolatedService()
        let key = "test_delete"

        try sut.store(key: key, value: "to-be-deleted")
        XCTAssertTrue(sut.exists(key: key))

        try sut.delete(key: key)
        XCTAssertFalse(sut.exists(key: key))
    }

    func testDeleteIsIdempotent() throws {
        try XCTSkipIf(!isKeychainAvailable(), "Keychain not available in this environment")
        let sut = makeIsolatedService()

        // Deleting a key that was never stored should not throw.
        try sut.delete(key: "never-existed")
        try sut.delete(key: "never-existed")
    }

    // MARK: Exists

    func testExistsReturnsFalseForMissingKey() throws {
        try XCTSkipIf(!isKeychainAvailable(), "Keychain not available in this environment")
        let sut = makeIsolatedService()
        XCTAssertFalse(sut.exists(key: "com.livtet.test.nonexistent"))
    }

    func testExistsReturnsTrueAfterStore() throws {
        try XCTSkipIf(!isKeychainAvailable(), "Keychain not available in this environment")
        let sut = makeIsolatedService()
        let key = "test_exists"

        try sut.store(key: key, value: "exists-check")
        XCTAssertTrue(sut.exists(key: key))

        try sut.delete(key: key)
    }

    // MARK: Retrieve errors

    func testRetrieveNonExistentThrows() throws {
        try XCTSkipIf(!isKeychainAvailable(), "Keychain not available in this environment")
        let sut = makeIsolatedService()

        XCTAssertThrowsError(try sut.retrieve(key: "com.livtet.test.nonexistent")) { error in
            XCTAssertTrue(error is KeychainServiceError)
        }
    }

    // MARK: Overwrite

    func testOverwriteExistingKey() throws {
        try XCTSkipIf(!isKeychainAvailable(), "Keychain not available in this environment")
        let sut = makeIsolatedService()
        let key = "test_overwrite"

        try sut.store(key: key, value: "first-value")
        XCTAssertEqual(try sut.retrieve(key: key), "first-value")

        try sut.store(key: key, value: "second-value")
        XCTAssertEqual(try sut.retrieve(key: key), "second-value")

        try sut.delete(key: key)
    }

    // MARK: Helpers

    /// Returns true when the test host has a usable keychain.
    /// On CI or macOS without a simulator this will be false.
    private func isKeychainAvailable() -> Bool {
        // A lightweight probe: try to store and immediately delete a
        // known key. If the keychain is functional the operations
        // succeed; on CI or headless environments they may fail with
        // errSecNotAvailable or similar.
        let probe = KeychainService(service: "net.olamaelcu.livtet.probe")
        let probeKey = "probe_\(UUID().uuidString)"
        do {
            try probe.store(key: probeKey, value: "probe")
            try probe.delete(key: probeKey)
            return true
        } catch {
            return false
        }
    }
}
