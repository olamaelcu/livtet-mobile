import Foundation
import LivtetKit
import LivtetKitFFI

/// App-facing facade over the generated UniFFI bindings for `livtet-ffi`.
///
/// The generated `LivtetKit` SPM module exposes top-level functions and
/// types with names that match the Rust source verbatim. The uniffi-generated
/// function for database initialization is named `init` (a reserved word in
/// Swift), so we route through a generated `LivtetKit` enum wrapper that
/// handles the keyword escape for us. `LivtetCoreBridge` adds app-friendly
/// error translation (`MobileError` → `AppError`) and gives the rest of the
/// app a single, stable surface to import.
///
/// `DashboardBridge` is the subset of methods needed by the Dashboard screen.
/// Extracting it as a protocol keeps view-models unit-testable with a mock
/// bridge without having to spin up the FFI.
protocol DashboardBridge: AnyObject {
    func getGreeting() throws -> Greeting
    func getDashboardStats() throws -> DashboardStats
    func getRecentlyReadBooks(limit: Int32) throws -> [RecentlyReadBook]
    func getRecentSearches(limit: Int32) throws -> [RecentSearch]
}

/// Subset of sync/device methods the Settings screen needs.
/// Extracted as a protocol for the same reason as `DashboardBridge`:
/// unit-testable with a mock that doesn't touch the FFI.
protocol SyncBridge: AnyObject {
    func getPairedDevices() throws -> [PairedDeviceMobile]
    func pairDevice(name: String, address: String, port: Int32, deviceType: String) throws -> PairedDeviceMobile
    func unpairDevice(deviceId: DbId) throws
    func getNetworkAddresses() throws -> NetworkAddressesMobile
}

/// Subset of plugin-management methods the Settings screen needs.
protocol PluginBridge: AnyObject {
    func listInstalledPlugins() throws -> [InstalledPluginMobile]
    func setPluginEnabled(pluginId: String, enabled: Bool) throws
    func pluginGetSetting(pluginId: String, key: String) throws -> String?
    func pluginSaveSetting(pluginId: String, key: String, value: String) throws
}

/// Subset of reading-progress methods needed by the Edition detail screen.
protocol ReadingBridge: AnyObject {
    func getReadingProgress(editionId: DbId, formatId: DbId) throws -> ReadingProgress?
    func upsertReadingProgress(
        editionId: DbId,
        formatId: DbId,
        progress: Double,
        progressUnit: ProgressUnit,
        lastLocation: String?,
        totalReadingTimeSecs: Int64
    ) throws
}

/// Adapter that exposes the static [LivtetCoreBridge] FFI wrappers as
/// instance methods that satisfy [DashboardBridge]. The dashboard view-model
/// holds a strong reference to one of these so unit tests can swap in a mock
/// conforming to [DashboardBridge].
final class LivtetDashboardBridgeAdapter: DashboardBridge {
    func getGreeting() throws -> Greeting {
        // Forwarding the (non-throwing) underlying call into the `throws`
        // protocol surface. We still have to spell `try` here because
        // `LivtetCoreBridge.getGreeting()` itself is declared `throws` so that
        // it matches the bridge protocol; the underlying FFI never throws.
        return try LivtetCoreBridge.getGreeting()
    }

    func getDashboardStats() throws -> DashboardStats {
        try LivtetCoreBridge.getDashboardStats()
    }

    func getRecentlyReadBooks(limit: Int32) throws -> [RecentlyReadBook] {
        try LivtetCoreBridge.getRecentlyReadBooks(limit: limit)
    }

    func getRecentSearches(limit: Int32) throws -> [RecentSearch] {
        try LivtetCoreBridge.getRecentSearches(limit: limit)
    }
}

/// Adapter that exposes the static [LivtetCoreBridge] FFI wrappers as
/// instance methods that satisfy [ReadingBridge].
final class LivtetReadingBridgeAdapter: ReadingBridge {
    func getReadingProgress(editionId: DbId, formatId: DbId) throws -> ReadingProgress? {
        try LivtetCoreBridge.getReadingProgress(editionId: editionId, formatId: formatId)
    }

