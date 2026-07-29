@testable import Livtet
import XCTest

final class KeychainServiceTests: XCTestCase {
    func testRoundTrip() throws {
        let service = KeychainService(service: "net.olamaelcu.livtet.tests.\(UUID().uuidString)")
        let key = "test_key"
        let value = "hello-\(UUID().uuidString)"

        XCTAssertThrowsError(try service.retrieve(key: key))

        try service.store(key: key, value: value)
        let read = try service.retrieve(key: key)
        XCTAssertEqual(read, value)

        try service.delete(key: key)
        XCTAssertThrowsError(try service.retrieve(key: key))
    }

    func testMissingKeyThrows() throws {
        let service = KeychainService(service: "net.olamaelcu.livtet.tests.\(UUID().uuidString)")
        XCTAssertThrowsError(try service.retrieve(key: "never-set"))
    }

    func testDeleteIsIdempotent() throws {
        let service = KeychainService(service: "net.olamaelcu.livtet.tests.\(UUID().uuidString)")
        try service.delete(key: "never-existed")
        try service.delete(key: "never-existed")
    }
}
