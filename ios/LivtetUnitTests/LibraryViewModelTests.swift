@testable import Livtet
import LivtetKit
import XCTest

@MainActor
final class LibraryViewModelTests: XCTestCase {
    private func makeWork(_ title: String) -> WorkSummary {
        WorkSummary(
            id: "01ARZ3NDEKTSV4RRFFQ69G5FAV",
            title: title,
            sortTitle: nil,
            description: nil,
            authors: ["Toni Morrison"],
            createdAt: "2024-01-01T00:00:00Z",
            updatedAt: nil
        )
    }

    func testLoadSuccessPopulatesAllFields() async {
        let mock = MockLibraryBridge()
        mock.formats = [FormatInfo(id: "01ARZ3NDEKTSV4RRFFQ69G5FA1", name: "Paperback", metadataSchema: "{}")]
        mock.languages = [LibraryLanguage(id: "01ARZ3NDEKTSV4RRFFQ69G5FA2", name: "English", flagEmoji: nil)]
        mock.works = [makeWork("Beloved"), makeWork("Song of Solomon")]

        let viewModel = LibraryViewModel(bridge: mock)
        viewModel.load()
        // Give the parallel-load + state propagation time to settle.
        try? await Task.sleep(nanoseconds: 100_000_000)

        XCTAssertEqual(viewModel.formats.count, 1)
        XCTAssertEqual(viewModel.languages.count, 1)
        XCTAssertEqual(viewModel.works.count, 2)
        XCTAssertNil(viewModel.error)
        XCTAssertFalse(viewModel.isLoading)
    }

    func testFilterChangesTriggerRefetchWithDebounce() async {
        let mock = MockLibraryBridge()
        mock.works = []

        let viewModel = LibraryViewModel(bridge: mock)
        let fmt1 = "01ARZ3NDEKTSV4RRFFQ69G5FB1"
        let fmt2 = "01ARZ3NDEKTSV4RRFFQ69G5FB2"
        let fmt3 = "01ARZ3NDEKTSV4RRFFQ69G5FB3"
        viewModel.selectedFormatIds = [fmt1]
        viewModel.selectedFormatIds = [fmt1, fmt2]
        viewModel.selectedFormatIds = [fmt1, fmt2, fmt3]
        // Wait past the 150 ms debounce.
        try? await Task.sleep(nanoseconds: 300_000_000)

        // Only the final set should have triggered a call.
        XCTAssertEqual(mock.calls.count, 1)
        XCTAssertEqual(mock.calls.last?.formatIds.sorted(), [fmt1, fmt2, fmt3].sorted())
    }

    /// Polls until `condition` holds or `timeout` elapses; the view-model
    /// hops through `Task`/Combine so fixed sleeps are flaky under load.
    private func waitUntil(
        timeout: TimeInterval = 2,
        _ condition: () -> Bool
    ) async {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition() && Date() < deadline {
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
    }

    func testErrorSurfacesAndRetryClearsIt() async {
        let mock = MockLibraryBridge()
        mock.error = AppError.database("connection refused")

        let viewModel = LibraryViewModel(bridge: mock)
        viewModel.load()
        await waitUntil { viewModel.error != nil }
        XCTAssertNotNil(viewModel.error)

        // Clear the failure and retry.
        mock.error = nil
        viewModel.retry()
        await waitUntil { viewModel.error == nil }
        XCTAssertNil(viewModel.error)
    }

    func testRefreshTriggersNewLoad() async {
        let mock = MockLibraryBridge()
        mock.works = [makeWork("Initial")]

        let viewModel = LibraryViewModel(bridge: mock)
        viewModel.load()
        try? await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertEqual(viewModel.works.first?.title, "Initial")

        mock.works = [makeWork("Refreshed")]
        viewModel.refresh()
        try? await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertEqual(viewModel.works.first?.title, "Refreshed")
    }

    func testIdempotentUnderInFlightLoad() async {
        let mock = MockLibraryBridge()
        mock.artificialDelayNanos = 100_000_000 // 100ms per call
        mock.works = [makeWork("X")]

        let viewModel = LibraryViewModel(bridge: mock)
        viewModel.load()
        viewModel.load()
        viewModel.load()
        // Wait long enough for all three to settle (debounce cancels predecessors).
        try? await Task.sleep(nanoseconds: 300_000_000)

        XCTAssertEqual(viewModel.works.count, 1)
        XCTAssertFalse(viewModel.isLoading)
    }

    func testSupersededFetchIsCancelled() async {
        let mock = MockLibraryBridge()
        mock.artificialDelayNanos = 200_000_000 // 200ms
        mock.works = []

        let viewModel = LibraryViewModel(bridge: mock)
        viewModel.load()
        // Trigger another load before the first finishes.
        try? await Task.sleep(nanoseconds: 50_000_000)
        viewModel.refresh()
        try? await Task.sleep(nanoseconds: 400_000_000)

        // The cancelled predecessor's bridge call may still complete, but the
        // view-model must settle on the latest fetch and stop loading.
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertTrue(viewModel.works.isEmpty)
        XCTAssertGreaterThanOrEqual(mock.calls.count, 1)
    }

    func testSortDirectionIsForwarded() async {
        let mock = MockLibraryBridge()
        let viewModel = LibraryViewModel(bridge: mock)
        viewModel.load()
        try? await Task.sleep(nanoseconds: 100_000_000)

        viewModel.sortOrder = .asc
        try? await Task.sleep(nanoseconds: 300_000_000)

        XCTAssertEqual(mock.calls.last?.sortDirection, .asc)
    }
}