    func upsertReadingProgress(
        editionId: DbId,
        formatId: DbId,
        progress: Double,
        progressUnit: ProgressUnit,
        lastLocation: String?,
        totalReadingTimeSecs: Int64
    ) throws {
        try LivtetCoreBridge.upsertReadingProgress(
            editionId: editionId,
            formatId: formatId,
            progress: progress,
            progressUnit: progressUnit,
            lastLocation: lastLocation,
            totalReadingTimeSecs: totalReadingTimeSecs
        )
    }
}

/// App-facing facade over the generated UniFFI bindings for `livtet-ffi`.
enum LivtetCoreBridge {
    /// Initialize the FFI with a database path. Calls the generated
    /// `LivtetKit.init(...)` wrapper.
    static func initialize(databasePath: String) throws {
        do {
            try livtetFfiInit(databasePath: databasePath)
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        }
    }

    /// True if the library has been initialized.
    static var isReady: Bool {
        livtetFfiIsInitialized()
    }

    /// True if the sync pool has been initialized.
    static var isSyncPoolReady: Bool {
        livtetFfiIsSyncPoolInitialized()
    }

    /// Populate the database with realistic demo data.
    /// Only available in debug builds.
    static func seedDatabase(works: Int = 30) async throws -> SeedResultMobile {
        return try await livtetFfiSeedDatabase(works: Int32(works))
    }

    // MARK: - Books

    /// List books from the local database, paginated.
    static func listBooks(limit: Int = 50, offset: Int = 0, order: BookSearchSortOrder = .ascending) throws -> [Book] {
        do {
            return try livtetFfiListBooks(limit: Int32(limit), offset: Int32(offset), order: order)
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        }
    }

    /// Create a new book in the local database.
    static func createBook(title: String, description: String?) throws -> Book {
        do {
            return try livtetFfiCreateBook(title: title, description: description)
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        }
    }

    /// Look up a book by its database id (ULID hex string).
    static func getBook(id: String) throws -> Book? {
        do {
            return try livtetFfiGetBook(id: id)
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        }
    }

    /// Create a new edition for a given work. The `title`,
    /// `isbn`, `publishedDate`, and `languageId` arguments are
    /// optional; pass `nil` to leave the column unset on the
    /// edition row.
    static func createEdition(
        workId: DbId,
        title: String?,
        isbn: String?,
        publishedDate: String?,
        languageId: DbId?
    ) throws -> Edition {
        do {
            return try livtetFfiCreateEdition(
                workId: workId,
                title: title,
                isbn: isbn,
                publishedDate: publishedDate,
                languageId: languageId
            )
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        }
    }

    /// Find or create an author by display name. Used by the
    /// OPDS acquisition flow when attaching a publication's
    /// author to a freshly-created Work.
    static func findOrCreateAuthor(name: String) throws -> AuthorInfo {
        do {
            return try livtetFfiFindOrCreateAuthor(name: name)
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        }
    }

    /// Link a work to an author with a role (e.g. `"author"`,
    /// `"illustrator"`, `"translator"`). Used after
    /// `findOrCreateAuthor` in the OPDS acquisition flow.
    static func linkWorkAuthor(
        workId: DbId,
        authorId: DbId,
        role: String
    ) throws {
        do {
            try livtetFfiLinkWorkAuthor(workId: workId, authorId: authorId, role: role)
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        }
    }

    // MARK: - Dashboard

    /// Fetch the literary greeting drawn from African American and African
    /// diaspora authors chosen for the current time of day. The underlying
    /// FFI call is non-throwing; this method is `throws` purely to satisfy
    /// the [DashboardBridge] protocol's uniform surface.
    static func getGreeting() throws -> Greeting {
        // No try/catch needed: the FFI call doesn't throw.
        return livtetFfiGetGreeting()
    }

    /// Aggregate dashboard statistics about the user's library and
    /// reading activity. Counts and totals are computed from the
    /// `works` and `reading_progress` tables.
    static func getDashboardStats() throws -> DashboardStats {
        do {
            return try livtetFfiGetDashboardStats()
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        }
    }

    /// Return the most recently-read books, ordered by when reading
    /// progress was last updated (most recent first), up to `limit`.
    static func getRecentlyReadBooks(limit: Int32) throws -> [RecentlyReadBook] {
        do {
            return try livtetFfiGetRecentlyReadBooks(limit: limit)
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        }
    }

