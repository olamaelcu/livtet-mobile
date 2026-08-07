import Inject
import SwiftUI

enum AppTab: Hashable, Identifiable {
    case dashboard, library, puzzle, feed, settings
    var id: Self { self }
}

/// Root tab container for the Livtet iOS app.
///
/// Tabs are switched programmatically via [selectedTab] so dashboard
/// quick-action cards can jump straight to the Library tab without a
/// navigation push.
///
/// Selection is typed to [AppTab] so the iOS 18 `Tab(...)` API and the
/// legacy `.tag(AppTab.case)` pattern share the same binding.
///
/// On iOS 18+ the view uses the modern `Tab(...)` syntax with
/// `.tabViewStyle(.sidebarAdaptable)` so it presents as a sidebar on iPad/macOS
/// and a native floating-glass tab bar on iPhone.
///
/// On iOS 17 the view falls back to the classic `.tabItem` + `.tag(...)`
/// syntax with the system translucent tab bar (no opaque overlay).
struct RootTabView: View {
    @State private var selectedTab: AppTab = .dashboard

    @ObserveInjection var forceRedraw

    var body: some View {
        if #available(iOS 18.0, *) {
            modernTabView
        } else {
            legacyTabView
        }
    }

    @available(iOS 18.0, *)
    private var modernTabView: some View {
        TabView(selection: $selectedTab) {
            Tab("Dashboard", systemImage: "house.fill", value: AppTab.dashboard) {
                DashboardView(selectedTab: $selectedTab)
            }
            Tab("Library", systemImage: "books.vertical.fill", value: AppTab.library) {
                LibraryView()
            }
            Tab("Puzzle", systemImage: "puzzlepiece.extension.fill", value: AppTab.puzzle) {
                PuzzleTabView()
            }
            Tab("Feed", systemImage: "newspaper.fill", value: AppTab.feed) {
                FeedPlaceholderTabView()
            }
            Tab("Settings", systemImage: "gearshape", value: AppTab.settings) {
                SettingsView()
            }
        }
        .tint(.brand)
        .tabViewStyle(.sidebarAdaptable)
        .enableInjection()
    }

    private var legacyTabView: some View {
        TabView(selection: $selectedTab) {
            DashboardView(selectedTab: $selectedTab)
                .tabItem {
                    Label("Dashboard", systemImage: "house.fill")
                }
                .tag(AppTab.dashboard)

            LibraryView()
                .tabItem {
                    Label("Library", systemImage: "books.vertical.fill")
                }
                .tag(AppTab.library)

            PuzzleTabView()
                .tabItem {
                    Label("Puzzle", systemImage: "puzzlepiece.extension.fill")
                }
                .tag(AppTab.puzzle)

            FeedPlaceholderTabView()
                .tabItem {
                    Label("Feed", systemImage: "newspaper.fill")
                }
                .tag(AppTab.feed)

            SettingsView()
                .tabItem {
                    Label("Settings", systemImage: "gearshape")
                }
                .tag(AppTab.settings)
        }
        .tint(.brand)
        .enableInjection()
    }
}

/// Feed tab placeholder. Mirrors Android's `FeedScreen.kt` placeholder
/// inline rather than reusing [FeedPlaceholderCard] because the tab
/// is dedicated to the feed — no greeting/stats above it.
/// Uses the same scrollable chrome pattern as [DashboardView] so the
/// nav bar material matches across tabs.
private struct FeedPlaceholderTabView: View {
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    Text("\u{1F4F0}")
                        .font(.livtetHeading(size: 48))
                    Text("Feed")
                        .font(.livtetHeading(size: 24, weight: .semibold))
                        .foregroundStyle(Color("textQuiet"))
                    Text("Coming soon")
                        .font(.livtetBody(size: 14))
                        .foregroundStyle(Color("textQuiet").opacity(0.6))
                    Text("Social features and recommendations are on the way.")
                        .font(.livtetBody(size: 12))
                        .foregroundStyle(Color("textQuiet").opacity(0.4))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(.horizontal, 16)
                .padding(.vertical, 24)
            }
            .background(Color("surfaceDefault").ignoresSafeArea())
            .navigationTitle("Feed")
        }
    }
}

#if DEBUG
#Preview {
    RootTabView()
}
#endif
