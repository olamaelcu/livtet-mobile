import FastULID
import Foundation
import LivtetKitFFI

public func livtetFfiInit(databasePath: String) throws {
    try `init`(databasePath: databasePath)
}

public func livtetFfiIsInitialized() -> Bool {
    isInitialized()
}

public func livtetFfiIsSyncPoolInitialized() -> Bool {
    isSyncPoolInitialized()
}

public func livtetFfiListBooks(limit: Int32, offset: Int32, order: BookSearchSortOrder) throws -> [Book] {
    try listBooks(limit: limit, offset: offset, order: order)
}

public func livtetFfiCreateBook(title: String, description: String?) throws -> Book {
    try createBook(title: title, description: description)
}

public func livtetFfiGetBook(id: String) throws -> Book? {
    guard let ulid = ULID.fromHexString(id) else { return nil }
    return try getBook(id: ulid)
}

public func livtetFfiCreateEdition(
    workId: ULID,
    title: String?,
    isbn: String?,
    publishedDate: String?,
    languageId: ULID?
) throws -> Edition {
    try createEdition(
        workId: workId,
        title: title,
        isbn: isbn,
        publishedDate: publishedDate,
        languageId: languageId
    )
}

public func livtetFfiFindOrCreateAuthor(name: String) throws -> AuthorInfo {
    try findOrCreateAuthor(name: name)
}

public func livtetFfiLinkWorkAuthor(
    workId: ULID,
    authorId: ULID,
    role: String
) throws {
    try linkWorkAuthor(workId: workId, authorId: authorId, role: role)
}

public func livtetFfiGetRecentSearches(limit: Int32) throws -> [RecentSearch] {
    try getRecentSearches(limit: limit)
}

public func livtetFfiGetGreeting() -> Greeting {
    getGreeting()
}

public func livtetFfiSetSystemSecrets(_ secrets: [String: String]) {
    setSystemSecrets(secrets: secrets)
}

public func livtetFfiGetEmptyStateQuotation() -> EmptyMessage {
    getEmptyStateQuotation()
}

public func livtetFfiGetDashboardStats() throws -> DashboardStats {
    try getDashboardStats()
}

public func livtetFfiGetRecentlyReadBooks(limit: Int32) throws -> [RecentlyReadBook] {
    try getRecentlyReadBooks(limit: limit)
}

public func livtetFfiGetDistinctFormats() throws -> [FormatInfo] {
    try getDistinctFormats()
}

public func livtetFfiGetDistinctLanguages() throws -> [LanguageInfo] {
    try getDistinctLanguages()
}

public func livtetFfiGetDistinctWorkStatuses() throws -> [WorkStatusInfo] {
    try getDistinctWorkStatuses()
}

public func livtetFfiListBooksWithFilters(
    limit: Int32,
    offset: Int32,
    order: BookSearchSortOrder,
    formatIds: [ULID],
    languageIds: [ULID]
) throws -> [Book] {
    let filters = BookListFilters(formatIds: formatIds, languageIds: languageIds)
    return try listBooksWithFilters(
        limit: limit,
        offset: offset,
        order: order,
        filters: filters
    )
}

// MARK: - Duplicate detection & merge
//
// These wrappers expose the FFI exports added by the duplicate
// detection & merge feature. The Rust side is the single source of
// truth — `findDuplicateWorks`, `findDuplicateEditionsInWork`, and
// `findCrossWorkEditionDuplicates` return ranked candidate lists
// filtered by `minConfidence`; `mergeWorks` / `mergeEditions` fold a
// duplicate into a primary transactionally; `moveEditionToWork` is
// the recovery action for cross-work edition duplicates.
//
// The `matchKinds` argument lets the caller select which rules fire
// (e.g. `[.ExactIsbn, .TitleAndAuthor(titleSimilarity: 0.85)]`). The
// `conflictResolution` argument is the per-field resolution the user
// picked in the conflict sheet (`.keepPrimary` / `.keepDuplicate` /
// `.keepBoth` per field).

public func livtetFfiFindDuplicateWorks(
    matchKinds: [DuplicateMatchKindMobile],
    minConfidence: Float
) async throws -> [DuplicateCandidateMobile] {
    try await findDuplicateWorks(matchKinds: matchKinds, minConfidence: minConfidence)
}

public func livtetFfiFindDuplicateEditionsInWork(
    workId: ULID,
    matchKinds: [DuplicateMatchKindMobile],
    minConfidence: Float
) async throws -> [EditionDuplicateCandidateMobile] {
    try await findDuplicateEditionsInWork(
        workId: workId,
        matchKinds: matchKinds,
        minConfidence: minConfidence
    )
}