    /// Return the most recent search queries, ordered by when they were
    /// searched (most recent first), up to `limit`.
    static func getRecentSearches(limit: Int32 = 5) throws -> [RecentSearch] {
        do {
            return try livtetFfiGetRecentSearches(limit: limit)
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        }
    }

    // MARK: - Library (filter-aware list)

    /// List books with format / language filter predicates. Empty
    /// `BookListFilters` defaults mean "no filter on any dimension".
    static func listBooksWithFilters(
        limit: Int = 50,
        offset: Int = 0,
        order: BookSearchSortOrder = .descending,
        filters: BookListFilters = BookListFilters(formatIds: [], languageIds: [])
    ) throws -> [Book] {
        do {
            return try livtetFfiListBooksWithFilters(
                limit: Int32(limit),
                offset: Int32(offset),
                order: order,
                formatIds: filters.formatIds,
                languageIds: filters.languageIds
            )
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        } catch {
            throw AppError.sync("Filtered book list unavailable: \(error)")
        }
    }

    // MARK: - Library filter dimensions

    /// Distinct formats actually present in the user's library.
    static func getDistinctFormats() throws -> [FormatInfo] {
        do {
            return try livtetFfiGetDistinctFormats()
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        }
    }

    /// Distinct languages actually present in the user's library editions.
    static func getDistinctLanguages() throws -> [LanguageInfo] {
        do {
            return try livtetFfiGetDistinctLanguages()
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        }
    }

    /// Distinct work-status values actually present in the user's library.
    /// The iOS Library UI displays these as chips in the filter sheet,
    /// but status chips are inert in this change (status predicate is
    /// deferred to a follow-on).
    static func getDistinctWorkStatuses() throws -> [WorkStatusInfo] {
        do {
            return try livtetFfiGetDistinctWorkStatuses()
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        }
    }

    /// Return an empty-state filler — a literary quotation without a
    /// greeting label or time-of-day period. Use this when a list or
    /// view would otherwise be empty. The quote is picked
    /// deterministically per call.
    static func getEmptyStateQuotation() throws -> EmptyMessage {
        do {
            return try livtetFfiGetEmptyStateQuotation()
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        } catch {
            throw AppError.sync("Empty-state quote unavailable: \(error)")
        }
    }

    /// Get all editions for a work with cover information (cover_path
    /// and cover_source) and ISBN identifiers populated.
    static func getEditionsWithCoversForWork(workId: DbId) throws -> [Edition] {
        do {
            return try livtetFfiGetEditionsWithCoversForWork(workId: workId)
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        }
    }

    /// Set a manual cover image for a physical edition (no
    /// digital_inventory row). Writes to the edition_specific_covers
    /// table.
    static func setEditionManualCover(editionId: DbId, localPath: String) throws {
        do {
            try livtetFfiSetEditionManualCover(editionId: editionId, localPath: localPath)
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        }
    }

    // MARK: - Duplicate detection & merge

    /// Find candidate duplicate *works* in the user's library. The
    /// returned list is ranked by confidence (high → low). Each entry
    /// pairs a primary work with a duplicate work that matched one or
    /// more of the supplied `matchKinds` at or above `minConfidence`.
    static func findDuplicateWorks(
        matchKinds: [DuplicateMatchKindMobile],
        minConfidence: Float
    ) async throws -> [DuplicateCandidateMobile] {
        try await FFIErrorBridge.wrap(
            { try await livtetFfiFindDuplicateWorks(matchKinds: matchKinds, minConfidence: minConfidence) },
            context: "findDuplicateWorks"
        )
    }

    /// Find duplicate editions within a single work. Only matches
    /// between editions that share the same `work_id` are returned.
    static func findDuplicateEditionsInWork(
        workId: DbId,
        matchKinds: [DuplicateMatchKindMobile],
        minConfidence: Float
    ) async throws -> [EditionDuplicateCandidateMobile] {
        try await FFIErrorBridge.wrap(
            {
                try await livtetFfiFindDuplicateEditionsInWork(
                    workId: workId,
                    matchKinds: matchKinds,
                    minConfidence: minConfidence
                )
            },
            context: "findDuplicateEditionsInWork"
        )
    }

