import Foundation
import LivtetKit

/// App-facing facade over the `LivtetStore` handle.
///
/// Every method translates `LivtetError` into `AppError` so the rest of the
/// app has one error vocabulary, and adapters below expose the same surface as
/// protocols for unit-testable view-models.
///
/// Surfaces that the revived `livtet-ffi` does not expose (create-book,
/// duplicate detection/merge, sync/pairing, manual covers, distinct work
/// statuses) have been removed rather than emulated; see
/// `docs/IOS_VERIFICATION.md` Step 2.

// MARK: - Protocols

/// Subset of `LivtetCoreBridge` methods needed by the Dashboard screen.
protocol DashboardBridge: AnyObject {
    func getGreeting() async throws -> Greeting
    func getDashboardStats() async throws -> DashboardStats
    func getRecentlyReadBooks(limit: Int32) async throws -> [RecentlyReadBook]
    func getRecentSearches(limit: Int32) async throws -> [RecentSearch]
}

/// Subset of `LivtetCoreBridge` methods needed by the Library tab.
protocol LibraryBridge: AnyObject {
    func listWorks(
        limit: Int32,
        offset: Int32,
        sortDirection: SortDirection,
        formatIds: [DbId],
        languageIds: [DbId]
    ) async throws -> [WorkSummary]
    func getDistinctFormats() async throws -> [FormatInfo]
    func getDistinctLanguages() async throws -> [LibraryLanguage]
    func getEditionsForWork(workId: DbId) async throws -> [EditionSummary]
    func getEdition(id: DbId) async throws -> EditionDetail?
    func getEmptyStateQuotation() -> EmptyMessage
}

// MARK: - Adapters

/// Exposes the static `LivtetCoreBridge` wrappers as instance methods that
/// satisfy `DashboardBridge`.
final class LivtetDashboardBridgeAdapter: DashboardBridge {
    func getGreeting() async throws -> Greeting {
        try await LivtetCoreBridge.getGreeting()
    }

    func getDashboardStats() async throws -> DashboardStats {
        try await LivtetCoreBridge.getDashboardStats()
    }

    func getRecentlyReadBooks(limit: Int32) async throws -> [RecentlyReadBook] {
        try await LivtetCoreBridge.getRecentlyReadBooks(limit: limit)
    }

    func getRecentSearches(limit: Int32) async throws -> [RecentSearch] {
        try await LivtetCoreBridge.getRecentSearches(limit: limit)
    }
}

/// Exposes the static `LivtetCoreBridge` wrappers as instance methods that
/// satisfy `LibraryBridge`.
final class LivtetLibraryBridgeAdapter: LibraryBridge {
    func listWorks(
        limit: Int32,
        offset: Int32,
        sortDirection: SortDirection,
        formatIds: [DbId],
        languageIds: [DbId]
    ) async throws -> [WorkSummary] {
        try await LivtetCoreBridge.listWorks(
            limit: limit,
            offset: offset,
            sortDirection: sortDirection,
            formatIds: formatIds,
            languageIds: languageIds
        )
    }

    func getDistinctFormats() async throws -> [FormatInfo] {
        try await LivtetCoreBridge.getDistinctFormats()
    }

    func getDistinctLanguages() async throws -> [LibraryLanguage] {
        try await LivtetCoreBridge.getDistinctLanguages()
    }

    func getEditionsForWork(workId: DbId) async throws -> [EditionSummary] {
        try await LivtetCoreBridge.listEditions(workId: workId)
    }

    func getEdition(id: DbId) async throws -> EditionDetail? {
        try await LivtetCoreBridge.getEdition(id: id)
    }

    func getEmptyStateQuotation() -> EmptyMessage {
        LivtetCoreBridge.getEmptyStateQuotation()
    }
}

// MARK: - Facade

enum LivtetCoreBridge {
    /// Open store handle. Internal so adapters can hold the same instance.
    static private(set) var store: LivtetStore?

    /// Open the store (creating and migrating if needed).
    static func initialize(databasePath: String, indexDir: String = "") async throws {
        store = try await LivtetStore.open(
            dbPath: databasePath,
            indexDir: indexDir.isEmpty ? databasePath + "/search-index" : indexDir
        )
    }

    /// Flush and close the store.
    static func shutdown() async throws {
        try await store?.shutdown()
        store = nil
    }

    /// True if the library has been initialized.
    static var isReady: Bool {
        store != nil
    }

