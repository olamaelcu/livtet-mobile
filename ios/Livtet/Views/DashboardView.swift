import Inject
import LivtetKit
import LivtetKitFFI
import SwiftUI

/// The Dashboard tab of the Livtet iOS app.
///
/// Composes every dashboard section into a single vertical scroll
/// surface. The view delegates all state to [DashboardViewModel] and
/// keeps no derived state of its own — the sections above and below
/// the (optional) error banner each render their own data if present.
///
/// Tapping any [QuickActionCard] switches to `AppTab.library`, providing a
/// behavior where the dashboard's quick actions navigate to the Library tab.
struct DashboardView: View {
    /// Bound to the parent `TabView`'s selection so quick-action taps
    /// can jump to the Library tab.
    @Binding var selectedTab: AppTab

    @StateObject private var viewModel = DashboardViewModel()
    @State private var showAddBookWizard = false

    @ObserveInjection var forceRedraw

    /// Navigation-bar title mirrors the greeting's time-of-day label
    /// so the chrome and the lead card agree on the current period
    /// ("Evening tide", "Good morning", etc.). Falls back to
    /// `"Dashboard"` while the greeting is still loading.
    private var navigationTitle: String {
        viewModel.greeting?.label ?? "Dashboard"
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 24) {
                    if let greeting = viewModel.greeting {
                        GreetingCard(greeting: greeting)
                            .accessibilityLabel(greeting.label)
                    }

                    if let error = viewModel.error {
                        ErrorBanner(
                            message: error.localizedDescription,
                            onRetry: { viewModel.retry() }
                        )
                    } else if let stats = viewModel.stats {
                        StatsRow(stats: stats)
                            .accessibilityLabel("Your Reading Stats")
                            .accessibilityHint("Total books: \(stats.totalBooks), in progress: \(stats.booksInProgress), finished: \(stats.finishedBooks)")
                        QuickActions(
                            stats: stats,
                            onNavigateToLibrary: navigateToLibrary,
                            onAddBook: openAddBookWizard
                        )
                    }

                    if let book = viewModel.recentBook {
                        ContinueReadingCard(book: book)
                            .accessibilityLabel("Continue Reading")
                            .accessibilityHint("Tap to continue reading \(book.title)")
                    }

                    if !viewModel.recentSearches.isEmpty {
                        RecentSearchesRow(searches: viewModel.recentSearches)
                            .accessibilityLabel("Recent Searches")
                    }

                    FeedPlaceholderCard()
                        .accessibilityLabel("Feed")
                        .accessibilityHint("Friend activity and recommendations will appear here once the social feed is ready")
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 24)
            }
            .background(Color("surfaceDefault").ignoresSafeArea())
            .navigationTitle(navigationTitle)
            .refreshable { await viewModel.load() }
            .task { await viewModel.load() }
        }
        .enableInjection()
        .fullScreenCover(isPresented: $showAddBookWizard) {
            AddBookWizardView()
        }
    }

    private func navigateToLibrary() {
        selectedTab = .library
    }

    private func openAddBookWizard() {
        showAddBookWizard = true
    }
}

/// Renders the conditional trio of [QuickActionCard]s based on
/// [DashboardStats]:
///
/// - **Add Your First Book** — `totalBooks < 10`
/// - **Record Your Reading** — no first-reading row yet, or the first
///   reading is less than 14 days old
/// - **Finish a Book** — `finishedBooks == 0`
private struct QuickActions: View {
    let stats: DashboardStats
    let onNavigateToLibrary: () -> Void
    let onAddBook: () -> Void

    var body: some View {
        let firstReadingAt = stats.firstReadingAtMillis
        let now = Date().timeIntervalSince1970 * 1000
        let daysSinceFirst: Int64 = {
            guard let firstReadingAt else { return 0 }
            return Int64((now - Double(firstReadingAt)) / (1000 * 60 * 60 * 24))
        }()

        let showAddBook = stats.totalBooks < 10
        let showRecordReading = firstReadingAt == nil || daysSinceFirst < 14
        let showFinishBook = stats.finishedBooks == 0

        VStack(spacing: 8) {
            if showAddBook {
                QuickActionCard(
                    icon: .lucideBookPlus,
                    title: "Add Your First Book",
                    description: "Build your library to get started",
                    progress: progressFraction(current: stats.totalBooks, total: 10),
                    progressLabel: "\(stats.totalBooks)/10 books",
                    tintColor: Color("successOnNormal"),
                    onTap: onAddBook
                )
            }

            if showRecordReading {
                let description = firstReadingAt == nil
                    ? "Log your first reading session"
                    : "Keep your reading streak going"
                QuickActionCard(
                    icon: .lucideBookOpen,
                    title: "Record Your Reading",
                    description: description,
                    progress: progressFraction(current: daysSinceFirst, total: 14),
                    progressLabel: "\(daysSinceFirst)/14 days",
                    tintColor: Color("warningOnNormal"),
                    onTap: onNavigateToLibrary
                )
            }

            if showFinishBook {
                QuickActionCard(
                    icon: .lucideCircleCheck,
                    title: "Finish a Book",
                    description: "Complete a book to make progress",
                    progress: nil,
                    progressLabel: nil,
                    tintColor: Color("neutralOnNormal"),
                    onTap: onNavigateToLibrary
                )
            }
        }
    }

    /// Clamp the progress ratio to [0, 1].
    private func progressFraction(current: Int64, total: Int64) -> Double {
        guard total > 0 else { return 0 }
        return min(1, max(0, Double(current) / Double(total)))
    }
}

#if DEBUG
#Preview {
    DashboardView(selectedTab: .constant(.dashboard))
}
#endif