    /// Find duplicate editions that live under *different* works (the
    /// "two records of the same book" case where the editions are
    /// under separate work rows). Matched on shared ISBN.
    static func findCrossWorkEditionDuplicates(
        matchKinds: [DuplicateMatchKindMobile],
        minConfidence: Float
    ) async throws -> [CrossWorkEditionDuplicateMobile] {
        try await FFIErrorBridge.wrap(
            {
                try await livtetFfiFindCrossWorkEditionDuplicates(
                    matchKinds: matchKinds,
                    minConfidence: minConfidence
                )
            },
            context: "findCrossWorkEditionDuplicates"
        )
    }

    /// Merge a duplicate work into a primary work. Transactional:
    /// every edition, identifier, author, tag, genre, subject, and
    /// publisher from the duplicate moves to the primary; the
    /// duplicate work row is then deleted. `conflictResolution`
    /// controls which side wins for any field where the two works
    /// have non-null values.
    static func mergeWorks(
        primaryWorkId: DbId,
        duplicateWorkId: DbId,
        conflictResolution: WorkMergeConflictResolutionMobile
    ) async throws -> MergeResultMobile {
        try await FFIErrorBridge.wrap(
            {
                try await livtetFfiMergeWorks(
                    primaryWorkId: primaryWorkId,
                    duplicateWorkId: duplicateWorkId,
                    conflictResolution: conflictResolution
                )
            },
            context: "mergeWorks"
        )
    }

    /// Merge a duplicate edition into a primary edition.
    /// Transactional: digital_inventory rows, reading_progress rows,
    /// and identifiers from the duplicate move to the primary; the
    /// duplicate edition row is then deleted. `conflictResolution`
    /// controls which side wins for any field where the two editions
    /// have non-null values.
    static func mergeEditions(
        primaryEditionId: DbId,
        duplicateEditionId: DbId,
        conflictResolution: EditionMergeConflictResolutionMobile
    ) async throws -> MergeResultMobile {
        try await FFIErrorBridge.wrap(
            {
                try await livtetFfiMergeEditions(
                    primaryEditionId: primaryEditionId,
                    duplicateEditionId: duplicateEditionId,
                    conflictResolution: conflictResolution
                )
            },
            context: "mergeEditions"
        )
    }

    /// Move a single edition from one work to another. Used as the
    /// recovery action for cross-work edition duplicates: the user
    /// confirms the editions are the same book, and the duplicate
    /// edition's `work_id` is reassigned to the primary.
    static func moveEditionToWork(
        editionId: DbId,
        targetWorkId: DbId
    ) async throws {
        try await FFIErrorBridge.wrap(
            { try await livtetFfiMoveEditionToWork(editionId: editionId, targetWorkId: targetWorkId) },
            context: "moveEditionToWork"
        )
    }

    // MARK: - Reading progress

    /// Fetch reading progress for a specific edition and format.
    /// Returns `nil` if no progress has been recorded yet.
    static func getReadingProgress(
        editionId: DbId,
        formatId: DbId
    ) throws -> ReadingProgress? {
        do {
            return try livtetFfiGetReadingProgress(editionId: editionId, formatId: formatId)
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        }
    }

    /// Upsert (insert or update) reading progress for an edition/format pair.
    ///
    /// The `progressUnit` determines how the `progress` value is interpreted:
    /// - `.ratio`: a 0.0-1.0 completion fraction (for ebooks)
    /// - `.page`: a 1-based physical page number (for physical books)
    /// - `.seconds`: a seek position in seconds (for audiobooks)
    ///
    /// Pass `nil` for `lastLocation` if no Readium locator is available.
    static func upsertReadingProgress(
        editionId: DbId,
        formatId: DbId,
        progress: Double,
        progressUnit: ProgressUnit,
        lastLocation: String?,
        totalReadingTimeSecs: Int64
    ) throws {
        do {
            try livtetFfiUpsertReadingProgress(
                editionId: editionId,
                formatId: formatId,
                progress: progress,
                progressUnit: progressUnit,
                lastLocation: lastLocation,
                totalReadingTimeSecs: totalReadingTimeSecs
            )
        } catch let error as MobileError {
            throw AppError.from(ffi: error)
        }
    }

    // MARK: - Pairing / paired devices (mobile)