    /// Populate the database with realistic demo data.
    static func seedDatabase(works: Int = 30) async throws -> SeedStats {
        try await withStore { try await $0.seedSampleData(numWorks: UInt32(works)) }
    }

    static func resetAndSeed(numWorks: Int = 30) async throws -> SeedStats {
        try await withStore { try await $0.resetAndSeed(numWorks: UInt32(numWorks)) }
    }

    // MARK: - Works & editions

    /// Works matching the format/language filters, newest or oldest first.
    static func listWorks(
        limit: Int32 = 100,
        offset: Int32 = 0,
        sortDirection: SortDirection = .desc,
        formatIds: [DbId] = [],
        languageIds: [DbId] = []
    ) async throws -> [WorkSummary] {
        let filters = WorkFilters(
            tagIds: [],
            genreIds: [],
            subjectIds: [],
            publisherIds: [],
            authorIds: [],
            formatIds: formatIds,
            languageIds: languageIds,
            hasFile: nil,
            sortBy: .createdAt,
            sortDirection: sortDirection,
            limit: UInt64(max(limit, 0))
        )
        return try await withStore {
            try await $0.listWorksFiltered(filters: filters, offset: UInt32(max(offset, 0)))
        }
    }

    /// Total number of works in the library.
    static func countWorks() async throws -> UInt64 {
        try await withStore { try await $0.countWorks() }
    }

    /// One edition with all of its related data, if it exists.
    static func getEdition(id: DbId) async throws -> EditionDetail? {
        try await withStore { try await $0.getEdition(id: id) }
    }

    /// Editions of one work, newest first.
    static func listEditions(workId: DbId) async throws -> [EditionSummary] {
        try await withStore { try await $0.listEditions(workId: workId) }
    }

    // MARK: - Filter dimensions

    /// Formats attached to at least one edition.
    static func getDistinctFormats() async throws -> [FormatInfo] {
        try await withStore { try await $0.listFormats() }
    }

    /// Languages used by at least one edition.
    static func getDistinctLanguages() async throws -> [LibraryLanguage] {
        try await withStore { try await $0.listLanguages() }
    }

    /// Literary quotation for the Library empty state.
    static func getEmptyStateQuotation() -> EmptyMessage {
        LivtetKit.getEmptyStateQuotation()
    }

    // MARK: - Dashboard

    /// Literary greeting for the current time of day.
    static func getGreeting() async throws -> Greeting {
        LivtetKit.getGreeting()
    }

    static func getDashboardStats() async throws -> DashboardStats {
        try await withStore { try await $0.getDashboardStats() }
    }

    static func getRecentlyReadBooks(limit: Int32) async throws -> [RecentlyReadBook] {
        try await withStore { try await $0.getRecentlyReadBooks(limit: UInt32(max(limit, 0))) }
    }

    static func getRecentSearches(limit: Int32 = 5) async throws -> [RecentSearch] {
        try await withStore { try await $0.getRecentSearches(limit: UInt32(max(limit, 0))) }
    }

    // MARK: - Reading progress

    /// The progress row for an edition, if any.
    static func getReadingProgress(editionId: DbId) async throws -> ReadingProgress? {
        try await withStore { try await $0.getReadingProgress(editionId: editionId) }
    }

    /// Upsert reading progress for an edition.
    ///
    /// `progressUnit` determines how `progress` is interpreted:
    /// `.percentage` (0–1 fraction), `.page` (1-based physical page),
    /// `.virtualPage`, `.timestamp` (seconds), `.chapter`, `.cfi`.
    /// `totalReadingTimeSecs` is *added* to the accumulated total.
    static func recordReadingProgress(
        editionId: DbId,
        formatId: DbId,
        progress: Double,
        progressUnit: ProgressUnit,
        lastLocation: String?,
        additionalSeconds: Int64
    ) async throws {
        _ = try await withStore {
            try await $0.recordReadingProgress(
                editionId: editionId,
                formatId: formatId,
                progress: progress,
                unit: progressUnit,
                lastLocation: lastLocation,
                additionalSeconds: additionalSeconds
            )
        }
    }

    // MARK: - Helpers

    /// Runs `body` against the open store, translating `LivtetError` into
    /// `AppError` and failing clearly when the store is not open.
    private static func withStore<T>(_ body: (LivtetStore) async throws -> T) async throws -> T {
        guard let store else {
            throw AppError.notFound("Library store is not open")
        }
        do {
            return try await body(store)
        } catch let error as LivtetError {
            throw AppError.from(ffi: error)
        }
    }
}
