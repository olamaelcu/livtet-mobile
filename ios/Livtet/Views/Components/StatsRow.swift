import LivtetKit
import LivtetKitFFI
import SwiftUI

/// 2x2 grid of reading-stat pills rendered from [DashboardStats].
///
/// Mirrors Android's `StatsRow`. Stat values are tinted with the brand
/// color so they pop against the `surfaceRaised` card background.
struct StatsRow: View {
    let stats: DashboardStats

    var body: some View {
        VStack(alignment: .center, spacing: 12) {
            Text("Your Reading Stats")
                .font(.livtetBody(size: 12, weight: .medium))
                .foregroundStyle(Color("textQuiet"))
                .frame(maxWidth: .infinity)

            VStack(spacing: 12) {
                HStack(spacing: 12) {
                    stat(value: String(stats.totalBooks), label: "Books")
                    stat(value: String(stats.booksInProgress), label: "In Progress")
                }
                HStack(spacing: 12) {
                    stat(value: String(stats.finishedBooks), label: "Finished")
                    stat(value: formatDuration(stats.totalReadingTimeSecs), label: "Reading")
                }
            }
        }
        .padding(16)
        .background(Color("surfaceRaised"))
        .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Reading Stats")
        .accessibilityHint("Total books: \(stats.totalBooks), in progress: \(stats.booksInProgress), finished: \(stats.finishedBooks), total reading time: \(formatDuration(stats.totalReadingTimeSecs))")
    }

    /// One stat cell: brand-tinted value above a muted label.
    private func stat(value: String, label: String) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.livtetHeading(size: 22, weight: .medium))
                .foregroundStyle(Color.brand)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
            Text(label)
                .font(.livtetBody(size: 11, weight: .regular))
                .foregroundStyle(Color("textQuiet"))
        }
        .frame(maxWidth: .infinity)
    }
}

/// Format a reading-time duration as `"Xh"` if at least one hour,
/// otherwise as `"Ym"`. Matches Android's `formatDuration` helper.
private func formatDuration(_ seconds: Int64) -> String {
    let hours = seconds / 3600
    if hours > 0 {
        return "\(hours)h"
    }
    return "\(seconds / 60)m"
}

#if DEBUG
#Preview {
    StatsRow(stats: DashboardStats(
        totalBooks: 42,
        booksInProgress: 3,
        finishedBooks: 12,
        totalReadingTimeSecs: 9 * 3600 + 12 * 60,
        firstReadingAtMillis: nil
    ))
    .padding()
    .background(Color("surfaceDefault"))
}
#endif
