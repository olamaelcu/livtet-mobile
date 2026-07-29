import Foundation
import LivtetKitFFI

struct WizardData: Equatable {
    var title: String = ""
    var description: String = ""
    var isbn: String = ""
    var publishedDate: String = ""
    var languageId: DbId? = nil
    var formatId: DbId? = nil
    var publisher: String = ""
    var authors: [AuthorEntry] = []
    var tags: [String] = []
    var genres: [String] = []
    var subjects: [String] = []
    var coverUrl: URL? = nil
    var searchQuery: String = ""
    var localDedupResults: [WorkSummary] = []
    var searchResults: [ProviderResult] = []
}

enum WizardPage: Hashable {
    case search
    case titleAndAuthors
    case hub
    case description, cover, isbn, publishedDate, language, format, publisher
    case tags, genres, subjects
}

struct AuthorEntry: Equatable, Identifiable {
    let id = UUID()
    var name: String = ""
    var role: String = "author"
}

struct ProviderResult: Equatable, Identifiable {
    let id = UUID()
    let title: String
    let authors: [String]
    let isbn: String?
    let year: Int?
    let publisher: String?
    let coverUrl: URL?
    let source: String
}
