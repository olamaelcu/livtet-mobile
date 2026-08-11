import Foundation
import LivtetKitFFI

/// Source of the cover image entered in Step 1 — Title and Cover.
///
/// `remote` is the only case the Phase 1 wizard can land in: the user has
/// either typed/pasted a URL, picked a search result, or selected a
/// photo from the OS picker. The `pendingLocal` and `downloaded` cases
/// are wired through the view-model state machine but their persistence
/// paths (the FFI download + `setEditionCover`) are intentionally not
/// exercised by Phase 1 — saving is disabled until the core/ FFI work
/// described in the Phase 2 spec lands.
enum CoverSource: Equatable {
    case remote(URL)
    case pendingLocal(uri: String, mimeType: String, byteSize: Int)
    case downloaded(localPath: String)

    var displayURL: URL? {
        switch self {
        case .remote(let url): return url
        case .pendingLocal(let uri, _, _): return URL(string: uri)
        case .downloaded(let localPath): return URL(fileURLWithPath: localPath)
        }
    }
}

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
    var cover: CoverSource? = nil
    var searchQuery: String = ""
    var localDedupResults: [WorkSummary] = []
    var searchResults: [ProviderResult] = []

    /// Backward-compat accessor for the original `coverUrl: URL?` field.
    /// Older code paths (the Hub's "Cover Image" row, `CoverDetailView`'s
    /// prefilled text field) still read `data.coverUrl`; this lets them
    /// keep working without a separate stored field. New code should
    /// read `cover` directly and pattern-match on the `CoverSource` case.
    var coverUrl: URL? {
        get { cover?.displayURL }
        set {
            if let url = newValue {
                cover = .remote(url)
            } else {
                cover = nil
            }
        }
    }
}

enum WizardPage: Hashable {
    /// Linear 5-step flow pages — driven by the wizard router and the
    /// step views' `goToNext` / `goToBack` transitions.
    case titleAndCover
    case contributors
    case genres
    case subjects
    case tags
    case hub

    /// Hub field pages — never assigned to `currentPage` in the new
    /// flow, but kept in the enum so the existing `HubView` detail
    /// rows can switch on them when the user opens the "More options"
    /// hub from the Tags step. The legacy `cover` case is preserved
    /// for parity; the new Step 1 already gathers the cover before the
    /// user can reach the hub.
    case description, isbn, publishedDate, language, format, publisher, cover
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
