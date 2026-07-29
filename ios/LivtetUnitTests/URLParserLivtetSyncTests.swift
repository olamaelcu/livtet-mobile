@testable import Livtet
import XCTest

// MARK: - URLParserLivtetSyncTests

final class URLParserLivtetSyncTests: XCTestCase {

    // MARK: Helpers

    private let desktopId = "01HXYZ123456789ABCDEFGHIJK"
    private let validToken = "01HABC9876543210ZYXWVUTSR"

    // MARK: Valid URLs — legacy single `ip` parameter

    func testParsesValidURL() throws {
        let url = URL(
            string: "livtet://sync?v=1&desktopId=\(desktopId)"
                + "&ip=192.168.1.100&port=12345&token=\(validToken)"
        )!
        let result = try XCTUnwrap(url.parseLivtetSyncURL())
        XCTAssertEqual(result.desktopId, desktopId)
        XCTAssertEqual(result.ip, "192.168.1.100")
        XCTAssertEqual(result.port, 12_345)
        XCTAssertEqual(result.token, validToken)
        XCTAssertEqual(result.addresses, ["192.168.1.100"])
    }

    func testParsesIPv6URL() throws {
        let url = URL(
            string: "livtet://sync?v=1&desktopId=\(desktopId)"
                + "&ip=%5Bfe80::1%5D&port=12345&token=\(validToken)"
        )!
        let result = try XCTUnwrap(url.parseLivtetSyncURL())
        XCTAssertEqual(result.ip, "fe80::1")
        XCTAssertEqual(result.addresses, ["fe80::1"])
    }

    // MARK: Valid URLs — new `ip[]` array format

    func testParsesURLWithMultipleIps() throws {
        let url = URL(
            string: "livtet://sync?v=1&desktopId=\(desktopId)"
                + "&port=12345&token=\(validToken)"
                + "&ip[]=192.168.1.100&ip[]=10.0.0.42"
        )!
        let result = try XCTUnwrap(url.parseLivtetSyncURL())
        XCTAssertEqual(result.ip, "192.168.1.100")
        XCTAssertEqual(result.port, 12_345)
        XCTAssertEqual(result.addresses, ["192.168.1.100", "10.0.0.42"])
    }

    func testParsesURLWithMultipleIpsIncludingIPv6() throws {
        let url = URL(
            string: "livtet://sync?v=1&desktopId=\(desktopId)"
                + "&port=12345&token=\(validToken)"
                + "&ip[]=192.168.1.100&ip[]=%5Bfe80::1%5D"
        )!
        let result = try XCTUnwrap(url.parseLivtetSyncURL())
        XCTAssertEqual(result.ip, "192.168.1.100")
        XCTAssertEqual(result.addresses, ["192.168.1.100", "fe80::1"])
    }

    func testParsesURLWithSingleIpArray() throws {
        let url = URL(
            string: "livtet://sync?v=1&desktopId=\(desktopId)"
                + "&port=12345&token=\(validToken)"
                + "&ip[]=172.16.0.1"
        )!
        let result = try XCTUnwrap(url.parseLivtetSyncURL())
        XCTAssertEqual(result.ip, "172.16.0.1")
        XCTAssertEqual(result.addresses, ["172.16.0.1"])
    }

    // MARK: Scheme and host rejection

    func testReturnsNilForNonLivtetScheme() {
        let url = URL(string: "https://example.com")!
        XCTAssertNil(try? url.parseLivtetSyncURL())
    }

    func testReturnsNilForWrongHost() {
        let url = URL(
            string: "livtet://other?v=1&desktopId=abc"
                + "&ip=1.2.3.4&port=8080&token=\(validToken)"
        )!
        XCTAssertNil(try? url.parseLivtetSyncURL())
    }

    // MARK: Missing or invalid parameters

    func testThrowsOnMissingVersion() {
        let url = URL(
            string: "livtet://sync?desktopId=\(desktopId)"
                + "&ip=1.2.3.4&port=8080&token=\(validToken)"
        )!
        XCTAssertThrowsError(try url.parseLivtetSyncURL())
    }

    func testThrowsOnWrongVersion() {
        let url = URL(
            string: "livtet://sync?v=2&desktopId=\(desktopId)"
                + "&ip=1.2.3.4&port=8080&token=\(validToken)"
        )!
        XCTAssertThrowsError(try url.parseLivtetSyncURL())
    }

    func testThrowsOnMissingDesktopId() {
        let url = URL(
            string: "livtet://sync?v=1&ip=1.2.3.4&port=8080&token=\(validToken)"
        )!
        XCTAssertThrowsError(try url.parseLivtetSyncURL())
    }

    func testThrowsOnMissingIPAndIpArray() {
        let url = URL(
            string: "livtet://sync?v=1&desktopId=\(desktopId)"
                + "&port=8080&token=\(validToken)"
        )!
        XCTAssertThrowsError(try url.parseLivtetSyncURL())
    }

    func testThrowsOnMissingToken() {
        let url = URL(
            string: "livtet://sync?v=1&desktopId=\(desktopId)"
                + "&ip=1.2.3.4&port=8080"
        )!
        XCTAssertThrowsError(try url.parseLivtetSyncURL())
    }

    func testThrowsOnInvalidPort() {
        let url = URL(
            string: "livtet://sync?v=1&desktopId=\(desktopId)"
                + "&ip=1.2.3.4&port=notaport&token=\(validToken)"
        )!
        XCTAssertThrowsError(try url.parseLivtetSyncURL())
    }

    func testThrowsOnInvalidTokenFormat() {
        // Token shorter than 26 characters should fail ULID validation
        let url = URL(
            string: "livtet://sync?v=1&desktopId=\(desktopId)"
                + "&ip=1.2.3.4&port=8080&token=tooshort"
        )!
        XCTAssertThrowsError(try url.parseLivtetSyncURL())
    }

    // MARK: Error type specificity

    func testThrownErrorIsSyncURLParseError() {
        let url = URL(
            string: "livtet://sync?v=2&desktopId=abc"
                + "&ip=1.2.3.4&port=8080&token=\(validToken)"
        )!
        XCTAssertThrowsError(try url.parseLivtetSyncURL()) { error in
            XCTAssertTrue(error is SyncURLParseError)
        }
    }
}
