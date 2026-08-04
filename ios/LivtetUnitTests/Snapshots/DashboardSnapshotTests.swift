import FastULID
@testable import Livtet
import LivtetKit
import LivtetKitFFI
import SnapshotTesting
import SwiftUI
import XCTest

/// Snapshot tests for the Dashboard screen.
///
/// We render [DashboardView] against four fixture states (loaded,
/// empty, error, loading) and assert that the pixel output matches the
/// golden images in `__Snapshots__/`. These are the tests that catch
/// accidental design regressions in the future — every change to a
/// dashboard component should update the relevant snapshot.
///
/// Snapshots are recorded with `record = true` (passed via the
/// `SNAPSHOT_RECORD` env var, defaulting to `false`).
@MainActor
final class DashboardSnapshotTests: XCTestCase {
    /// Bundle-relative directory for golden images.
    private static let snapshotsDirectory: String = "__Snapshots__"

    private var recordMode: Bool {
        ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "true"
    }

    // MARK: - Greeting card

    func testGreetingCardLight() {
        let view = GreetingCard(greeting: Self.fixtureGreeting)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testGreetingCardLight",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    func testGreetingCardDark() {
        let view = GreetingCard(greeting: Self.fixtureGreeting)
            .padding()
            .background(Color("surfaceDefault"))
            .preferredColorScheme(.dark)

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testGreetingCardDark",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    // MARK: - Stats row

    func testStatsRowLight() {
        let view = StatsRow(stats: Self.fixtureStats)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testStatsRowLight",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    // MARK: - Quick action card

    func testQuickActionCardWithProgress() {
        let view = QuickActionCard(
            icon: Image.lucideBookPlus,
            title: "Add Your First Book",
            description: "Build your library to get started",
            progress: 0.3,
            progressLabel: "3/10 books",
            tintColor: Color("successOnNormal"),
            onTap: {}
        )
        .padding()
        .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testQuickActionCardWithProgress",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    func testQuickActionCardNoProgress() {
        let view = QuickActionCard(
            icon: Image.lucideCircleCheck,
            title: "Finish a Book",
            description: "Complete a book to make progress",
            progress: nil,
            progressLabel: nil,
            tintColor: Color("neutralOnNormal"),
            onTap: {}
        )
        .padding()
        .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testQuickActionCardNoProgress",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    // MARK: - Continue reading card

    func testContinueReadingCardLight() {
        let view = ContinueReadingCard(book: Self.fixtureBook)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testContinueReadingCardLight",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    // MARK: - Recent searches row

    func testRecentSearchesRow() {
        let view = RecentSearchesRow(searches: Self.fixtureSearches)
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testRecentSearchesRow",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    // MARK: - Feed placeholder

    func testFeedPlaceholderCard() {
        let view = FeedPlaceholderCard()
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testFeedPlaceholderCard",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    // MARK: - Error banner

    func testErrorBanner() {
        let view = ErrorBanner(message: "Database connection refused on /var/lib/livtet.db") {}
            .padding()
            .background(Color("surfaceDefault"))

        assertSnapshot(
            matching: view,
            as: .image(layout: .device(config: .iPhone13)),
            named: "testErrorBanner",
            record: recordMode,
            testName: Self.snapshotsDirectory
        )
    }

    // MARK: - Fixtures

    private static let fixtureGreeting = Greeting(
        label: "Good morning",
        text: "The morning sun spilled across the open book.",
        author: "Maya Angelou",
        material: "I Know Why the Caged Bird Sings",
        period: "Early Morning"
    )

    private static let fixtureStats = DashboardStats(
        totalBooks: 42,
        booksInProgress: 3,
        finishedBooks: 12,
        totalReadingTimeSecs: 9 * 3600 + 12 * 60,
        firstReadingAtMillis: nil
    )

    private static let fixtureBook = RecentlyReadBook(
        workId: ULID(ulidData: Data(repeating: 0, count: 16))!,
        editionId: ULID(ulidData: Data(repeating: 0, count: 16))!,
        title: "Beloved",
        authorName: "Toni Morrison",
        progress: 0.42,
        totalReadingTimeSecs: 3600,
        lastReadAt: "2026-07-12T00:00:00Z"
    )

    private static let fixtureSearches: [RecentSearch] = [
        RecentSearch(query: "Morrison", searchedAt: "2026-07-12T00:00:00Z"),
        RecentSearch(query: "Octavia Butler", searchedAt: "2026-07-11T00:00:00Z"),
        RecentSearch(query: "Ralph Ellison", searchedAt: "2026-07-10T00:00:00Z")
    ]
}
