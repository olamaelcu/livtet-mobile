@testable import Livtet
import XCTest

final class URLParserLivtetSyncTests: XCTestCase {

    // MARK: - Fixtures

    /// iroh's endpoint id: 64 lowercase hex characters.
    private let nodeId = String(repeating: "a1", count: 32)

    /// The Crockford-base32 ULID older peers report as their node id.
    private let ulid = "01HXYZ123456789ABCDEFGHIJK"

    // MARK: - Helpers

    private func parse(_ string: String) throws -> LivtetSyncURL? {
        try XCTUnwrap(URL(string: string), "not a URL: \(string)").parseLivtetSyncURL()
    }

    /// The shape a share sheet or QR code actually carries: `ip[]` travels
    /// percent-encoded as `ip%5B%5D`, so no literal brackets reach the query.
    private func encodedLink(_ items: [(String, String)]) -> String {
        var components = URLComponents()
        components.scheme = "livtet"
        components.host = "sync"
        components.queryItems = items.map { URLQueryItem(name: $0.0, value: $0.1) }
        guard let string = components.string else {
            XCTFail("could not encode query items \(items)")
            return "livtet://sync"
        }
        return string
    }

    private func expectMissingParameter(
        _ name: String,
        in url: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        XCTAssertThrowsError(try parse(url), file: file, line: line) { error in
            guard case let SyncURLParseError.missingRequiredParameter(actual) = error else {
                return XCTFail(
                    "expected a missing-parameter error, got \(error)",
                    file: file,
                    line: line
                )
            }
            XCTAssertEqual(actual, name, file: file, line: line)
        }
    }

    private func expectInvalidFormat(
        in url: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        XCTAssertThrowsError(try parse(url), file: file, line: line) { error in
            guard case SyncURLParseError.invalidFormat = error else {
                return XCTFail(
                    "expected an invalid-format error, got \(error)",
                    file: file,
                    line: line
                )
            }
        }
    }

    // MARK: - Happy path

    func testParsesNodeIdOnlyLink() throws {
        let parsed = try parse("livtet://sync?v=2&nodeId=\(nodeId)")

        XCTAssertEqual(parsed?.nodeId, nodeId)
        XCTAssertNil(parsed?.host, "a link without an address must resolve over Bonjour")
        XCTAssertNil(parsed?.port)
        XCTAssertNil(parsed?.name)
        XCTAssertEqual(parsed?.addresses, [])
    }

    func testParsesEveryField() throws {
        let link = encodedLink([
            ("v", "2"),
            ("nodeId", nodeId),
            ("host", "192.168.1.20"),
            ("port", "41234"),
            ("name", "Nimble Quail"),
        ])

        let parsed = try XCTUnwrap(try parse(link))

        XCTAssertEqual(parsed.nodeId, nodeId)
        XCTAssertEqual(parsed.host, "192.168.1.20")
        XCTAssertEqual(parsed.port, 41_234)
        XCTAssertEqual(parsed.name, "Nimble Quail")
        XCTAssertEqual(parsed.addresses, ["192.168.1.20"], "the host seeds the address list")
    }

    func testHostWithoutPortLeavesTheDefaultPortToTheCaller() throws {
        let parsed = try parse("livtet://sync?v=2&nodeId=\(nodeId)&host=10.0.0.9")

        XCTAssertEqual(parsed?.host, "10.0.0.9")
        XCTAssertNil(parsed?.port, "SyncManager substitutes NetworkClient.defaultPort")
        XCTAssertEqual(parsed?.addresses, ["10.0.0.9"])
    }

    func testPercentEncodedNameIsDecoded() throws {
        let parsed = try parse("livtet://sync?v=2&nodeId=\(nodeId)&name=Nimble%20Quail")

        XCTAssertEqual(parsed?.name, "Nimble Quail")
    }

    func testEmptyNameIsTreatedAsAbsent() throws {
        let parsed = try parse("livtet://sync?v=2&nodeId=\(nodeId)&name=")

        XCTAssertNil(parsed?.name)
    }

