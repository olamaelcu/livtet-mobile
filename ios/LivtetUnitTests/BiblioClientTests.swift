@testable import Livtet
import XCTest

/// Serves canned responses so the client's request shapes and fallbacks can be
/// asserted without a network.
final class StubURLProtocol: URLProtocol {
    static var handler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

    override class func canInit(with request: URLRequest) -> Bool { true }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let handler = Self.handler else {
            client?.urlProtocol(self, didFailWithError: URLError(.unsupportedURL))
            return
        }
        do {
            let (response, data) = try handler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}

final class BiblioClientTests: XCTestCase {
    private let editionUri =
        "at://did:web:biblio.livtet.olamaelcu.net/community.lexicon.book.edition/ol.OL3425796M"

    private var requests: [URLRequest] = []

    private func makeClient() -> BiblioClient {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubURLProtocol.self]
        return BiblioClient(session: URLSession(configuration: configuration))
    }

    private func respond(_ body: String, status: Int = 200, url: URL) throws -> (HTTPURLResponse, Data) {
        let response = HTTPURLResponse(
            url: url,
            statusCode: status,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        return (response, Data(body.utf8))
    }

    override func tearDown() {
        StubURLProtocol.handler = nil
        requests = []
        super.tearDown()
    }

    func testGetEditionDecodesAHydratedView() async throws {
        let payload = """
        {
          "uri": "\(editionUri)",
          "cid": "bafyedition",
          "value": {
            "uri": "\(editionUri)",
            "title": "Dune",
            "coverImageUrl": "https://covers.example/dune.jpg",
            "pages": 512,
            "language": "en",
            "identifiers": [{ "resource": "isbn", "uri": "isbn:9780441172719" }],
            "contributors": [
              { "contributor": { "uri": "at://did:plc:c1/community.lexicon.book.contributor/herbert", "name": "Frank Herbert" }, "role": "author" },
              { "contributor": { "uri": "at://did:plc:c2/community.lexicon.book.contributor/x", "name": "Someone Else" }, "role": "editor" }
            ]
          }
        }
        """
        let client = makeClient()
        StubURLProtocol.handler = { request in
            self.requests.append(request)
            return try self.respond(payload, url: request.url!)
        }

        let response = try await client.getEdition(atUri: editionUri)

        XCTAssertEqual(response.value.title, "Dune")
        XCTAssertEqual(response.value.authorLine, "Frank Herbert")
        XCTAssertEqual(response.value.isbn, "9780441172719")
        XCTAssertEqual(requests.count, 1)
        let request = try XCTUnwrap(requests.first)
        XCTAssertEqual(request.url?.path, "/xrpc/com.atproto.repo.getRecord")
        let query = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems ?? []
        let values = Dictionary(uniqueKeysWithValues: query.map { ($0.name, $0.value ?? "") })
        XCTAssertEqual(values["repo"], "did:web:biblio.livtet.olamaelcu.net")
        XCTAssertEqual(values["collection"], "community.lexicon.book.edition")
        XCTAssertEqual(values["rkey"], "ol.OL3425796M")
    }

    func testMissingOptionalEditionFieldsDecode() async throws {
        let payload = """
        { "uri": "\(editionUri)", "value": { "title": "Dune" } }
        """
        let client = makeClient()
        StubURLProtocol.handler = { request in try self.respond(payload, url: request.url!) }

        let response = try await client.getEdition(atUri: editionUri)

        XCTAssertEqual(response.value.title, "Dune")
        XCTAssertNil(response.value.pages)
        XCTAssertNil(response.value.authorLine)
    }

    func testGetEditionMapsHttpFailures() async throws {
        let client = makeClient()
        StubURLProtocol.handler = { request in
            try self.respond("{}", status: 404, url: request.url!)
        }
        do {
            _ = try await client.getEdition(atUri: editionUri)
            XCTFail("expected a throw")
        } catch let error as BiblioClientError {
            guard case .notFound = error else {
                return XCTFail("unexpected error \(error)")
            }
        }

        StubURLProtocol.handler = { request in
            try self.respond("{}", status: 500, url: request.url!)
        }
        do {
            _ = try await client.getEdition(atUri: editionUri)
            XCTFail("expected a throw")
        } catch let error as BiblioClientError {
            guard case let .http(status) = error else {
                return XCTFail("unexpected error \(error)")
            }
            XCTAssertEqual(status, 500)
        }
    }

    func testEmptyAppViewListFallsBackToBacklinksAndPds() async throws {
        let didDocument = """
        { "service": [{ "id": "#atproto_pds", "type": "AtprotoPersonalDataServer", "serviceEndpoint": "https://pds.test" }] }
        """
        let reviewRecord = """
        {
          "uri": "at://did:plc:r1/net.olamaelcu.livtet.bookReview/3rk1",
          "value": {
            "text": "Still the best.",
            "book": { "uri": "\(editionUri)", "cid": "bafybook" },
            "rating": 9.5,
            "createdAt": "2025-02-03T04:05:06.000Z"
          }
        }
        """
        let client = makeClient()
        StubURLProtocol.handler = { request in
            let url = request.url!
            let path = url.path
            self.requests.append(request)
            if path.hasSuffix(".getReviewsByBook") {
                return try self.respond(#"{ "reviews": [] }"#, url: url)
            }
            if path.hasSuffix(".getBacklinks") {
                let backlinks = """
                { "records": [{ "did": "did:plc:r1", "collection": "net.olamaelcu.livtet.bookReview", "rkey": "3rk1" }] }
                """
                return try self.respond(backlinks, url: url)
            }
            if url.host == "plc.directory" {
                return try self.respond(didDocument, url: url)
            }
            if url.host == "pds.test" {
                return try self.respond(reviewRecord, url: url)
            }
            return try self.respond("{}", status: 404, url: url)
        }

        let reviews = try await client.getBookReviews(atUri: editionUri)

        XCTAssertEqual(reviews.count, 1)
        XCTAssertEqual(reviews.first?.record.text, "Still the best.")
        XCTAssertEqual(reviews.first?.record.rating, 9.5)
        XCTAssertEqual(reviews.first?.record.bookUri, editionUri)
        XCTAssertTrue(requests.contains { $0.url?.path.hasSuffix(".getBacklinks") == true })
        XCTAssertTrue(requests.contains { $0.url?.host == "pds.test" })
    }

    func testStringAndStrongRefBookSpellingsBothDecode() throws {
        let decoder = JSONDecoder()
        let asString = try decoder.decode(
            BiblioReviewRecord.self,
            from: Data(#"{"text":"Fine.","book":"\#(editionUri)"}"#.utf8)
        )
        XCTAssertEqual(asString.bookUri, editionUri)

        let asRef = try decoder.decode(
            BiblioReviewRecord.self,
            from: Data(#"{"text":"Fine.","book":{"uri":"\#(editionUri)","cid":"bafy"}}"#.utf8)
        )
        XCTAssertEqual(asRef.bookUri, editionUri)
    }

    func testResolvePdsForPlcAndWeb() async throws {
        let didDocument = """
        { "service": [{ "id": "#atproto_pds", "type": "AtprotoPersonalDataServer", "serviceEndpoint": "https://pds.test" }] }
        """
        let client = makeClient()
        StubURLProtocol.handler = { request in
            self.requests.append(request)
            return try self.respond(didDocument, url: request.url!)
        }

        let plc = try await client.resolvePds(did: "did:plc:e2ctbutx6kya6si4if5ngjmm")
        XCTAssertEqual(plc?.absoluteString, "https://pds.test")
        XCTAssertEqual(requests.first?.url?.absoluteString, "https://plc.directory/did:plc:e2ctbutx6kya6si4if5ngjmm")

        let web = try await client.resolvePds(did: "did:web:biblio.livtet.olamaelcu.net")
        XCTAssertEqual(web?.absoluteString, "https://pds.test")
        XCTAssertEqual(
            requests.last?.url?.absoluteString,
            "https://biblio.livtet.olamaelcu.net/.well-known/did.json"
        )
    }

    func testDidDocumentUrlsFollowTheDidMethod() {
        XCTAssertEqual(
            BiblioClient.didDocumentURL(did: "did:plc:abc")?.absoluteString,
            "https://plc.directory/did:plc:abc"
        )
        XCTAssertEqual(
            BiblioClient.didDocumentURL(did: "did:web:biblio.livtet.olamaelcu.net")?.absoluteString,
            "https://biblio.livtet.olamaelcu.net/.well-known/did.json"
        )
        XCTAssertEqual(
            BiblioClient.didDocumentURL(did: "did:web:example.com%3A8443:users:alice")?.absoluteString,
            "https://example.com:8443/users/alice/did.json"
        )
        XCTAssertNil(BiblioClient.didDocumentURL(did: "did:key:zabc"))
    }

    func testUnresolvableDidYieldsNoDiscovery() async throws {
        let client = makeClient()
        StubURLProtocol.handler = { request in try self.respond("{}", status: 404, url: request.url!) }

        let review = try await client.getReview(did: "did:plc:missing", rkey: "3rk1")
        XCTAssertNil(review)
        let reviews = try await client.listActorReviews(did: "did:plc:missing")
        XCTAssertTrue(reviews.isEmpty)
    }
}
