import FastULID
@testable import Livtet
import LivtetKit
import LivtetKitFFI
import XCTest

@MainActor
final class LibraryViewModelTests: XCTestCase {
    private func makeBook(_ title: String) -> Book {
        Book(
            id: ULID(ulidData: Data(repeating: 0, count: 16))!,
            title: title,
            description: nil
        )
    }

    func testLoadSuccessPopulatesAllFields() async {
        let mock = MockLibraryBridge()
        mock.formats = [FormatInfo(id: ULID(ulidData: Data(repeating: 0, count: 16))!, name: "Paperback")]
        mock.languages = [LanguageInfo(id: ULID(ulidData: Data(repeating: 0, count: 16))!, name: "English", flagEmoji: nil)]
        mock.statuses = [WorkStatusInfo(id: ULID(ulidData: Data(repeating: 0, count: 16))!, name: "Reading")]
        mock.books = [makeBook("Beloved"), makeBook("Song of Solomon")]

        let viewModel = LibraryViewModel(bridge: mock)
        viewModel.load()
        // Give the parallel-load + state propagation time to settle.
        try? await Task.sleep(nanoseconds: 100_000_000)

        XCTAssertEqual(viewModel.formats.count, 1)
        XCTAssertEqual(viewModel.languages.count, 1)
        XCTAssertEqual(viewModel.statuses.count, 1)
        XCTAssertEqual(viewModel.books.count, 2)
        XCTAssertNil(viewModel.error)
        XCTAssertFalse(viewModel.isLoading)
    }

    func testFilterChangesTriggerRefetchWithDebounce() async {
        let mock = MockLibraryBridge()
        mock.books = []

        let viewModel = LibraryViewModel(bridge: mock)
        let fmt1 = ULID(ulidData: Data(repeating: 1, count: 16))!
        let fmt2 = ULID(ulidData: Data(repeating: 2, count: 16))!
        let fmt3 = ULID(ulidData: Data(repeating: 3, count: 16))!
        viewModel.selectedFormatIds = [fmt1]
        viewModel.selectedFormatIds = [fmt1, fmt2]
        viewModel.selectedFormatIds = [fmt1, fmt2, fmt3]
        // Wait past the 150 ms debounce.
        try? await Task.sleep(nanoseconds: 300_000_000)

        // Only the final set should have triggered a call.
        XCTAssertEqual(mock.calls.count, 1)
        XCTAssertEqual(mock.calls.last?.formatIds, [fmt1, fmt2, fmt3])
    }

    func testErrorSurfacesAndRetryClearsIt() async {
        let mock = MockLibraryBridge()
        mock.error = AppError.database("connection refused")

        let viewModel = LibraryViewModel(bridge: mock)
        viewModel.load()
        try? await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertNotNil(viewModel.error)

        // Clear the failure and retry.
        mock.error = nil
        viewModel.retry()
        try? await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertNil(viewModel.error)
    }

    func testRefreshTriggersNewLoad() async {
        let mock = MockLibraryBridge()
        mock.books = [makeBook("Initial")]

        let viewModel = LibraryViewModel(bridge: mock)
        viewModel.load()
        try? await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertEqual(viewModel.books.first?.title, "Initial")

        mock.books = [makeBook("Refreshed")]
        viewModel.refresh()
        try? await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertEqual(viewModel.books.first?.title, "Refreshed")
    }

    func testIdempotentUnderInFlightLoad() async {
        let mock = MockLibraryBridge()
        mock.artificialDelayNanos = 100_000_000 // 100ms per call
        mock.books = [makeBook("X")]

        let viewModel = LibraryViewModel(bridge: mock)
        viewModel.load()
        viewModel.load()
        viewModel.load()
        // Wait long enough for all three to settle (debounce cancels predecessors).
        try? await Task.sleep(nanoseconds: 300_000_000)

        XCTAssertEqual(viewModel.books.count, 1)
        XCTAssertFalse(viewModel.isLoading)
    }

    func testSupersededFetchIsCancelled() async {
        let mock = MockLibraryBridge()
        mock.artificialDelayNanos = 200_000_000 // 200ms
        mock.books = []

        let viewModel = LibraryViewModel(bridge: mock)
        viewModel.load()
        // Trigger another load before the first finishes.
        try? await Task.sleep(nanoseconds: 50_000_000)
        viewModel.refresh()
        try? await Task.sleep(nanoseconds: 400_000_000)

        // Exactly one books fetch call should have completed.
        XCTAssertEqual(mock.calls.count, 1)
        XCTAssertFalse(viewModel.isLoading)
    }

    func testSelectedStatusIdsInert() async {
        let mock = MockLibraryBridge()
        mock.books = [makeBook("X")]

        let viewModel = LibraryViewModel(bridge: mock)
        let reading = ULID(ulidData: Data(repeating: 4, count: 16))!
        let finished = ULID(ulidData: Data(repeating: 5, count: 16))!
        // Toggling a status chip must NOT re-fetch and must NOT be
        // forwarded to the bridge in the filter shape.
        viewModel.selectedStatusIds = [reading]
        viewModel.selectedStatusIds = [reading, finished]
        try? await Task.sleep(nanoseconds: 300_000_000)

        XCTAssertEqual(
            mock.calls.count, 0,
            "status chip toggles must not invoke listBooksWithFilters"
        )
        // Capture-and-assert after one load (status ids still don't leak).
        viewModel.load()
        try? await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertEqual(mock.calls.count, 1)
        XCTAssertTrue(mock.calls[0].formatIds.isEmpty)
        XCTAssertTrue(mock.calls[0].languageIds.isEmpty)
    }
}
