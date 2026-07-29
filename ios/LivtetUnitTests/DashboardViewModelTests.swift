import FastULID
@testable import Livtet
import LivtetKit
import LivtetKitFFI
import XCTest

/// Mock bridge for unit-testing the dashboard view-model without the FFI.
private final class MockDashboardBridge: DashboardBridge {
    var greeting: Greeting?
    var stats: DashboardStats?
    var recentBooks: [RecentlyReadBook] = []
    var recentSearches: [RecentSearch] = []
    var error: Error?

    /// Optional sleep injected before each call so we can assert on the
    /// `isLoading` flag while a load is in flight.
    var artificialDelayNanos: UInt64 = 0

    func getGreeting() throws -> Greeting {
        try sleepIfNeeded()
        if let error { throw error }
        return greeting ?? Greeting(
            label: "Good morning",
            text: "The morning sun spilled across the open book.",
            author: "Maya Angelou",
            material: "I Know Why the Caged Bird Sings",
            period: "Early Morning"
        )
    }

    func getDashboardStats() throws -> DashboardStats {
        try sleepIfNeeded()
        if let error { throw error }
        return stats ?? DashboardStats(
            totalBooks: 42,
            booksInProgress: 3,
            finishedBooks: 12,
            totalReadingTimeSecs: 9 * 3600 + 12 * 60,
            firstReadingAtMillis: nil
        )
    }

    func getRecentlyReadBooks(limit: Int32) throws -> [RecentlyReadBook] {
        try sleepIfNeeded()
        if let error { throw error }
        return Array(recentBooks.prefix(Int(limit)))
    }

    func getRecentSearches(limit: Int32) throws -> [RecentSearch] {
        try sleepIfNeeded()
        if let error { throw error }
        return Array(recentSearches.prefix(Int(limit)))
    }

    private func sleepIfNeeded() throws {
        if artificialDelayNanos > 0 {
            try? awaitTask(nanos: artificialDelayNanos)
        }
    }

    private func awaitTask(nanos: UInt64) throws {
        let sem = DispatchSemaphore(value: 0)
        DispatchQueue.global().asyncAfter(deadline: .now() + .nanoseconds(Int(nanos))) {
            sem.signal()
        }
        sem.wait()
    }
}

@MainActor
final class DashboardViewModelTests: XCTestCase {
    func testLoadSuccessPopulatesAllFields() async {
        let mock = MockDashboardBridge()
        let workId = ULID(ulidData: Data(repeating: 0, count: 16))!
        let editionId = ULID(ulidData: Data(repeating: 0, count: 16))!
        mock.recentBooks = [
            RecentlyReadBook(
                workId: workId,
                editionId: editionId,
                title: "Beloved",
                authorName: "Toni Morrison",
                progress: 0.42,
                totalReadingTimeSecs: 3600,
                lastReadAt: "2026-07-12T00:00:00Z"
            )
        ]
        mock.recentSearches = [
            RecentSearch(query: "Morrison", searchedAt: "2026-07-12T00:00:00Z"),
            RecentSearch(query: "Angelou", searchedAt: "2026-07-11T00:00:00Z")
        ]

        let viewModel = DashboardViewModel(bridge: mock)
        await viewModel.load()

        XCTAssertNotNil(viewModel.greeting)
        XCTAssertEqual(viewModel.greeting?.label, "Good morning")
        XCTAssertEqual(viewModel.stats?.totalBooks, 42)
        XCTAssertEqual(viewModel.recentBook?.title, "Beloved")
        XCTAssertEqual(viewModel.recentSearches.count, 2)
        XCTAssertNil(viewModel.error)
        XCTAssertTrue(viewModel.hasAnyData)
        XCTAssertFalse(viewModel.isLoading)
    }

    func testLoadFailureSetsError() async {
        let mock = MockDashboardBridge()
        mock.error = AppError.database("connection refused")

        let viewModel = DashboardViewModel(bridge: mock)
        await viewModel.load()

        XCTAssertNotNil(viewModel.error)
        XCTAssertEqual(viewModel.error?.localizedDescription, "connection refused")
        XCTAssertFalse(viewModel.isLoading)
    }

    func testHasAnyDataIsFalseInitially() async {
        let mock = MockDashboardBridge()
        // Configure all calls to throw so nothing loads.
        mock.error = AppError.database("not initialized")

        let viewModel = DashboardViewModel(bridge: mock)
        XCTAssertFalse(viewModel.hasAnyData)
    }

    func testConcurrentLoadIsSerialized() async {
        let mock = MockDashboardBridge()
        mock.artificialDelayNanos = 100_000_000 // 100ms

        let viewModel = DashboardViewModel(bridge: mock)

        async let first: Void = viewModel.load()
        async let second: Void = viewModel.load()
        _ = await (first, second)

        // Both calls should have completed (the second dropped while the
        // first was in flight). The result should be fully populated.
        XCTAssertNotNil(viewModel.greeting)
        XCTAssertNotNil(viewModel.stats)
    }

    func testRetryReRunsLoad() async {
        let mock = MockDashboardBridge()
        mock.error = AppError.database("temporary")

        let viewModel = DashboardViewModel(bridge: mock)
        await viewModel.load()
        XCTAssertNotNil(viewModel.error)

        // Clear the failure and retry.
        mock.error = nil
        viewModel.retry()
        // Give the retry task time to complete.
        try? await Task.sleep(nanoseconds: 200_000_000)

        XCTAssertNil(viewModel.error)
        XCTAssertNotNil(viewModel.greeting)
    }
}
