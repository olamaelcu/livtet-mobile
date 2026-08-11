import Combine
import Foundation
import LivtetKit
import LivtetKitFFI
import os.log

@MainActor
final class AddBookWizardViewModel: ObservableObject {
    @Published var data: WizardData
    @Published var currentPage: WizardPage = .titleAndCover
    @Published private(set) var isSearching = false
    @Published private(set) var isSaving = false
    @Published var errorMessage: String?
    @Published var providerError: ProviderErrorInfo?
    @Published var searchSource: SearchSource?
    @Published var duplicateSummary: ExistingWorkSummary?
    @Published var languages: [LanguageInfo] = []
    @Published var formats: [FormatInfo] = []
    @Published var partialSaveWarning: String?
    @Published private(set) var didCompleteSave = false
    /// Phase 1 ships the wizard UI without the write-back path. The
    /// "Save book" affordance on the Tags step surfaces this banner
    /// instead of calling the FFI save flow. Flip to `false` (or delete
    /// the gate entirely) once the Phase 2 core/ FFI work lands.
    @Published private(set) var isSaveAvailable = false

    private let bridge: WizardBridge
    private let googleBooksClient: GoogleBooksClient
    private let openLibraryClient: OpenLibraryClient
    private static let logger = Logger(subsystem: "net.olamaelcu.livtet", category: "AddBookWizardViewModel")
    private var searchTask: Task<Void, Never>?
    private var isbnCheckTask: Task<Void, Never>?
    private var cancellables = Set<AnyCancellable>()

    init(
        bridge: WizardBridge = LivtetWizardBridgeAdapter(),
        googleBooksClient: GoogleBooksClient = GoogleBooksClient(),
        openLibraryClient: OpenLibraryClient = OpenLibraryClient()
    ) {
        self.bridge = bridge
        self.googleBooksClient = googleBooksClient
        self.openLibraryClient = openLibraryClient
        self.data = WizardData()
        bindSearchDebounce()
    }

    func goToPage(_ page: WizardPage) { currentPage = page }
    func goToHub() { currentPage = .hub }

    /// Advance to the next step in the linear 5-step flow. Centralised
    /// here so the step views don't need to know about page order.
    func goToNext() {
        switch currentPage {
        case .titleAndCover: currentPage = .contributors
        case .contributors: currentPage = .genres
        case .genres: currentPage = .subjects
        case .subjects: currentPage = .tags
        case .tags, .hub: break
        }
    }

    /// Step back in the linear flow. Used by the per-step back button.
    func goToBack() {
        switch currentPage {
        case .titleAndCover: break
        case .contributors: currentPage = .titleAndCover
        case .genres: currentPage = .contributors
        case .subjects: currentPage = .genres
        case .tags: currentPage = .subjects
        case .hub: currentPage = .tags
        }
    }

    var canContinueFromTitleAndCover: Bool {
        !data.title.trimmingCharacters(in: .whitespaces).isEmpty && data.cover != nil
    }

    /// Backward-compat alias for the older 2-in-1 step that combined
    /// title and authors. Settles to `true` when both step 1 and step 2
    /// are valid, which is the same gate the original
    /// `canContinueFromTitleAndAuthors` enforced.
    var canContinueFromTitleAndAuthors: Bool {
        canContinueFromTitleAndCover && canContinueFromContributors
    }

    var canContinueFromContributors: Bool {
        data.authors.contains { $0.role == "author" && !$0.name.trimmingCharacters(in: .whitespaces).isEmpty }
    }

    var canCreateBook: Bool { canContinueFromTitleAndAuthors }

    func isItemFilled(_ page: WizardPage) -> Bool {
        switch page {
        case .titleAndCover: return canContinueFromTitleAndCover
        case .contributors: return canContinueFromContributors
        case .titleAndAuthors: return canContinueFromTitleAndAuthors
        case .description: return !data.description.isEmpty
        case .cover: return data.cover != nil
        case .isbn: return !data.isbn.isEmpty
        case .publishedDate: return !data.publishedDate.isEmpty
        case .language: return data.languageId != nil
        case .format: return data.formatId != nil
        case .publisher: return !data.publisher.isEmpty
        case .tags: return !data.tags.isEmpty
        case .genres: return !data.genres.isEmpty
        case .subjects: return !data.subjects.isEmpty
        case .hub: return false
        }
    }