    /// All devices currently paired with this instance, ordered by
    /// most-recently-paired. `device_type` is the canonical display
    /// name resolved by `DeviceType::display_name_for` (e.g.
    /// `"E-Reader"` for any `Ereader` variant — canonical or
    /// custom-named).
    static func getPairedDevices() throws -> [PairedDeviceMobile] {
        try FFIErrorBridge.wrap(
            { try livtetFfiGetPairedDevices() },
            context: "getPairedDevices"
        )
    }

    /// Insert a `paired_devices` row directly. Used by manual pairing
    /// flows (Settings → "Pair New Device" sheet). The `device_id` is
    /// generated server-side; the function returns the new row.
    static func pairDevice(
        name: String,
        address: String,
        port: Int32,
        deviceType: String
    ) throws -> PairedDeviceMobile {
        try FFIErrorBridge.wrap(
            { try livtetFfiPairDevice(
                name: name,
                address: address,
                port: UInt16(port),
                deviceType: deviceType
            ) },
            context: "pairDevice"
        )
    }

    /// Remove a paired device by its id. No-op if the row is gone.
    static func unpairDevice(deviceId: DbId) throws {
        try FFIErrorBridge.wrap(
            { try livtetFfiUnpairDevice(deviceId: deviceId) },
            context: "unpairDevice"
        )
    }

    // MARK: - Installed plugins (mobile)

    /// All installed plugin rows. Used by the Settings → Plugins
    /// section to render the per-plugin toggle list.
    static func listInstalledPlugins() throws -> [InstalledPluginMobile] {
        try FFIErrorBridge.wrap(
            { try livtetFfiListInstalledPlugins() },
            context: "listInstalledPlugins"
        )
    }

    /// Enable or disable a plugin. `enabled = false` disables the
    /// plugin without uninstalling it.
    static func setPluginEnabled(pluginId: String, enabled: Bool) throws {
        try FFIErrorBridge.wrap(
            { try livtetFfiSetPluginEnabled(pluginId: pluginId, enabled: enabled) },
            context: "setPluginEnabled"
        )
    }

    // MARK: - Plugin settings (mobile)

    /// Upsert a single per-plugin setting. Mirrors the desktop
    /// `pluginSaveSetting` Tauri command.
    static func pluginSaveSetting(
        pluginId: String,
        key: String,
        value: String
    ) throws {
        try FFIErrorBridge.wrap(
            { try livtetFfiPluginSaveSetting(
                pluginId: pluginId,
                key: key,
                value: value
            ) },
            context: "pluginSaveSetting"
        )
    }

    /// Read a per-plugin setting. Returns `nil` if the key is absent.
    static func pluginGetSetting(
        pluginId: String,
        key: String
    ) throws -> String? {
        try FFIErrorBridge.wrap(
            { try livtetFfiPluginGetSetting(pluginId: pluginId, key: key) },
            context: "pluginGetSetting"
        )
    }

    // MARK: - Network addresses (mobile)

    /// Non-loopback IPv4/IPv6 addresses of the device. The Settings
    /// screen uses this to render the local interfaces a user can
    /// pick from when configuring manual pairing by IP. May be
    /// empty on CI or in a sandboxed environment.
    static func getNetworkAddresses() throws -> NetworkAddressesMobile {
        try FFIErrorBridge.wrap(
            { try livtetFfiGetNetworkAddresses() },
            context: "getNetworkAddresses"
        )
    }

    // MARK: - Reader module

    /// Resolve the preferred readable edition for a work. Returns `nil` if
    /// the work has no edition with a cached file in
    /// `digital_inventory`. The returned `Edition` has `inventory_id`
    /// and `cover_path` populated from the matching
    /// `digital_inventory` row.
    static func getReadableEditionForWork(workId: DbId) throws -> Edition? {
        try FFIErrorBridge.wrap(
            { try livtetFfiGetReadableEditionForWork(workId: workId) },
            context: "getReadableEditionForWork"
        )
    }

    /// Upsert reading progress for a given edition and format.
    /// Get the cached file path for an inventory item. Returns `nil`
    /// when no local file has been registered for the given
    /// `inventoryId`.
    static func getCachedFilePath(inventoryId: DbId) throws -> String? {
        try FFIErrorBridge.wrap(
            { try livtetFfiGetCachedFilePath(inventoryId: inventoryId) },
            context: "getCachedFilePath"
        )
    }

