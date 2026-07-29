import FastULID
import LivtetKit
import LivtetKitFFI
import os
import SwiftUI

/// "Continue Reading" card — solid brand-tinted background with the
/// title, optional author name, a progress bar, and a percent label.
///
/// Mirrors Android's `ContinueReadingCard` in `DashboardScreen.kt`.
struct ContinueReadingCard: View {
    private let logger = Logger(subsystem: "net.olamaelcu.livtet", category: "Dashboard")

    let book: RecentlyReadBook

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Continue Reading")
                .font(.livtetBody(size: 12, weight: .medium))
                .foregroundStyle(Color.white.opacity(0.85))

            Text(book.title)
                .font(.livtetHeading(size: 18, weight: .semibold))
                .foregroundStyle(Color.white)

            if let authorName = book.authorName {
                Text(authorName)
                    .font(.livtetBody(size: 12))
                    .foregroundStyle(Color.white.opacity(0.7))
            }

            ProgressView(value: book.progress)
                .progressViewStyle(.linear)
                .tint(Color.white)
                .padding(.top, 12)
                .accessibilityLabel("Reading progress")
                .accessibilityValue("\(Int(book.progress * 100))% complete")

            Text("\(Int(book.progress * 100))% complete")
                .font(.livtetBody(size: 11))
                .foregroundStyle(Color.white.opacity(0.7))
                .padding(.top, 4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color.brand)
        .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous))
        .onTapGesture {
            logger.debug("ContinueReadingCard tapped for book: \(book.title, privacy: .public)")
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Continue Reading \(book.title)")
        .accessibilityHint("Tap to continue reading your book")
    }
}

#if DEBUG
#Preview {
    let workId = ULID(ulidData: Data(repeating: 0, count: 16))!
    let editionId = ULID(ulidData: Data(repeating: 0, count: 16))!
    ContinueReadingCard(book: RecentlyReadBook(
        workId: workId,
        editionId: editionId,
        title: "Beloved",
        authorName: "Toni Morrison",
        progress: 0.42,
        totalReadingTimeSecs: 3600,
        lastReadAt: "2026-07-12T00:00:00Z"
    ))
    .padding()
    .background(Color("surfaceDefault"))
}
#endif