    func previewForItem(_ page: WizardPage) -> String? {
        switch page {
        case .titleAndCover:
            return data.title.isEmpty ? nil : data.title
        case .contributors:
            let names = data.authors.map(\.name).joined(separator: ", ")
            return names.isEmpty ? nil : names
        case .titleAndAuthors:
            return "\"\(data.title)\" — \(data.authors.map(\.name).joined(separator: ", "))"
        case .description:
            return data.description.count > 50 ? String(data.description.prefix(50)) + "..." : data.description
        case .cover: return data.cover?.displayURL?.absoluteString
        case .isbn: return data.isbn
        case .publishedDate: return data.publishedDate
        case .language: return languages.first { $0.id == data.languageId }?.name
        case .format: return formats.first { $0.id == data.formatId }?.name
        case .publisher: return data.publisher
        case .tags: return data.tags.isEmpty ? nil : data.tags.joined(separator: ", ")
        case .genres: return data.genres.isEmpty ? nil : data.genres.joined(separator: ", ")
        case .subjects: return data.subjects.isEmpty ? nil : data.subjects.joined(separator: ", ")
        case .hub: return nil
        }
    }

    func clearItem(_ page: WizardPage) {
        switch page {
        case .titleAndCover: break
        case .contributors: data.authors = []
        case .description: data.description = ""
        case .cover: data.cover = nil
        case .isbn: data.isbn = ""; data.localDedupResults = []
        case .publishedDate: data.publishedDate = ""
        case .language: data.languageId = nil
        case .format: data.formatId = nil
        case .publisher: data.publisher = ""
        case .tags: data.tags = []
        case .genres: data.genres = []
        case .subjects: data.subjects = []
        case .titleAndAuthors: break
        case .hub: break
        }
    }

    private func bindSearchDebounce() {
        $data.map(\.searchQuery).removeDuplicates()
            .debounce(for: .milliseconds(750), scheduler: DispatchQueue.main)
            .dropFirst()
            .sink { [weak self] query in self?.runSearch(query: query) }
            .store(in: &cancellables)
    }

    func updateSearchQuery(_ query: String) { data.searchQuery = query }

    /// Phase 1: step 1 IS the search step (search inline) — there is no
    /// separate search page to skip. Direct callers to the next step
    /// when `canContinueFromTitleAndCover` is satisfied.
    func continueFromTitleAndCover() {
        guard canContinueFromTitleAndCover else { return }
        currentPage = .contributors
    }

    func selectResult(_ result: ProviderResult) {
        data.title = result.title
        data.isbn = result.isbn ?? ""
        data.publishedDate = result.year.map { "\($0)-01-01" } ?? ""
        data.publisher = result.publisher ?? ""
        data.authors = result.authors.map { AuthorEntry(name: $0) }
        if let url = result.coverUrl {
            data.cover = .remote(url)
        }
        currentPage = .contributors
    }

    func runSearch(query: String) {
        searchTask?.cancel()
        guard query.count >= 3 else {
            data.localDedupResults = []; data.searchResults = []
            return
        }
        isSearching = true
        searchTask = Task { [weak self] in await self?.performSearch(query: query) }
    }

