import LivtetKit
import LivtetKitFFI
import SwiftUI

/// Horizontal row of recent-search chips. Mirrors Android's
/// `RecentSearchesRow` in `DashboardScreen.kt`.
///
/// Each chip is a non-interactive label (the dashboard is read-only — the
/// library screen is where the user actually re-runs searches).
struct RecentSearchesRow: View {
    let searches: [RecentSearch]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Recent Searches")
                .font(.livtetBody(size: 12, weight: .medium))
                .foregroundStyle(Color("textQuiet"))

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(searches.prefix(5), id: \.query) { search in
                        Text(search.query)
                            .font(.livtetBody(size: 12))
                            .foregroundStyle(Color("textQuiet"))
                            .lineLimit(1)
                            .truncationMode(.tail)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 6)
                            .background(Color("surfaceRaised"))
                            .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous))
                            .accessibilityLabel(search.query)
                            .accessibilityHint("Search history item")
                    }
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Recent Searches")
        .accessibilityHint("Swipe horizontally to view past searches")
    }
}

#if DEBUG
#Preview {
    RecentSearchesRow(searches: [
        RecentSearch(query: "Morrison", searchedAt: "2026-07-12T00:00:00Z"),
        RecentSearch(query: "Octavia Butler", searchedAt: "2026-07-11T00:00:00Z"),
        RecentSearch(query: "Ralph Ellison", searchedAt: "2026-07-10T00:00:00Z")
    ])
    .padding()
    .background(Color("surfaceDefault"))
}
#endif