    func testAcceptsTheUlidFormOlderPeersReport() throws {
        let parsed = try parse("livtet://sync?v=2&nodeId=\(ulid)")

        XCTAssertEqual(parsed?.nodeId, ulid)
        XCTAssertEqual(parsed?.addresses, [])
    }

    func testAcceptsSnakeCaseNodeIdAlias() throws {
        let parsed = try parse("livtet://sync?v=2&node_id=\(nodeId)&port=3120")

        XCTAssertEqual(parsed?.nodeId, nodeId)
        XCTAssertEqual(parsed?.port, 3_120)
    }

    // MARK: - Addresses: `ip[]` array

    func testParsesIpArrayInOrderAndUsesItsFirstAddress() throws {
        let parsed = try parse(
            "livtet://sync?v=2&nodeId=\(nodeId)&ip[]=192.168.1.100&ip[]=10.0.0.42&port=3120"
        )

        XCTAssertEqual(parsed?.addresses, ["192.168.1.100", "10.0.0.42"])
        XCTAssertEqual(parsed?.host, "192.168.1.100")
        XCTAssertEqual(parsed?.port, 3_120)
    }

    func testEncodedIpArrayWithBracketedIPv6RoundTrips() throws {
        let link = encodedLink([
            ("v", "2"),
            ("nodeId", nodeId),
            ("ip[]", "192.168.1.100"),
            ("ip[]", "[fe80::1]"),
            ("port", "3120"),
            ("name", "Nimble Quail"),
        ])
        XCTAssertTrue(link.contains("ip%5B%5D="), "encoder check: \(link)")

        let parsed = try XCTUnwrap(try parse(link))

        XCTAssertEqual(parsed.addresses, ["192.168.1.100", "fe80::1"])
        XCTAssertEqual(parsed.host, "192.168.1.100")
        XCTAssertEqual(parsed.port, 3_120)
        XCTAssertEqual(parsed.name, "Nimble Quail")
    }

    /// Regression: a link written with a literal `ip[]` key makes Foundation
    /// double-escape every value in the query, so parsing exposes `%5Bfe80::1%5D`.
    /// The parser decodes `ip[]` a second time so bracketed IPv6 survives.
    func testLiteralIpArrayKeyWithBracketedIPv6RoundTrips() throws {
        let parsed = try parse(
            "livtet://sync?v=2&nodeId=\(nodeId)"
                + "&ip[]=192.168.1.100&ip[]=%5Bfe80::1%5D&port=3120"
        )

        XCTAssertEqual(parsed?.addresses, ["192.168.1.100", "fe80::1"])
        XCTAssertEqual(parsed?.host, "192.168.1.100")
        XCTAssertEqual(parsed?.port, 3_120)
    }

    func testSingleBracketedIpArrayValueIsUnbracketed() throws {
        let parsed = try parse("livtet://sync?v=2&nodeId=\(nodeId)&ip[]=%5Bfe80::1%5D")

        XCTAssertEqual(parsed?.addresses, ["fe80::1"])
        XCTAssertEqual(parsed?.host, "fe80::1")
    }

    /// Regression: a `Dictionary(uniqueKeysWithValues:)` build trapped on the
    /// repeated `ip[]` key.
    func testDuplicateIpArrayKeysArePreservedWithoutTrapping() throws {
        let parsed = try parse(
            "livtet://sync?v=2&nodeId=\(nodeId)&ip[]=10.0.0.5&ip[]=10.0.0.6&port=3120"
        )

        XCTAssertEqual(parsed?.addresses, ["10.0.0.5", "10.0.0.6"])
        XCTAssertEqual(parsed?.host, "10.0.0.5")
    }

    func testDuplicateScalarKeysKeepTheFirstValue() throws {
        let parsed = try parse(
            "livtet://sync?v=2&nodeId=\(nodeId)&nodeId=\(ulid)&host=a&host=b"
        )

        XCTAssertEqual(parsed?.nodeId, nodeId, "first nodeId wins, as it did before v2")
        XCTAssertEqual(parsed?.host, "a")
        XCTAssertEqual(parsed?.addresses, ["a"])
    }