    func performSearch(query: String) async {
        do {
            data.localDedupResults = try bridge.findWorksByTitlePrefix(prefix: query, limit: 5)
        } catch { data.localDedupResults = [] }

        do {
            let items = try await googleBooksClient.search(query: query)
            data.searchResults = items.map(toProviderResult)
            searchSource = .googleBooks
            providerError = nil
            let googleCount = data.searchResults.count
            Self.logger.info("Search served by googleBooks: \(googleCount, privacy: .public) items")
        } catch let error as GoogleBooksError {
            switch error {
            case .providerDown:
                Self.logger.notice("Google Books returned providerDown; falling back to OpenLibrary")
                do {
                    let docs = try await openLibraryClient.search(query: query)
                    data.searchResults = docs.map(toProviderResult)
                    searchSource = .openLibrary
                    providerError = nil
                    let openLibCount = data.searchResults.count
                    Self.logger.info("Search served by openLibrary (fallback): \(openLibCount, privacy: .public) items")
                } catch let fallbackError {
                    Self.logger.error("OpenLibrary fallback failed: \(fallbackError.localizedDescription, privacy: .public); surfacing original Google Books providerDown error")
                    providerError = ProviderErrorInfo(
                        category: error.category,
                        retryAfterSeconds: error.retryAfterSeconds,
                        providerId: "googlebooks"
                    )
                    searchSource = nil
                }
            default:
                providerError = ProviderErrorInfo(
                    category: error.category,
                    retryAfterSeconds: error.retryAfterSeconds,
                    providerId: "googlebooks"
                )
                searchSource = nil
            }
        } catch {
            errorMessage = "Search failed: \(error.localizedDescription)"
            searchSource = nil
        }

        if data.searchResults.isEmpty {
            searchSource = nil
        }
        isSearching = false
    }

    private func toProviderResult(_ item: GoogleBooksItem) -> ProviderResult {
        let info = item.volumeInfo
        let year: Int? = {
            guard let date = info.publishedDate, date.count >= 4 else { return nil }
            return Int(date.prefix(4))
        }()
        let isbn = info.industryIdentifiers?
            .first { $0.type == "ISBN_13" || $0.type == "ISBN_10" }?
            .identifier
        let coverUrl: URL? = {
            guard let raw = info.imageLinks?.thumbnail,
                  let cleaned = GoogleBooksClient.cleanCoverURL(raw) else {
                return nil
            }
            return URL(string: cleaned)
        }()
        return ProviderResult(
            title: info.title,
            authors: info.authors ?? [],
            isbn: isbn,
            year: year,
            publisher: info.publisher,
            coverUrl: coverUrl,
            source: SearchSource.googleBooks.rawValue
        )
    }

    private func toProviderResult(_ doc: OpenLibraryDoc) -> ProviderResult {
        let isbn: String? = {
            guard let list = doc.isbn else { return nil }
            if let isbn13 = list.first(where: { $0.count == 13 }) { return isbn13 }
            if let isbn10 = list.first(where: { $0.count == 10 }) { return isbn10 }
            return list.first
        }()
        let coverUrl: URL? = doc.coverI.flatMap { OpenLibraryClient.coverURL(for: $0) }
        return ProviderResult(
            title: doc.title ?? "",
            authors: doc.authorName ?? [],
            isbn: isbn,
            year: doc.firstPublishYear,
            publisher: doc.publisher?.first,
            coverUrl: coverUrl,
            source: SearchSource.openLibrary.rawValue
        )
    }