    /// Register a local file for an inventory item. The Rust side
    /// computes the Blake3 hash and stores the row in
    /// `digital_inventory`; the Swift side does not re-implement
    /// hashing. Returns the resulting `CachedFile` row.
    static func registerLocalFile(inventoryId: DbId, localPath: String) throws -> CachedFile {
        try FFIErrorBridge.wrap(
            { try livtetFfiRegisterLocalFile(inventoryId: inventoryId, localPath: localPath) },
            context: "registerLocalFile"
        )
    }
}

// MARK: - Library

/// Subset of `LivtetCoreBridge` methods needed by the Library tab.
/// Extracted as a protocol so the view-model can be unit-tested with a
/// mock implementation without spinning up the FFI.
protocol LibraryBridge: AnyObject {
    func listBooksWithFilters(
        limit: Int32,
        offset: Int32,
        order: BookSearchSortOrder,
        filters: BookListFilters
    ) throws -> [Book]
    func getDistinctFormats() throws -> [FormatInfo]
    func getDistinctLanguages() throws -> [LanguageInfo]
    func getDistinctWorkStatuses() throws -> [WorkStatusInfo]
    /// Return a literary quotation for the empty-state surface
    /// (the Library tab when no books are present). Picked
    /// deterministically per call from `data/quotes/empty.txt`.
    func getEmptyStateQuotation() throws -> EmptyMessage
    func getEditionsWithCoversForWork(workId: DbId) throws -> [Edition]
    func setEditionManualCover(editionId: DbId, localPath: String) throws
}

/// Adapter that exposes the static [LivtetCoreBridge] FFI wrappers as
/// instance methods satisfying [LibraryBridge].
final class LivtetLibraryBridgeAdapter: LibraryBridge {
    func listBooksWithFilters(
        limit: Int32,
        offset: Int32,
        order: BookSearchSortOrder,
        filters: BookListFilters
    ) throws -> [Book] {
        try LivtetCoreBridge.listBooksWithFilters(
            limit: Int(limit),
            offset: Int(offset),
            order: order,
            filters: filters
        )
    }

    func getDistinctFormats() throws -> [FormatInfo] {
        try LivtetCoreBridge.getDistinctFormats()
    }

    func getDistinctLanguages() throws -> [LanguageInfo] {
        try LivtetCoreBridge.getDistinctLanguages()
    }

    func getDistinctWorkStatuses() throws -> [WorkStatusInfo] {
        try LivtetCoreBridge.getDistinctWorkStatuses()
    }

    func getEmptyStateQuotation() throws -> EmptyMessage {
        try LivtetCoreBridge.getEmptyStateQuotation()
    }

    func getEditionsWithCoversForWork(workId: DbId) throws -> [Edition] {
        try LivtetCoreBridge.getEditionsWithCoversForWork(workId: workId)
    }

    func setEditionManualCover(editionId: DbId, localPath: String) throws {
        try LivtetCoreBridge.setEditionManualCover(editionId: editionId, localPath: localPath)
    }
}

// MARK: - Duplicate detection & merge

/// Subset of `LivtetCoreBridge` methods needed by the Duplicate
/// Management screen. Extracted as a protocol so the view-model can
/// be unit-tested with a mock implementation without spinning up the
/// FFI.
protocol DuplicateBridge: AnyObject {
    func findDuplicateWorks(
        matchKinds: [DuplicateMatchKindMobile],
        minConfidence: Float
    ) async throws -> [DuplicateCandidateMobile]

    func findDuplicateEditionsInWork(
        workId: DbId,
        matchKinds: [DuplicateMatchKindMobile],
        minConfidence: Float
    ) async throws -> [EditionDuplicateCandidateMobile]

    func findCrossWorkEditionDuplicates(
        matchKinds: [DuplicateMatchKindMobile],
        minConfidence: Float
    ) async throws -> [CrossWorkEditionDuplicateMobile]

    func mergeWorks(
        primaryWorkId: DbId,
        duplicateWorkId: DbId,
        conflictResolution: WorkMergeConflictResolutionMobile
    ) async throws -> MergeResultMobile

    func mergeEditions(
        primaryEditionId: DbId,
        duplicateEditionId: DbId,
        conflictResolution: EditionMergeConflictResolutionMobile
    ) async throws -> MergeResultMobile

    func moveEditionToWork(
        editionId: DbId,
        targetWorkId: DbId
    ) async throws
}

