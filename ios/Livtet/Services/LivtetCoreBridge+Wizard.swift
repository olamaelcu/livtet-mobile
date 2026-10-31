import Foundation
import LivtetKit

/// Write-side facade for the Add Book wizard, backed by `LivtetStore`.
///
/// The old free-function surface (`createBookComplete`, `mergeReplaceWork`,
/// `findOrCreateAuthor`/`Tag`/`Genre`/`Subject`, `linkWork*`, plugin search,
/// `setEditionCover`) no longer exists: `LivtetStore` exposes editions,
/// identifiers, annotations and reading state — no create-work operation.
/// The wizard's creation flow is gated off (`isSaveAvailable == false`), so
/// only the store-backed operations remain here.
protocol WizardBridge: AnyObject {
    func getEditionsForWork(workId: DbId) async throws -> [EditionSummary]
    func updateEdition(
        editionId: DbId,
        title: String?,
        publishedDate: String?,
        formatId: DbId?,
        languageId: DbId?,
        notes: String?,
        description: String?
    ) async throws -> EditionDetail
    func linkIsbnToExistingEdition(editionId: DbId, isbnUrn: String) async throws
    func getDistinctLanguages() async throws -> [LibraryLanguage]
    func getDistinctFormats() async throws -> [FormatInfo]
    func downloadImage(from url: URL) async throws -> URL
}

final class LivtetWizardBridgeAdapter: WizardBridge {
    private let imageDownloader: ImageDownloader

    init(imageDownloader: ImageDownloader = ImageDownloader()) {
        self.imageDownloader = imageDownloader
    }

    func getEditionsForWork(workId: DbId) async throws -> [EditionSummary] {
        try await requireStore().listEditions(workId: workId)
    }

    func updateEdition(
        editionId: DbId,
        title: String?,
        publishedDate: String?,
        formatId: DbId?,
        languageId: DbId?,
        notes: String?,
        description: String?
    ) async throws -> EditionDetail {
        let patch = EditionPatch(
            title: title,
            description: description,
            notes: notes,
            publishedDate: try Self.publishedDate(from: publishedDate),
            formatId: formatId,
            languageId: languageId
        )
        return try await requireStore().updateEdition(editionId: editionId, patch: patch)
    }

    func linkIsbnToExistingEdition(editionId: DbId, isbnUrn: String) async throws {
        // `Identifier` is a typealias for the URN string on the Swift side.
        _ = try await requireStore().addEditionIdentifier(editionId: editionId, identifier: isbnUrn)
    }

    func getDistinctLanguages() async throws -> [LibraryLanguage] {
        try await requireStore().listLanguages()
    }

    func getDistinctFormats() async throws -> [FormatInfo] {
        try await requireStore().listFormats()
    }

    func downloadImage(from url: URL) async throws -> URL {
        try await imageDownloader.download(from: url)
    }

    /// The store handle, or a clear error when the library has not been
    /// initialized yet.
    private func requireStore() throws -> LivtetStore {
        guard let store = LivtetCoreBridge.store else {
            throw AppError.notFound("Library store is not open")
        }
        return store
    }

    /// Parses the wizard's free-text `YYYY-MM-DD` date into the FFI's
    /// `PublishedDate`. `EditionPatch` only stores the full calendar date;
    /// year-only and year-month forms are rejected by the core, so they are
    /// rejected here with the same message instead of surfacing a database
    /// error later.
    private static func publishedDate(from text: String?) throws -> PublishedDate? {
        guard let text, !text.isEmpty else { return nil }
        let parts = text.split(separator: "-", omittingEmptySubsequences: true)
        guard parts.count == 3,
              let year = Int32(parts[0]),
              let month = UInt8(parts[1]),
              let day = UInt8(parts[2]) else {
            throw LivtetError.InvalidInput("Published date must be YYYY-MM-DD")
        }
        return .yearMonthDay(year: year, month: month, day: day)
    }
}
