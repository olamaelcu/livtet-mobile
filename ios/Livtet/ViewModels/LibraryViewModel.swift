import Combine
import Foundation
import LivtetKit
import LivtetKitFFI

/// View-state for the Library screen.
///
/// Mirrors `DashboardViewModel` in shape but adds user-driven filter
/// inputs that drive Combine-debounced re-fetches against the FFI.
/// All load paths route through a shared `loadTask` so a fast-arriving
/// filter change cancels the in-flight predecessor rather than racing
/// it on `isLoading`.
///
/// Status filters are inert in this version: tapping a status chip
/// mutates `selectedStatusIds` but `fetchBooks` does not forward the
/// selection to the FFI. When the deferred search-and-status feature
/// lands, status will be added to `BookListFilters` and forwarded.
@MainActor
final class LibraryViewModel: ObservableObject {
    // MARK: - Output

    @Published private(set) var editions: [Edition] = []
    @Published private(set) var formats: [FormatInfo] = []
    @Published private(set) var languages: [LanguageInfo] = []
    @Published private(set) var statuses: [WorkStatusInfo] = []
    /// Literary quotation for the empty-state surface. Populated on
    /// every `load()` (in parallel with the format/language/status
    /// fetches) so the empty state has it ready when `books.isEmpty`.
    /// Picked deterministically per call on the Rust side.
    @Published private(set) var emptyMessage: EmptyMessage?
    @Published private(set) var isLoading = false
    @Published private(set) var error: AppError?

    // MARK: - Input (user-driven)

    @Published var sortOrder: BookSearchSortOrder = .descending
    @Published var selectedFormatIds: Set<DbId> = []
    @Published var selectedLanguageIds: Set<DbId> = []
    @Published var selectedStatusIds: Set<DbId> = []

    // MARK: - Dependencies

    private let bridge: LibraryBridge
    private var loadTask: Task<Void, Never>?
    private var cancellables = Set<AnyCancellable>()

    init(bridge: LibraryBridge = LivtetLibraryBridgeAdapter()) {
        self.bridge = bridge
        bindFilterChanges()
        bindBookCreatedNotification()
    }

    // MARK: - Actions

    /// Initial parallel load of filters + first page of books. Any
    /// in-flight predecessor is cancelled.
    func load() {
        loadTask?.cancel()
        loadTask = Task { [weak self] in
            await self?.performLoad()
        }
    }

    /// Re-runs after pull-to-refresh.
    func refresh() { load() }

    /// Re-runs after a fetch failure from the error banner.
    func retry() { load() }

    // MARK: - Private

    private func performLoad() async {
        isLoading = true
        defer { isLoading = false }
        do {
            async let formatsTask = bridge.getDistinctFormats()
            async let languagesTask = bridge.getDistinctLanguages()
            async let statusesTask = bridge.getDistinctWorkStatuses()
            async let emptyTask = bridge.getEmptyStateQuotation()
            let (loadedFormats, loadedLanguages, loadedStatuses, loadedEmpty) = try await (
                formatsTask, languagesTask, statusesTask, emptyTask
            )
            formats = loadedFormats
            languages = loadedLanguages
            statuses = loadedStatuses
            emptyMessage = loadedEmpty

            editions = try await fetchEditions()
            error = nil
        } catch is CancellationError {
            // superseded
        } catch let appError as AppError {
            error = appError
        } catch {
            self.error = .unknown(error.localizedDescription)
        }
    }

    private func bindFilterChanges() {
        let changes = Publishers.MergeMany(
            $sortOrder.dropFirst().map { _ in () }.eraseToAnyPublisher(),
            $selectedFormatIds.dropFirst().map { _ in () }.eraseToAnyPublisher(),
            $selectedLanguageIds.dropFirst().map { _ in () }.eraseToAnyPublisher(),
            $selectedStatusIds.dropFirst().map { _ in () }.eraseToAnyPublisher()
        )
        changes
            .debounce(for: .milliseconds(150), scheduler: DispatchQueue.main)
            .sink { [weak self] in self?.applyFilters() }
            .store(in: &cancellables)
    }

    /// Reloads the library whenever the Add Book wizard posts a
    /// `.livtetBookCreated` notification. The wizard already inserts
    /// its new row via the bridge; the notification is the cue to
    /// surface it in the library list without manual refresh.
    private func bindBookCreatedNotification() {
        NotificationCenter.default.publisher(for: .livtetBookCreated)
            .sink { [weak self] _ in self?.load() }
            .store(in: &cancellables)
    }

    private func applyFilters() {
        loadTask?.cancel()
        loadTask = Task { [weak self] in
            await self?.performFetch()
        }
    }

    private func performFetch() async {
        isLoading = true
        defer { isLoading = false }
        do {
            editions = try await fetchEditions()
            error = nil
        } catch is CancellationError {
            // superseded
        } catch let appError as AppError {
            error = appError
        } catch {
            self.error = .unknown(error.localizedDescription)
        }
    }

    private func fetchEditions() async throws -> [Edition] {
        let filters = BookListFilters(
            formatIds: Array(selectedFormatIds),
            languageIds: Array(selectedLanguageIds)
        )
        let books = try bridge.listBooksWithFilters(
            limit: 50, offset: 0, order: sortOrder, filters: filters
        )

        var allEditions: [Edition] = []
        for book in books {
            do {
                let bookEditions = try bridge.getEditionsWithCoversForWork(workId: book.id)
                allEditions.append(contentsOf: bookEditions)
            } catch {
                // skip works whose editions fail to load
            }
        }
        return allEditions
    }
}
