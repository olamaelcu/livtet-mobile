import Foundation
import LivtetKit
import LivtetKitFFI

protocol WizardBridge: AnyObject {
    func setApiKeys(_ keys: [String: String])
    func initPlugins() async throws
    func searchProviders(query: String) async throws -> [PluginHitMobile]
    func findWorksByTitlePrefix(prefix: String, limit: Int32) throws -> [WorkSummary]
    func findWorkByIsbn(isbnUrn: String) throws -> ExistingWorkSummary?
    func findOrCreateAuthor(name: String) throws -> AuthorInfo
    func findOrCreateTag(name: String) throws -> TagInfo
    func findOrCreateGenre(name: String) throws -> GenreInfo
    func findOrCreateSubject(name: String) throws -> SubjectInfo
    func linkWorkTag(workId: DbId, tagId: DbId) throws
    func linkWorkGenre(workId: DbId, genreId: DbId) throws
    func linkWorkSubject(workId: DbId, subjectId: DbId) throws
    func createBookComplete(
        title: String, description: String?, editionTitle: String?, isbn: String?,
        publishedDate: String?, languageId: DbId?, authorNames: [String], publisher: String?
    ) throws -> Book
    func updateEdition(
        editionId: DbId, title: String?, publishedDate: String?,
        formatId: DbId?, languageId: DbId?, notes: String?, description: String?
    ) throws -> Edition?
    func setEditionCover(editionId: DbId, localPath: String) throws
    func getEditionsForWork(workId: DbId) throws -> [Edition]
    func mergeReplaceWork(
        workId: DbId, newTitle: String, newDescription: String?, newIsbn: String,
        newEditionTitle: String?, publishedDate: String?
    ) throws -> Book
    func createEditionForWork(
        workId: DbId, isbnUrn: String, editionTitle: String?, publishedDate: String?, languageId: DbId?
    ) throws -> DbId
    func linkIsbnToExistingEdition(editionId: DbId, isbnUrn: String) throws
    func getDistinctLanguages() throws -> [LanguageInfo]
    func getDistinctFormats() throws -> [FormatInfo]
    func downloadImage(from url: URL) async throws -> URL
}

final class LivtetWizardBridgeAdapter: WizardBridge {
    private let imageDownloader: ImageDownloader

    init(imageDownloader: ImageDownloader = ImageDownloader()) {
        self.imageDownloader = imageDownloader
    }

    func setApiKeys(_ keys: [String: String]) { livtetFfiSetSystemSecrets(keys) }
    func initPlugins() async throws { try await livtetFfiInitPlugins() }
    func searchProviders(query: String) async throws -> [PluginHitMobile] {
        try await livtetFfiSearchProviders(query: query)
    }
    func findWorksByTitlePrefix(prefix: String, limit: Int32) throws -> [WorkSummary] {
        try livtetFfiFindWorksByTitlePrefix(prefix: prefix, limit: limit)
    }
    func findWorkByIsbn(isbnUrn: String) throws -> ExistingWorkSummary? {
        try livtetFfiFindWorkByIsbn(isbnUrn: isbnUrn)
    }
    func findOrCreateAuthor(name: String) throws -> AuthorInfo {
        try livtetFfiFindOrCreateAuthor(name: name)
    }
    func findOrCreateTag(name: String) throws -> TagInfo {
        try livtetFfiFindOrCreateTag(name: name)
    }
    func findOrCreateGenre(name: String) throws -> GenreInfo {
        try livtetFfiFindOrCreateGenre(name: name)
    }
    func findOrCreateSubject(name: String) throws -> SubjectInfo {
        try livtetFfiFindOrCreateSubject(name: name)
    }
    func linkWorkTag(workId: DbId, tagId: DbId) throws {
        try livtetFfiLinkWorkTag(workId: workId, tagId: tagId)
    }
    func linkWorkGenre(workId: DbId, genreId: DbId) throws {
        try livtetFfiLinkWorkGenre(workId: workId, genreId: genreId)
    }
    func linkWorkSubject(workId: DbId, subjectId: DbId) throws {
        try livtetFfiLinkWorkSubject(workId: workId, subjectId: subjectId)
    }
    func createBookComplete(
        title: String, description: String?, editionTitle: String?, isbn: String?,
        publishedDate: String?, languageId: DbId?, authorNames: [String], publisher: String?
    ) throws -> Book {
        try livtetFfiCreateBookComplete(
            title: title, description: description, editionTitle: editionTitle,
            isbn: isbn, publishedDate: publishedDate, languageId: languageId,
            authorNames: authorNames, publisher: publisher
        )
    }
    func updateEdition(
        editionId: DbId, title: String?, publishedDate: String?,
        formatId: DbId?, languageId: DbId?, notes: String?, description: String?
    ) throws -> Edition? {
        try livtetFfiUpdateEdition(
            editionId: editionId, title: title, publishedDate: publishedDate,
            formatId: formatId, languageId: languageId, notes: notes, description: description
        )
    }
    func setEditionCover(editionId: DbId, localPath: String) throws {
        try livtetFfiSetEditionCover(editionId: editionId, localPath: localPath)
    }
    func getEditionsForWork(workId: DbId) throws -> [Edition] {
        try livtetFfiGetEditionsForWork(workId: workId)
    }
    func mergeReplaceWork(
        workId: DbId, newTitle: String, newDescription: String?, newIsbn: String,
        newEditionTitle: String?, publishedDate: String?
    ) throws -> Book {
        try livtetFfiMergeReplaceWork(
            workId: workId, newTitle: newTitle, newDescription: newDescription,
            newIsbn: newIsbn, newEditionTitle: newEditionTitle, publishedDate: publishedDate
        )
    }
    func createEditionForWork(
        workId: DbId, isbnUrn: String, editionTitle: String?, publishedDate: String?, languageId: DbId?
    ) throws -> DbId {
        try livtetFfiCreateEditionForWork(
            workId: workId, isbnUrn: isbnUrn, editionTitle: editionTitle,
            publishedDate: publishedDate, languageId: languageId
        )
    }
    func linkIsbnToExistingEdition(editionId: DbId, isbnUrn: String) throws {
        try livtetFfiLinkIsbnToExistingEdition(editionId: editionId, isbnUrn: isbnUrn)
    }
    func getDistinctLanguages() throws -> [LanguageInfo] {
        try livtetFfiGetDistinctLanguages()
    }
    func getDistinctFormats() throws -> [FormatInfo] {
        try livtetFfiGetDistinctFormats()
    }
    func downloadImage(from url: URL) async throws -> URL {
        try await imageDownloader.download(from: url)
    }
}
