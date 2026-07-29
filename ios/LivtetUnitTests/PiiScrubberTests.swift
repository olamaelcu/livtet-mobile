import Sentry
import XCTest

final class PiiScrubberTests: XCTestCase {
    func testScrubsDeviceId() {
        let event = makeEvent(extras: ["device_id": "abc-123", "safe_field": "keep"])
        XCTAssertNotNil(event.extra)
        XCTAssertEqual(event.extra?["safe_field"] as? String, "keep")
        XCTAssertNil(event.extra?["device_id"])
    }

    func testScrubsSyncToken() {
        let event = makeEvent(extras: ["sync_token": "token-xyz"])
        XCTAssertNil(event.extra?["sync_token"])
    }

    func testScrubsReadingProgress() {
        let event = makeEvent(extras: ["reading_progress": "42%"])
        XCTAssertNil(event.extra?["reading_progress"])
    }

    func testScrubsAuthPrefixedFields() {
        let event = makeEvent(extras: ["auth_token": "secret", "auth_header": "Bearer x", "auth": "keep"])
        XCTAssertNil(event.extra?["auth_token"])
        XCTAssertNil(event.extra?["auth_header"])
        XCTAssertEqual(event.extra?["auth"] as? String, "keep")
    }

    func testScrubsSecretPrefixedFields() {
        let event = makeEvent(extras: ["secret_key": "hush", "secret_token": "quiet", "secret": "keep"])
        XCTAssertNil(event.extra?["secret_key"])
        XCTAssertNil(event.extra?["secret_token"])
        XCTAssertEqual(event.extra?["secret"] as? String, "keep")
    }

    func testScrubsTags() {
        let event = makeEvent(tags: ["device_id": "abc"])
        XCTAssertNil(event.tags?["device_id"])
    }

    private func makeEvent(extras: [String: Any] = [:], tags: [String: String] = [:]) -> Event {
        let event = Event()
        if !extras.isEmpty {
            event.extra = extras
        }
        if !tags.isEmpty {
            event.tags = tags
        }
        return event
    }
}
