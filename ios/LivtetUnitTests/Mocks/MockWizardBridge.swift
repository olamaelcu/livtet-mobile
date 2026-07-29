import Foundation
import FastULID
@testable import Livtet
import LivtetKit
import LivtetKitFFI

final class MockWizardBridge: WizardBridge {
    var apiKeys: [String: String] = [:]
    var searchResults: [PluginHitMobile] = []
    var findByIsbnResult: ExistingWorkSummary?
    var createBookResult: Book?
    var createBookError: Error?
    var editionsForWork: [Edition] = []
    var searchError: Error?

    var linkWorkTagCalls: [(workId: DbId, tagId: DbId)] = []
    var linkWorkGenreCalls: [(workId: DbId, genreId: DbId)] = []
    var linkWorkSubjectCalls: [(workId: DbId, subjectId: DbId)] = []
    var findOrCreateTagCalls: [String] = []
    var findOrCreateGenreCalls: [String] = []
    var findOrCreateSubjectCalls: [String] = []
    var createBookCallCount = 0
    var createBookCallArgs: (title: String, isbn: String?)?

    func setApiKeys(_ keys: [String: String]) { apiKeys = keys }
    func initPlugins() async throws {}
    func searchProviders(query: String) async throws -> [PluginHitMobile] {
        if let searchError { throw searchError }
        return searchResults
    }
    func findWorksByTitlePrefix(prefix: String, limit: Int32) throws -> [WorkSummary] { [] }
    func findWorkByIsbn(isbnUrn: String) throws -> ExistingWorkSummary? { findByIsbnResult }
    func findOrCreateAuthor(name: String) throws -> AuthorInfo {
        AuthorInfo(id: ULID(ulidData: Data(repeating: 0, count: 16))!, name: name)
    }
    func findOrCreateTag(name: String) throws -> TagInfo {
        findOrCreateTagCalls.append(name)
        return TagInfo(id: ULID(ulidData: Data(repeating: 0, count: 16))!, name: name)
    }
    func findOrCreateGenre(name: String) throws -> GenreInfo {
        findOrCreateGenreCalls.append(name)
        return GenreInfo(id: ULID(ulidData: Data(repeating: 0, count: 16))!, name: name)
    }
    func findOrCreateSubject(name: String) throws -> SubjectInfo {
        findOrCreateSubjectCalls.append(name)
        return SubjectInfo(id: ULID(ulidData: Data(repeating: 0, count: 16))!, name: name)
    }
    func linkWorkTag(workId: DbId, tagId: DbId) throws { linkWorkTagCalls.append((workId, tagId)) }
    func linkWorkGenre(workId: DbId, genreId: DbId) throws { linkWorkGenreCalls.append((workId, genreId)) }
    func linkWorkSubject(workId: DbId, subjectId: DbId) throws { linkWorkSubjectCalls.append((workId, subjectId)) }
    func createBookComplete(
        title: String, description: String?, editionTitle: String?, isbn: String?,
        publishedDate: String?, languageId: DbId?, authorNames: [String], publisher: String?
    ) throws -> Book {
        createBookCallCount += 1
        createBookCallArgs = (title: title, isbn: isbn)
        if let createBookError { throw createBookError }
        return createBookResult ?? Book(id: ULID(ulidData: Data(repeating: 0, count: 16))!, title: title, description: description)
    }
    func updateEdition(
        editionId: DbId, title: String?, publishedDate: String?,
        formatId: DbId?, languageId: DbId?, notes: String?, description: String?
    ) throws -> Edition? { nil }
    func setEditionCover(editionId: DbId, localPath: String) throws {}
    func getEditionsForWork(workId: DbId) throws -> [Edition] { editionsForWork }
    func mergeReplaceWork(
        workId: DbId, newTitle: String, newDescription: String?, newIsbn: String,
        newEditionTitle: String?, publishedDate: String?
    ) throws -> Book { Book(id: workId, title: newTitle, description: newDescription) }
    func createEditionForWork(
        workId: DbId, isbnUrn: String, editionTitle: String?, publishedDate: String?, languageId: DbId?
    ) throws -> DbId { ULID(ulidData: Data(repeating: 0, count: 16))! }
    func linkIsbnToExistingEdition(editionId: DbId, isbnUrn: String) throws {}
    func getDistinctLanguages() throws -> [LanguageInfo] { [] }
    func getDistinctFormats() throws -> [FormatInfo] { [] }
    func downloadImage(from url: URL) async throws -> URL {
        let local = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).jpg")
        try Data().write(to: local)
        return local
    }
}