/// Adapter that exposes the static [LivtetCoreBridge] FFI wrappers as
/// instance methods satisfying [DuplicateBridge].
final class LivtetDuplicateBridgeAdapter: DuplicateBridge {
    func findDuplicateWorks(
        matchKinds: [DuplicateMatchKindMobile],
        minConfidence: Float
    ) async throws -> [DuplicateCandidateMobile] {
        try await LivtetCoreBridge.findDuplicateWorks(
            matchKinds: matchKinds,
            minConfidence: minConfidence
        )
    }

    func findDuplicateEditionsInWork(
        workId: DbId,
        matchKinds: [DuplicateMatchKindMobile],
        minConfidence: Float
    ) async throws -> [EditionDuplicateCandidateMobile] {
        try await LivtetCoreBridge.findDuplicateEditionsInWork(
            workId: workId,
            matchKinds: matchKinds,
            minConfidence: minConfidence
        )
    }

    func findCrossWorkEditionDuplicates(
        matchKinds: [DuplicateMatchKindMobile],
        minConfidence: Float
    ) async throws -> [CrossWorkEditionDuplicateMobile] {
        try await LivtetCoreBridge.findCrossWorkEditionDuplicates(
            matchKinds: matchKinds,
            minConfidence: minConfidence
        )
    }

    func mergeWorks(
        primaryWorkId: DbId,
        duplicateWorkId: DbId,
        conflictResolution: WorkMergeConflictResolutionMobile
    ) async throws -> MergeResultMobile {
        try await LivtetCoreBridge.mergeWorks(
            primaryWorkId: primaryWorkId,
            duplicateWorkId: duplicateWorkId,
            conflictResolution: conflictResolution
        )
    }

    func mergeEditions(
        primaryEditionId: DbId,
        duplicateEditionId: DbId,
        conflictResolution: EditionMergeConflictResolutionMobile
    ) async throws -> MergeResultMobile {
        try await LivtetCoreBridge.mergeEditions(
            primaryEditionId: primaryEditionId,
            duplicateEditionId: duplicateEditionId,
            conflictResolution: conflictResolution
        )
    }

    func moveEditionToWork(
        editionId: DbId,
        targetWorkId: DbId
    ) async throws {
        try await LivtetCoreBridge.moveEditionToWork(
            editionId: editionId,
            targetWorkId: targetWorkId
        )
    }
}

// MARK: - Sync bridge

/// Adapter for [SyncBridge]. Forwards to the static
/// `LivtetCoreBridge` FFI wrappers. Holds no state of its own; the
/// Settings view-model keeps a strong reference to one of these for
/// unit-testability with a mock `SyncBridge` conformance.
final class LivtetSyncBridgeAdapter: SyncBridge {
    func getPairedDevices() throws -> [PairedDeviceMobile] {
        try LivtetCoreBridge.getPairedDevices()
    }

    func pairDevice(name: String, address: String, port: Int32, deviceType: String) throws -> PairedDeviceMobile {
        try LivtetCoreBridge.pairDevice(
            name: name, address: address, port: port, deviceType: deviceType
        )
    }

    func unpairDevice(deviceId: DbId) throws {
        try LivtetCoreBridge.unpairDevice(deviceId: deviceId)
    }

    func getNetworkAddresses() throws -> NetworkAddressesMobile {
        try LivtetCoreBridge.getNetworkAddresses()
    }
}

// MARK: - Plugin bridge

/// Adapter for [PluginBridge]. Same pattern as
/// `LivtetSyncBridgeAdapter` — a stateless forwarder.
final class LivtetPluginBridgeAdapter: PluginBridge {
    func listInstalledPlugins() throws -> [InstalledPluginMobile] {
        try LivtetCoreBridge.listInstalledPlugins()
    }

    func setPluginEnabled(pluginId: String, enabled: Bool) throws {
        try LivtetCoreBridge.setPluginEnabled(pluginId: pluginId, enabled: enabled)
    }

    func pluginGetSetting(pluginId: String, key: String) throws -> String? {
        try LivtetCoreBridge.pluginGetSetting(pluginId: pluginId, key: key)
    }

    func pluginSaveSetting(pluginId: String, key: String, value: String) throws {
        try LivtetCoreBridge.pluginSaveSetting(
            pluginId: pluginId, key: key, value: value
        )
    }
}