    // MARK: - Addresses: legacy fallbacks

    func testParsesLegacySingleIpParameter() throws {
        let parsed = try parse("livtet://sync?v=2&nodeId=\(nodeId)&ip=192.168.1.100&port=3120")

        XCTAssertEqual(parsed?.addresses, ["192.168.1.100"])
        XCTAssertEqual(parsed?.host, "192.168.1.100")
        XCTAssertEqual(parsed?.port, 3_120)
    }

    func testParsesLegacySingleIpWithBracketedIPv6() throws {
        let parsed = try parse("livtet://sync?v=2&nodeId=\(nodeId)&ip=[fe80::1]")

        XCTAssertEqual(parsed?.host, "fe80::1")
        XCTAssertEqual(parsed?.addresses, ["fe80::1"])
    }

    func testUnbracketsAHostIPv6Literal() throws {
        let parsed = try parse("livtet://sync?v=2&nodeId=\(nodeId)&host=%5Bfe80::1%5D")

        XCTAssertEqual(parsed?.host, "fe80::1")
        XCTAssertEqual(parsed?.addresses, ["fe80::1"])
    }

    func testIpArrayWinsOverSingleIpAndHost() throws {
        let parsed = try parse(
            "livtet://sync?v=2&nodeId=\(nodeId)&ip[]=10.0.0.5&ip=192.168.1.100&host=172.16.0.1"
        )

        XCTAssertEqual(parsed?.addresses, ["10.0.0.5"])
        XCTAssertEqual(parsed?.host, "10.0.0.5")
    }

    func testSingleIpWinsOverHost() throws {
        let parsed = try parse(
            "livtet://sync?v=2&nodeId=\(nodeId)&ip=192.168.1.100&host=172.16.0.1"
        )

        XCTAssertEqual(parsed?.addresses, ["192.168.1.100"])
        XCTAssertEqual(parsed?.host, "192.168.1.100")
    }

    func testEmptySingleIpFallsBackToHost() throws {
        let parsed = try parse("livtet://sync?v=2&nodeId=\(nodeId)&ip=&host=10.0.0.9")

        XCTAssertEqual(parsed?.host, "10.0.0.9")
        XCTAssertEqual(parsed?.addresses, ["10.0.0.9"])
    }

    // MARK: - Scheme and host

    func testReturnsNilForNonLivtetScheme() throws {
        XCTAssertNil(try parse("https://example.com/sync?v=2&nodeId=\(nodeId)"))
    }

    func testReturnsNilForWrongHost() throws {
        XCTAssertNil(try parse("livtet://other?v=2&nodeId=\(nodeId)"))
    }

    func testThrowsWhenTheLinkCarriesNoQuery() {
        expectInvalidFormat(in: "livtet://sync")
    }

    // MARK: - Version rejection

    func testRejectsVersionOneTokenLinks() {
        expectInvalidFormat(
            in: "livtet://sync?v=1&desktopId=\(ulid)&token=abc&ip=1.2.3.4&port=3120"
        )
    }

    func testRejectsMissingVersion() {
        expectInvalidFormat(in: "livtet://sync?nodeId=\(nodeId)&port=3120")
    }

    func testRejectsUnknownVersion() {
        expectInvalidFormat(in: "livtet://sync?v=3&nodeId=\(nodeId)")
    }

    // MARK: - Node id rejection

    func testRejectsMissingNodeId() {
        expectMissingParameter("nodeId", in: "livtet://sync?v=2&port=3120")
    }

    func testRejectsEmptyNodeId() {
        expectMissingParameter("nodeId", in: "livtet://sync?v=2&nodeId=")
    }

    func testRejectsMalformedNodeId() {
        expectMissingParameter("nodeId", in: "livtet://sync?v=2&nodeId=not-a-node-id")
    }

    func testRejectsUppercaseHexNodeId() {
        expectMissingParameter("nodeId", in: "livtet://sync?v=2&nodeId=\(nodeId.uppercased())")
    }

