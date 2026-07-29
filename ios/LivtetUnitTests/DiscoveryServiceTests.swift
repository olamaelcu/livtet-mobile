@testable import Livtet
import Combine
import XCTest

final class DiscoveryServiceTests: XCTestCase {
    func testStartsInEmptyState() async {
        let service = DiscoveryService()
        let expectation = XCTestExpectation(description: "desktops emits initial empty value")
        var cancellables: Set<AnyCancellable> = []
        var received: [Desktop]?
        service.desktops
            .sink { value in
                received = value
                expectation.fulfill()
            }
            .store(in: &cancellables)
        await fulfillment(of: [expectation], timeout: 1)
        XCTAssertEqual(received?.isEmpty, true)
    }

    func testStopIsIdempotent() {
        let service = DiscoveryService()
        service.stop()
        service.stop()
    }
}