public func livtetFfiFindCrossWorkEditionDuplicates(
    matchKinds: [DuplicateMatchKindMobile],
    minConfidence: Float
) async throws -> [CrossWorkEditionDuplicateMobile] {
    try await findCrossWorkEditionDuplicates(
        matchKinds: matchKinds,
        minConfidence: minConfidence
    )
}

public func livtetFfiMergeWorks(
    primaryWorkId: ULID,
    duplicateWorkId: ULID,
    conflictResolution: WorkMergeConflictResolutionMobile
) async throws -> MergeResultMobile {
    try await mergeWorks(
        primaryWorkId: primaryWorkId,
        duplicateWorkId: duplicateWorkId,
        conflictResolution: conflictResolution
    )
}

public func livtetFfiMergeEditions(
    primaryEditionId: ULID,
    duplicateEditionId: ULID,
    conflictResolution: EditionMergeConflictResolutionMobile
) async throws -> MergeResultMobile {
    try await mergeEditions(
        primaryEditionId: primaryEditionId,
        duplicateEditionId: duplicateEditionId,
        conflictResolution: conflictResolution
    )
}

public func livtetFfiMoveEditionToWork(
    editionId: ULID,
    targetWorkId: ULID
) async throws {
    try await moveEditionToWork(editionId: editionId, targetWorkId: targetWorkId)
}

/// Thrown by bridge wrappers when the underlying Rust FFI function is
/// no longer present. Surfaces as a typed error instead of a
/// `fatalError` so callers can handle the absence at runtime (e.g.,
/// degrade the UI feature gracefully) rather than crashing the app.
public struct FfiUnavailableError: Error, CustomStringConvertible {
    public let bridgeFunction: String
    public let rustSymbol: String
    public var description: String {
        "\(bridgeFunction): rust symbol `\(rustSymbol)` is not exported by livtet-ffi"
    }
}

// MARK: - Add Book wizard FFI wrappers
//
// These wrappers expose the FFI exports consumed by the Add Book
// wizard. Each wrapper is a thin pass-through to the corresponding
// UniFFI-generated Swift function in `LivtetKitFFI`, prefixed with
// `livtetFfi` so the call site reads consistently with the rest of
// the bridge surface.

public func livtetFfiCreateBookComplete(
    title: String, description: String?, editionTitle: String?, isbn: String?,
    publishedDate: String?, languageId: ULID?, authorNames: [String], publisher: String?
) throws -> Book {
    try createBookComplete(
        title: title, description: description, editionTitle: editionTitle,
        isbn: isbn, publishedDate: publishedDate, languageId: languageId,
        authorNames: authorNames, publisher: publisher
    )
}

public func livtetFfiFindWorksByTitlePrefix(prefix: String, limit: Int32) throws -> [WorkSummary] {
    try findWorksByTitlePrefix(prefix: prefix, limit: limit)
}

public func livtetFfiFindWorkByIsbn(isbnUrn: String) throws -> ExistingWorkSummary? {
    try findWorkByIsbn(isbnUrn: isbnUrn)
}

public func livtetFfiMergeReplaceWork(
    workId: ULID, newTitle: String, newDescription: String?, newIsbn: String,
    newEditionTitle: String?, publishedDate: String?
) throws -> Book {
    try mergeReplaceWork(
        workId: workId, newTitle: newTitle, newDescription: newDescription,
        newIsbn: newIsbn, newEditionTitle: newEditionTitle, publishedDate: publishedDate
    )
}

public func livtetFfiCreateEditionForWork(
    workId: ULID, isbnUrn: String, editionTitle: String?, publishedDate: String?, languageId: ULID?
) throws -> ULID {
    try createEditionForWork(
        workId: workId, isbnUrn: isbnUrn, editionTitle: editionTitle,
        publishedDate: publishedDate, languageId: languageId
    )
}

public func livtetFfiLinkIsbnToExistingEdition(editionId: ULID, isbnUrn: String) throws {
    try linkIsbnToExistingEdition(editionId: editionId, isbnUrn: isbnUrn)
}

public func livtetFfiInitPlugins() async throws {
    try await initPlugins()
}

public func livtetFfiSearchProviders(query: String) async throws -> [PluginHitMobile] {
    try await searchProviders(query: query)
}

public func livtetFfiUpdateEdition(
    editionId: ULID, title: String?, publishedDate: String?,
    formatId: ULID?, languageId: ULID?, notes: String?, description: String?
) throws -> Edition? {
    try updateEdition(
        editionId: editionId, title: title, publishedDate: publishedDate,
        formatId: formatId, languageId: languageId, notes: notes, description: description
    )
}

public func livtetFfiSetEditionCover(editionId: ULID, localPath: String) throws {
    try setEditionCover(editionId: editionId, localPath: localPath)
}

public func livtetFfiGetEditionsForWork(workId: ULID) throws -> [Edition] {
    try getEditionsForWork(workId: workId)
}

