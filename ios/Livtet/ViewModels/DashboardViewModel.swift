import Combine
import Foundation
import LivtetKit
import LivtetKitFFI

/// View-state for the Dashboard screen:
/// - On `.task` or `.refreshable`, fire all four FFI calls in parallel
///   via `async let` so the dashboard paints in one round-trip.
/// - On failure, capture the [AppError] and let the view render an
///   inline retry banner above the stats card.
@MainActor
final class DashboardViewModel: ObservableObject {
    // MARK: - Published State

    @Published private(set) var greeting: Greeting?
    @Published private(set) var stats: DashboardStats?
    @Published private(set) var recentBook: RecentlyReadBook?
    @Published private(set) var recentSearches: [RecentSearch] = []
    @Published private(set) var isLoading = false
    @Published private(set) var error: AppError?

    /// True iff any of `greeting`, `stats`, `recentBook`, `recentSearches`
    /// has been populated. Used by the view to decide between
    /// "loading skeleton" and "error banner" rendering.
    var hasAnyData: Bool {
        greeting != nil
            || stats != nil
            || recentBook != nil
            || !recentSearches.isEmpty
    }

    // MARK: - Dependencies

    private let bridge: DashboardBridge
    private var cancellables = Set<AnyCancellable>()

    init(bridge: DashboardBridge = LivtetDashboardBridgeAdapter()) {
        self.bridge = bridge
        bindBookCreatedNotification()
    }

    // MARK: - Actions

    /// Loads (or reloads) every dashboard section in parallel. Safe to call
    /// multiple times — subsequent calls while a load is already in flight
    /// are dropped
    func load() async {
        guard !isLoading else { return }
        isLoading = true
        error = nil
        defer { isLoading = false }

        do {
            // Fire all four FFI calls concurrently. The four tasks are
            // independent so we don't need to wait between them.
            async let greetingTask = bridge.getGreeting()
            async let statsTask = bridge.getDashboardStats()
            async let recentBooksTask = bridge.getRecentlyReadBooks(limit: 1)
            async let recentSearchesTask = bridge.getRecentSearches(limit: 5)

            let (loadedGreeting, loadedStats, loadedRecentBooks, loadedRecentSearches) = try await (
                greetingTask,
                statsTask,
                recentBooksTask,
                recentSearchesTask
            )

            greeting = loadedGreeting
            stats = loadedStats
            recentBook = loadedRecentBooks.first
            recentSearches = loadedRecentSearches
        } catch let appError as AppError {
            error = appError
        } catch {
            self.error = .unknown(error.localizedDescription)
        }
    }

    /// Re-runs [load], e.g. from a "Retry" button on the error banner.
    func retry() {
        Task { await load() }
    }

    // MARK: - Private

    /// Reloads the dashboard whenever the Add Book wizard posts a
    /// `.livtetBookCreated` notification. Wrapped in a `Task` because
    /// `load()` is async. The wizard already inserts its new row via
    /// the bridge; the notification is the cue to refresh the stats
    /// and "Add Your First Book" quick-action card.
    private func bindBookCreatedNotification() {
        NotificationCenter.default.publisher(for: .livtetBookCreated)
            .sink { [weak self] _ in
                Task { await self?.load() }
            }
            .store(in: &cancellables)
    }
}
