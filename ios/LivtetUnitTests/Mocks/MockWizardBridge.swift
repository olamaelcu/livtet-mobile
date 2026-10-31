import Foundation
@testable import Livtet
import LivtetKit

final class MockWizardBridge: WizardBridge {
    var editionsForWork: [EditionSummary] = []
    var updateEditionResult: EditionDetail?
    var updateEditionError: Error?
    var updateEditionCalls: [(editionId: DbId, title: String?, publishedDate: String?, formatId: DbId?, languageId: DbId?)] = []
    var linkedIsbns: [(editionId: DbId, isbnUrn: String)] = []
    var languages: [LibraryLanguage] = []
    var formats: [FormatInfo] = []

    func getEditionsForWork(workId: DbId) async throws -> [EditionSummary] {
        editionsForWork
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
        updateEditionCalls.append((editionId, title, publishedDate, formatId, languageId))
        if let updateEditionError { throw updateEditionError }
        guard let updateEditionResult else {
            throw AppError.notFound("No edition stub configured")
        }
        return updateEditionResult
    }

    func linkIsbnToExistingEdition(editionId: DbId, isbnUrn: String) async throws {
        linkedIsbns.append((editionId, isbnUrn))
    }

    func getDistinctLanguages() async throws -> [LibraryLanguage] {
        languages
    }

    func getDistinctFormats() async throws -> [FormatInfo] {
        formats
    }

    func downloadImage(from url: URL) async throws -> URL {
        let local = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).jpg")
        try Data().write(to: local)
        return local
    }
}