public func livtetFfiGetEditionsWithCoversForWork(workId: ULID) throws -> [Edition] {
    try getEditionsWithCoversForWork(workId: workId)
}

public func livtetFfiSetEditionManualCover(editionId: ULID, localPath: String) throws {
    try setEditionManualCover(editionId: editionId, localPath: localPath)
}

public func livtetFfiFindOrCreateTag(name: String) throws -> TagInfo {
    try findOrCreateTag(name: name)
}

public func livtetFfiFindOrCreateGenre(name: String) throws -> GenreInfo {
    try findOrCreateGenre(name: name)
}

public func livtetFfiFindOrCreateSubject(name: String) throws -> SubjectInfo {
    try findOrCreateSubject(name: name)
}

public func livtetFfiLinkWorkTag(workId: ULID, tagId: ULID) throws {
    try linkWorkTag(workId: workId, tagId: tagId)
}

public func livtetFfiLinkWorkGenre(workId: ULID, genreId: ULID) throws {
    try linkWorkGenre(workId: workId, genreId: genreId)
}

public func livtetFfiLinkWorkSubject(workId: ULID, subjectId: ULID) throws {
    try linkWorkSubject(workId: workId, subjectId: subjectId)
}

// MARK: - Paired Devices (sync)

public func livtetFfiGetPairedDevices() throws -> [PairedDeviceMobile] {
    try getPairedDevices()
}

public func livtetFfiPairDevice(name: String, address: String, port: UInt16, deviceType: String) throws -> PairedDeviceMobile {
    try pairDevice(name: name, address: address, port: port, deviceType: deviceType)
}

public func livtetFfiUnpairDevice(deviceId: DbId) throws {
    try unpairDevice(deviceId: deviceId)
}

public func livtetFfiGetNetworkAddresses() throws -> NetworkAddressesMobile {
    try getNetworkAddresses()
}

public func livtetFfiListInstalledPlugins() throws -> [InstalledPluginMobile] {
    try listInstalledPlugins()
}

public func livtetFfiSetPluginEnabled(pluginId: String, enabled: Bool) throws {
    try setPluginEnabled(pluginId: pluginId, enabled: enabled)
}

public func livtetFfiPluginGetSetting(pluginId: String, key: String) throws -> String? {
    try pluginGetSetting(pluginId: pluginId, key: key)
}

public func livtetFfiPluginSaveSetting(pluginId: String, key: String, value: String) throws {
    try pluginSaveSetting(pluginId: pluginId, key: key, value: value)
}

public func livtetFfiGetReadingProgress(editionId: DbId, formatId: DbId) throws -> ReadingProgress? {
    try getReadingProgress(editionId: editionId, formatId: formatId)
}

public func livtetFfiUpsertReadingProgress(
    editionId: DbId,
    formatId: DbId,
    progress: Double,
    progressUnit: ProgressUnit,
    lastLocation: String?,
    totalReadingTimeSecs: Int64
) throws {
    try upsertReadingProgress(
        editionId: editionId,
        formatId: formatId,
        progress: progress,
        progressUnit: progressUnit,
        lastLocation: lastLocation,
        totalReadingTimeSecs: totalReadingTimeSecs
    )
}

// MARK: - Reader module FFI wrappers
//
// These wrappers expose the FFI exports consumed by the reader module
// (`Sources/LivtetKit/Reader/`). Each wrapper is a thin pass-through to the
// corresponding UniFFI-generated Swift function in `LivtetKitFFI`, prefixed
// with `livtetFfi` so the call site reads consistently with the rest of the
// bridge surface.

/// Resolve the preferred readable edition for a work. Returns `nil` if the
/// work has no edition with a cached file in `digital_inventory`. The
/// returned `Edition` has `inventory_id` and `cover_path` populated from the
/// matching `digital_inventory` row.
public func livtetFfiGetReadableEditionForWork(workId: DbId) throws -> Edition? {
    try getReadableEditionForWork(workId: workId)
}

/// Get the cached file path for an inventory item. Returns `nil` when no
/// local file has been registered for the given `inventoryId`.
public func livtetFfiGetCachedFilePath(inventoryId: DbId) throws -> String? {
    try getCachedFilePath(inventoryId: inventoryId)
}

/// Register a local file for an inventory item. The Rust side computes the
/// Blake3 hash and stores the row in `digital_inventory`; the Swift side does
/// not re-implement hashing. Returns the resulting `CachedFile` row.
public func livtetFfiRegisterLocalFile(inventoryId: DbId, localPath: String) throws -> CachedFile {
    try registerLocalFile(inventoryId: inventoryId, localPath: localPath)
}

// MARK: - Seed (debug only)
//
// Populate the database with realistic demo data. Only available in debug
// builds with the `fake` feature enabled.

public func livtetFfiSeedDatabase(works: Int32) async throws -> SeedResultMobile {
    try await seedDatabase(works: works)
}
