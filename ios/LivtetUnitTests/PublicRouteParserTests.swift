@testable import Livtet
import XCTest

final class PublicRouteParserTests: XCTestCase {
    private func parse(_ raw: String) -> PublicRoute? {
        guard let url = URL(string: raw) else { return nil }
        return url.parseLivtetPublicURL()
    }

    // MARK: - Accepted

    func testParsesCanonicalBookPermalink() {
        XCTAssertEqual(
            parse("https://livtet.olamaelcu.net/book/did:web:biblio.livtet.olamaelcu.net/ol.OL3425796M"),
            .book(did: PublicLink.bibliographDid, rkey: "ol.OL3425796M")
        )
    }

    func testResolvesTheCatalogAlias() {
        XCTAssertEqual(
            parse("https://livtet.olamaelcu.net/book/bibliograph/ol.OL3425796M"),
            .book(did: PublicLink.bibliographDid, rkey: "ol.OL3425796M")
        )
    }

    func testToleratesATrailingSlash() {
        XCTAssertEqual(
            parse("https://livtet.olamaelcu.net/book/did:plc:abc/3abc/"),
            .book(did: "did:plc:abc", rkey: "3abc")
        )
    }

    func testParsesReviewPermalink() {
        XCTAssertEqual(
            parse("https://livtet.olamaelcu.net/review/did:plc:e2ctbutx6kya6si4if5ngjmm/3myy5wjqiydz7"),
            .review(did: "did:plc:e2ctbutx6kya6si4if5ngjmm", rkey: "3myy5wjqiydz7")
        )
    }

    func testParsesProfileHandleWithDefaultTab() {
        XCTAssertEqual(
            parse("https://livtet.olamaelcu.net/profile/jacky.wtf"),
            .profile(actor: "jacky.wtf", tab: .reviews, shelf: nil)
        )
    }

    func testParsesProfileByDid() {
        XCTAssertEqual(
            parse("https://livtet.olamaelcu.net/profile/did:plc:e2ctbutx6kya6si4if5ngjmm"),
            .profile(actor: "did:plc:e2ctbutx6kya6si4if5ngjmm", tab: .reviews, shelf: nil)
        )
    }

    func testParsesTabQueryValue() {
        XCTAssertEqual(
            parse("https://livtet.olamaelcu.net/profile/jacky.wtf?tab=to-read"),
            .profile(actor: "jacky.wtf", tab: .toRead, shelf: nil)
        )
    }

    func testFallsBackToReviewsForAnUnknownTab() {
        XCTAssertEqual(
            parse("https://livtet.olamaelcu.net/profile/jacky.wtf?tab=whatever"),
            .profile(actor: "jacky.wtf", tab: .reviews, shelf: nil)
        )
    }

    func testMapsShelfIndexToShelvesTab() {
        XCTAssertEqual(
            parse("https://livtet.olamaelcu.net/profile/did:plc:e2ctbutx6kya6si4if5ngjmm/shelf"),
            .profile(actor: "did:plc:e2ctbutx6kya6si4if5ngjmm", tab: .shelves, shelf: nil)
        )
    }

    func testMapsOneShelfWithTheShelfSelected() {
        XCTAssertEqual(
            parse("https://livtet.olamaelcu.net/profile/did:plc:e2ctbutx6kya6si4if5ngjmm/shelf/read"),
            .profile(actor: "did:plc:e2ctbutx6kya6si4if5ngjmm", tab: .shelves, shelf: "read")
        )
    }

    func testParsesTheUAliasOfAProfile() {
        XCTAssertEqual(
            parse("https://livtet.olamaelcu.net/u/did:plc:abc"),
            .profile(actor: "did:plc:abc", tab: .reviews, shelf: nil)
        )
    }

    func testParsesCustomSchemeWithHostAsPrefix() {
        XCTAssertEqual(
            parse("livtet://book/bibliograph/ol.OL3425796M"),
            .book(did: PublicLink.bibliographDid, rkey: "ol.OL3425796M")
        )
        XCTAssertEqual(
            parse("livtet://review/did:plc:abc/3abc"),
            .review(did: "did:plc:abc", rkey: "3abc")
        )
        XCTAssertEqual(
            parse("livtet://profile/jacky.wtf?tab=finished"),
            .profile(actor: "jacky.wtf", tab: .finished, shelf: nil)
        )
    }

    // MARK: - Rejected

    func testRejectsUnhandledInput() {
        let rejected = [
            "not a url",
            "livtet://sync?v=1&desktopId=abc&port=1&token=\(String(repeating: "A", count: 26))&ip=1.2.3.4",
            "https://example.com/book/did:plc:abc/3abc",
            "livtet://unknown/did:plc:abc/3abc",
            "https://livtet.olamaelcu.net/book/did:plc:abc",
            "https://livtet.olamaelcu.net/book/did:plc:abc/3abc/extra",
            "https://livtet.olamaelcu.net/review",
            "https://livtet.olamaelcu.net/review/did:plc:abc/a/b",
            "https://livtet.olamaelcu.net/profile",
            "https://livtet.olamaelcu.net/profile/jacky.wtf/books",
            "https://livtet.olamaelcu.net/profile/jacky.wtf/shelf/read/extra",
            "https://livtet.olamaelcu.net/u",
            "https://livtet.olamaelcu.net/book/notadid/3abc",
            "https://livtet.olamaelcu.net/book/did:plc:abc/%20",
            "https://livtet.olamaelcu.net/profile/not%20a%20handle",
            "https://livtet.olamaelcu.net/profile/jacky.wtf/shelf/read%2Fmore",
            "livtet://book/did:plc:abc/\(String(repeating: "x", count: 513))",
        ]
        for raw in rejected {
            XCTAssertNil(parse(raw), "expected nil for \(raw)")
        }
    }

    // MARK: - Tab vocabulary

    func testTabNormalizationAndStatusMapping() {
        XCTAssertEqual(ProfileViewTab.normalize(nil), .reviews)
        XCTAssertEqual(ProfileViewTab.normalize("shelves"), .shelves)
        XCTAssertEqual(ProfileViewTab.normalize("abandoned"), .abandoned)
        XCTAssertEqual(ProfileViewTab.normalize("nope"), .reviews)
        XCTAssertEqual(ProfileViewTab.reading.shelvingStatus, "reading")
        XCTAssertEqual(ProfileViewTab.finished.shelvingStatus, "read")
        XCTAssertEqual(ProfileViewTab.toRead.shelvingStatus, "to-read")
        XCTAssertEqual(ProfileViewTab.abandoned.shelvingStatus, "dnf")
        XCTAssertNil(ProfileViewTab.reviews.shelvingStatus)
        XCTAssertNil(ProfileViewTab.shelves.shelvingStatus)
    }

    func testDeepLinkRouterPrefersPublicRoutes() {
        let router = DeepLinkRouter.shared
        router.presentedPublicRoute = nil
        XCTAssertTrue(router.handleURL(URL(string: "livtet://profile/jacky.wtf")!))
        XCTAssertEqual(
            router.presentedPublicRoute,
            .profile(actor: "jacky.wtf", tab: .reviews, shelf: nil)
        )
        router.presentedPublicRoute = nil
        XCTAssertFalse(router.handleURL(URL(string: "https://example.com/profile/jacky.wtf")!))
        XCTAssertNil(router.presentedPublicRoute)
    }
}
