import Combine
import Foundation
import LivtetKit

/// View-state for the Library screen.
///
/// Mirrors `DashboardViewModel` in shape but adds user-driven filter inputs
/// that drive Combine-debounced re-fetches against the store. All load paths
/// route through a shared `loadTask` so a fast-arriving filter change cancels
/// the in-flight predecessor rather than racing it on `isLoading`.
///
/// The library lists *works* (`WorkSummary`); editions are fetched on demand
/// by the detail screen. Work-status filters have no store equivalent
/// (`LivtetStore` exposes a single work's status, not a distinct-statuses
/// listing), so that dimension was removed.
@MainActor
final class LibraryViewModel: ObservableObject {
    // MARK: - Output

    @Published private(set) var works: [WorkSummary] = []
    @Published private(set) var formats: [FormatInfo] = []
    @Published private(set) var languages: [LibraryLanguage] = []
    /// Literary quotation for the empty-state surface.
    @Published private(set) var emptyMessage: EmptyMessage?
    @Published private(set) var isLoading = false
    @Published private(set) var error: AppError?

    // MARK: - Input (user-driven)

    @Published var sortOrder: SortDirection = .desc
    @Published var selectedFormatIds: Set<DbId> = []
    @Published var selectedLanguageIds: Set<DbId> = []

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

    /// Initial parallel load of filters + first page of works. Any in-flight
    /// predecessor is cancelled.
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
            let (loadedFormats, loadedLanguages) = try await (formatsTask, languagesTask)
            formats = loadedFormats
            languages = loadedLanguages
            emptyMessage = bridge.getEmptyStateQuotation()

            works = try await fetchWorks()
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
            $selectedLanguageIds.dropFirst().map { _ in () }.eraseToAnyPublisher()
        )
        changes
            .debounce(for: .milliseconds(150), scheduler: DispatchQueue.main)
            .sink { [weak self] in self?.applyFilters() }
            .store(in: &cancellables)
    }

    /// Reloads the library whenever the Add Book wizard posts a
    /// `.livtetBookCreated` notification.
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
            works = try await fetchWorks()
            error = nil
        } catch is CancellationError {
            // superseded
        } catch let appError as AppError {
            error = appError
        } catch {
            self.error = .unknown(error.localizedDescription)
        }
    }

    private func fetchWorks() async throws -> [WorkSummary] {
        try await bridge.listWorks(
            limit: 100,
            offset: 0,
            sortDirection: sortOrder,
            formatIds: Array(selectedFormatIds),
            languageIds: Array(selectedLanguageIds)
        )
    }
}