    func testRejectsMalformedSnakeCaseNodeId() {
        expectMissingParameter("nodeId", in: "livtet://sync?v=2&node_id=oops")
    }

    // MARK: - Port rejection and boundaries

    func testRejectsNonNumericPort() {
        expectInvalidFormat(in: "livtet://sync?v=2&nodeId=\(nodeId)&port=abc")
    }

    func testRejectsEmptyPort() {
        expectInvalidFormat(in: "livtet://sync?v=2&nodeId=\(nodeId)&port=")
    }

    func testRejectsWhitespacePaddedPort() {
        expectInvalidFormat(in: "livtet://sync?v=2&nodeId=\(nodeId)&port=%203120")
    }

    func testRejectsPortOverflow() {
        expectInvalidFormat(in: "livtet://sync?v=2&nodeId=\(nodeId)&port=65536")
    }

    func testAcceptsBoundaryPorts() throws {
        XCTAssertEqual(
            try parse("livtet://sync?v=2&nodeId=\(nodeId)&port=0")?.port,
            0
        )
        XCTAssertEqual(
            try parse("livtet://sync?v=2&nodeId=\(nodeId)&port=65535")?.port,
            UInt16.max
        )
    }

    // MARK: - Error surface

    func testErrorsAreSyncURLParseErrorsWithDescriptions() {
        XCTAssertThrowsError(try parse("livtet://sync?v=1")) { error in
            XCTAssertTrue(error is SyncURLParseError)
        }

        XCTAssertThrowsError(try parse("livtet://sync?v=2")) { error in
            XCTAssertEqual(
                error.localizedDescription,
                "Missing required parameter 'nodeId'"
            )
        }
    }

    // MARK: - Scanned / pasted input

    func testBareNodeIdIsATarget() throws {
        let target = try XCTUnwrap(PairingInput.target(from: "  \(nodeId)\n"))

        XCTAssertEqual(target.nodeId, nodeId)
        XCTAssertNil(target.host)
        XCTAssertNil(target.port)
        XCTAssertNil(target.name)
        XCTAssertEqual(target.addresses, [])
    }

    func testBareUlidIsATarget() throws {
        let target = try XCTUnwrap(PairingInput.target(from: ulid))

        XCTAssertEqual(target.nodeId, ulid)
        XCTAssertEqual(target.addresses, [])
    }

    func testUrlIsATarget() throws {
        let target = try XCTUnwrap(PairingInput.target(
            from: "livtet://sync?v=2&nodeId=\(nodeId)&host=10.0.0.5&port=3120&name=Nimble%20Quail"
        ))

        XCTAssertEqual(target.nodeId, nodeId)
        XCTAssertEqual(target.host, "10.0.0.5")
        XCTAssertEqual(target.port, 3_120)
        XCTAssertEqual(target.name, "Nimble Quail")
        XCTAssertEqual(target.addresses, ["10.0.0.5"])
    }

    func testJunkIsNotATarget() {
        XCTAssertNil(PairingInput.target(from: "hello"))
        XCTAssertNil(PairingInput.target(from: ""))
        XCTAssertNil(PairingInput.target(from: "livtet://sync?v=2"))
        XCTAssertNil(PairingInput.target(from: "livtet://sync?v=1&token=abc"))
        XCTAssertNil(PairingInput.target(from: nodeId.uppercased()))
        XCTAssertNil(PairingInput.target(from: String(repeating: "z", count: 64)))
    }

    func testNodeIdValidation() {
        XCTAssertTrue(PairingInput.isNodeId(nodeId))
        XCTAssertTrue(PairingInput.isNodeId(ulid))
        XCTAssertFalse(PairingInput.isNodeId(nodeId.uppercased()))
        XCTAssertFalse(PairingInput.isNodeId(String(nodeId.dropLast())))
        XCTAssertFalse(PairingInput.isNodeId("not-a-node-id"))
        XCTAssertFalse(PairingInput.isNodeId(""))
    }
}
