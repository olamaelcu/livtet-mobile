@testable import Livtet
import XCTest

final class NetworkClientTests: XCTestCase {
    private let nodeId = String(repeating: "ab", count: 32)

    private var requests: [URLRequest] = []

    private func client() -> NetworkClient {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubURLProtocol.self]
        return NetworkClient(session: URLSession(configuration: configuration))
    }

    private func respond(
        _ body: String,
        status: Int = 200,
        url: URL
    ) -> (HTTPURLResponse, Data) {
        let response = HTTPURLResponse(
            url: url,
            statusCode: status,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        return (response, Data(body.utf8))
    }

    private func bodyData(of request: URLRequest) -> Data? {
        if let body = request.httpBody { return body }
        guard let stream = request.httpBodyStream else { return nil }
        stream.open()
        defer { stream.close() }
        var data = Data()
        let size = 1024
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: size)
        defer { buffer.deallocate() }
        while stream.hasBytesAvailable {
            let read = stream.read(buffer, maxLength: size)
            if read <= 0 { break }
            data.append(buffer, count: read)
        }
        return data
    }

    override func tearDown() {
        StubURLProtocol.handler = nil
        requests = []
        super.tearDown()
    }

    func testPairSendsTheNodeIdAndDecodesTheClaim() async throws {
        StubURLProtocol.handler = { [self] request in
            requests.append(request)
            return respond("{\"status\":\"pending\",\"claim\":\"claim-1\"}", url: request.url!)
        }

        let status = try await client().pair(
            nodeId: nodeId,
            deviceId: "01ARZ3NDEKTSV4RRFFQ69G5FAV",
            name: "Phone",
            deviceType: "ios",
            host: "10.0.0.5",
            port: 3120
        )

        XCTAssertEqual(status.status, "pending")
        XCTAssertEqual(status.claim, "claim-1")

        let request = try XCTUnwrap(requests.first)
        XCTAssertEqual(request.url?.path, "/sync/pair")
        XCTAssertEqual(request.httpMethod, "POST")
        let body = try XCTUnwrap(bodyData(of: request))
        let json = try XCTUnwrap(
            JSONSerialization.jsonObject(with: body) as? [String: String]
        )
        XCTAssertEqual(json["node_id"], nodeId)
        XCTAssertEqual(json["device_id"], "01ARZ3NDEKTSV4RRFFQ69G5FAV")
        XCTAssertEqual(json["device_type"], "ios")
        XCTAssertNil(json["token"], "the token flow is gone")
    }

    func testPairWithoutAClaimIsAnError() async {
        StubURLProtocol.handler = { [self] request in
            respond("{\"status\":\"pending\"}", url: request.url!)
        }

        do {
            _ = try await client().pair(
                nodeId: nodeId,
                deviceId: "device",
                name: "Phone",
                deviceType: "ios",
                host: "10.0.0.5",
                port: 3120
            )
            XCTFail("a pending response with no claim must not pair")
        } catch {
            // Expected: the caller cannot open the status stream without it.
        }
    }

    func testPairSurfacesTheHTTPStatus() async {
        StubURLProtocol.handler = { [self] request in
            respond("conflict", status: 409, url: request.url!)
        }

        do {
            _ = try await client().pair(
                nodeId: nodeId,
                deviceId: "device",
                name: "Phone",
                deviceType: "ios",
                host: "10.0.0.5",
                port: 3120
            )
            XCTFail("a 409 must not read as success")
        } catch {
            XCTAssertTrue("\(error)".contains("409"))
        }
    }

    func testIdentityDecodesTheNodeAndDeviceIds() async throws {
        StubURLProtocol.handler = { [self] request in
            respond(
                "{\"node_id\":\"\(nodeId)\",\"device_id\":\"01ARZ3NDEKTSV4RRFFQ69G5FAV\"}",
                url: request.url!
            )
        }

        let identity = try await client().identity(host: "10.0.0.5", port: 3120)

        XCTAssertEqual(identity.nodeId, nodeId)
        XCTAssertEqual(identity.deviceId, "01ARZ3NDEKTSV4RRFFQ69G5FAV")
    }

    func testStatusURLCarriesTheNodeIdAndClaim() {
        let url = NetworkClient.pairStatusURL(
            nodeId: nodeId,
            claim: "claim 1",
            host: "10.0.0.5",
            port: 3120
        )

        XCTAssertEqual(url?.path, "/sync/pair/status/\(nodeId)")
        let components = URLComponents(url: try! XCTUnwrap(url), resolvingAgainstBaseURL: false)
        XCTAssertEqual(
            components?.queryItems,
            [URLQueryItem(name: "claim", value: "claim 1")]
        )
    }
}