    func updateIsbn(_ isbn: String) {
        data.isbn = isbn
        isbnCheckTask?.cancel()
        let clean = isbn.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "-", with: "")
        guard isValidIsbnFormat(clean) else { return }
        isbnCheckTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 750_000_000)
            guard let self, !Task.isCancelled else { return }
            await self.checkIsbnDuplicate(cleanIsbn: clean)
        }
    }

    private func isValidIsbnFormat(_ isbn: String) -> Bool {
        guard !isbn.isEmpty else { return false }
        return isbn.range(of: "^(?:ISBN-?1[03])?[: ]*([0-9X-]+)$", options: .regularExpression) != nil
    }

    private func checkIsbnDuplicate(cleanIsbn: String) async {
        let urn = "urn:isbn:\(cleanIsbn)"
        do {
            if let existing = try bridge.findWorkByIsbn(isbnUrn: urn) {
                duplicateSummary = existing
            }
        } catch {}
    }

    func addAuthor(name: String, role: String) {
        guard !name.isEmpty else { return }
        do {
            _ = try bridge.findOrCreateAuthor(name: name)
            data.authors.append(AuthorEntry(name: name, role: role))
        } catch {
            errorMessage = "Failed to add author: \(error.localizedDescription)"
        }
    }

    func removeAuthor(_ author: AuthorEntry) {
        data.authors.removeAll { $0.id == author.id }
    }

    func loadLanguages() {
        do { languages = try bridge.getDistinctLanguages() } catch {}
    }

    func loadFormats() {
        do { formats = try bridge.getDistinctFormats() } catch {}
    }

    func save() {
        guard isSaveAvailable else { return }
        guard canCreateBook else { return }
        isSaving = true
        errorMessage = nil
        partialSaveWarning = nil
        Task { [weak self] in await self?.performSave() }
    }

    private func performSave() async {
        do {
            let cleanIsbn = data.isbn.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "-", with: "")
            let authorNames = data.authors.map { $0.name.trimmingCharacters(in: .whitespaces) }

            if !cleanIsbn.isEmpty {
                let urn = "urn:isbn:\(cleanIsbn)"
                if let existing = try bridge.findWorkByIsbn(isbnUrn: urn) {
                    duplicateSummary = existing
                    isSaving = false
                    return
                }
            }

            let book = try bridge.createBookComplete(
                title: data.title.trimmingCharacters(in: .whitespaces),
                description: data.description.isEmpty ? nil : data.description,
                editionTitle: data.title.trimmingCharacters(in: .whitespaces),
                isbn: cleanIsbn.isEmpty ? nil : cleanIsbn,
                publishedDate: data.publishedDate.isEmpty ? nil : data.publishedDate,
                languageId: data.languageId,
                authorNames: authorNames,
                publisher: data.publisher.isEmpty ? nil : data.publisher
            )

            let editions = try bridge.getEditionsForWork(workId: book.id)
            guard let edition = editions.first else {
                errorMessage = "Book created but no edition found"
                isSaving = false
                return
            }

            var warnings: [String] = []

            if let formatId = data.formatId {
                do {
                    _ = try bridge.updateEdition(
                        editionId: edition.id, title: nil, publishedDate: nil,
                        formatId: formatId, languageId: nil, notes: nil, description: nil
                    )
                } catch { warnings.append("format update failed") }
            }

            if let coverUrl = data.coverUrl {
                do {
                    let localUrl = try await bridge.downloadImage(from: coverUrl)
                    try bridge.setEditionCover(editionId: edition.id, localPath: localUrl.path)
                } catch { warnings.append("cover image failed") }
            }

            for tagName in data.tags {
                do {
                    let tag = try bridge.findOrCreateTag(name: tagName)
                    try bridge.linkWorkTag(workId: book.id, tagId: tag.id)
                } catch { warnings.append("tag '\(tagName)' failed") }
            }

            for genreName in data.genres {
                do {
                    let genre = try bridge.findOrCreateGenre(name: genreName)
                    try bridge.linkWorkGenre(workId: book.id, genreId: genre.id)
                } catch { warnings.append("genre '\(genreName)' failed") }
            }

            for subjectName in data.subjects {
                do {
                    let subject = try bridge.findOrCreateSubject(name: subjectName)
                    try bridge.linkWorkSubject(workId: book.id, subjectId: subject.id)
                } catch { warnings.append("subject '\(subjectName)' failed") }
            }

            if !warnings.isEmpty {
                partialSaveWarning = "Book saved, but: \(warnings.joined(separator: ", "))"
            }

            NotificationCenter.default.post(name: .livtetBookCreated, object: nil)
            didCompleteSave = true
            isSaving = false
        } catch let error as MobileError {
            handleMobileError(error)
            isSaving = false
        } catch {
            errorMessage = "Save failed: \(error.localizedDescription)"
            isSaving = false
        }
    }

    private func handleMobileErrorAsBridge(_ error: Error) {
        if let mobile = error as? MobileError {
            handleMobileError(mobile)
        } else {
            errorMessage = "Save failed: \(error.localizedDescription)"
        }
    }

    private func handleMobileError(_ error: MobileError) {
        switch error {
        case .Database(let msg):
            if msg.contains("UNIQUE constraint failed") {
                errorMessage = "This ISBN already exists in your library"
            } else if msg.contains("constraint failed") {
                errorMessage = "Duplicate entry detected"
            } else {
                errorMessage = "Database error: \(msg)"
            }
        case .IsbnConflict(_, _, let conflictingIsbn):
            let urn = "urn:isbn:\(conflictingIsbn.replacingOccurrences(of: "urn:isbn:", with: ""))"
            if let existing = try? bridge.findWorkByIsbn(isbnUrn: urn) {
                duplicateSummary = existing
            } else {
                errorMessage = "ISBN conflict on \(conflictingIsbn)"
            }
        case .NotFound(let msg):
            errorMessage = "Not found: \(msg)"
        default:
            errorMessage = "Save failed: \(error.localizedDescription)"
        }
    }

    enum DuplicateAction {
        case replace(workId: DbId), newEdition(workId: DbId), linkIsbn(editionId: DbId), cancel
    }

    func handleDuplicateAction(_ action: DuplicateAction) {
        guard let summary = duplicateSummary else { return }
        duplicateSummary = nil
        switch action {
        case .cancel: return
        case .replace(let workId): performReplace(workId: workId, summary: summary)
        case .newEdition(let workId): performNewEdition(workId: workId)
        case .linkIsbn(let editionId): performLinkIsbn(editionId: editionId)
        }
    }

    private func performReplace(workId: DbId, summary: ExistingWorkSummary) {
        let cleanIsbn = data.isbn.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "-", with: "")
        isSaving = true
        Task { [weak self] in
            guard let self else { return }
            do {
                _ = try bridge.mergeReplaceWork(
                    workId: workId,
                    newTitle: data.title.trimmingCharacters(in: .whitespaces),
                    newDescription: data.description.isEmpty ? nil : data.description,
                    newIsbn: cleanIsbn,
                    newEditionTitle: data.title.trimmingCharacters(in: .whitespaces),
                    publishedDate: data.publishedDate.isEmpty ? nil : data.publishedDate
                )
                NotificationCenter.default.post(name: .livtetBookCreated, object: nil)
                didCompleteSave = true
                isSaving = false
            } catch {
                handleMobileErrorAsBridge(error)
                isSaving = false
            }
        }
    }

    private func performNewEdition(workId: DbId) {
        let cleanIsbn = data.isbn.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "-", with: "")
        isSaving = true
        Task { [weak self] in
            guard let self else { return }
            do {
                _ = try bridge.createEditionForWork(
                    workId: workId, isbnUrn: "urn:isbn:\(cleanIsbn)",
                    editionTitle: data.title.trimmingCharacters(in: .whitespaces),
                    publishedDate: data.publishedDate.isEmpty ? nil : data.publishedDate,
                    languageId: data.languageId
                )
                NotificationCenter.default.post(name: .livtetBookCreated, object: nil)
                didCompleteSave = true
                isSaving = false
            } catch {
                handleMobileErrorAsBridge(error)
                isSaving = false
            }
        }
    }

    private func performLinkIsbn(editionId: DbId) {
        let cleanIsbn = data.isbn.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "-", with: "")
        isSaving = true
        Task { [weak self] in
            guard let self else { return }
            do {
                try bridge.linkIsbnToExistingEdition(
                    editionId: editionId, isbnUrn: "urn:isbn:\(cleanIsbn)"
                )
                NotificationCenter.default.post(name: .livtetBookCreated, object: nil)
                didCompleteSave = true
                isSaving = false
            } catch {
                handleMobileErrorAsBridge(error)
                isSaving = false
            }
        }
    }
}

struct ProviderErrorInfo: Equatable {
    let category: ProviderErrorCategory
    let retryAfterSeconds: Int?
    let providerId: String

    var userMessage: String {
        switch category {
        case .needsAuth:
            return "Search needs authentication. Add an API key in Settings to use Google Books."
        case .rateLimited:
            return retryAfterSeconds.map { "Rate limited — try again in \($0) seconds." }
                ?? "Rate limited — try again shortly."
        case .timeout: return "Search timed out. Try again."
        case .notFound: return "No results found."
        case .providerDown: return "Search is having problems. Try again later."
        }
    }
}
