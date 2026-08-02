import FastULID
import LivtetKitFFI
import SwiftUI

/// A single edition row in the Library list.
///
/// Flat display of each edition — no work grouping. Shows edition
/// title, ISBN, published date, page count, cover image, and a
/// cover source badge when the cover is set manually.
struct EditionRow: View {
    let edition: Edition

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            if let coverPath = edition.coverPath, let url = URL(string: "file://\(coverPath)") {
                CoverImageView(url: url, width: 48, height: 72)
                    .accessibilityHidden(true)
            } else {
                Color("surfaceHighlighted")
                    .frame(width: 48, height: 72)
                    .overlay {
                        Image(systemName: "book.closed")
                            .font(.livtetHeading(size: 20))
                            .foregroundStyle(Color("textQuiet").opacity(0.4))
                    }
                    .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.s, style: .continuous))
                    .accessibilityHidden(true)
            }

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(edition.workTitle.nilIfEmpty ?? "Untitled")
                        .font(.livtetHeading(size: 14, weight: .semibold))
                        .foregroundStyle(Color("textNormal"))
                        .lineLimit(2)

                    if edition.coverSource == "manual" {
                        Text("manual")
                            .font(.livtetBody(size: 9))
                            .foregroundStyle(Color("semanticWarningForeground"))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(
                                RoundedRectangle(cornerRadius: 4, style: .continuous)
                                    .fill(Color("semanticWarningBackground"))
                            )
                    }
                }

                if let edTitle = edition.editionTitle.nilIfEmpty {
                    Text(edTitle)
                        .font(.livtetBody(size: 12))
                        .foregroundStyle(Color("textQuiet"))
                        .lineLimit(1)
                }

                if let isbn = edition.isbn {
                    Text("ISBN: \(isbn)")
                        .font(.livtetBody(size: 12))
                        .foregroundStyle(Color("textQuiet"))
                        .lineLimit(1)
                }

                HStack(spacing: 8) {
                    if let publishedDate = edition.publishedDate {
                        Text(publishedDate)
                            .font(.livtetBody(size: 12))
                            .foregroundStyle(Color("textQuiet"))
                            .lineLimit(1)
                    }

                    if let pageCount = edition.pageCount, pageCount > 0 {
                        Text("\(pageCount)p")
                            .font(.livtetBody(size: 12))
                            .foregroundStyle(Color("textQuiet"))
                            .lineLimit(1)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous)
                .fill(Color("surfaceHighlighted"))
        )
        .accessibilityLabel(edition.workTitle.nilIfEmpty ?? edition.editionTitle.nilIfEmpty ?? "Edition")
        .accessibilityHint("Tap to view edition details")
    }
}

private extension Optional where Wrapped == String {
    var nilIfEmpty: String? {
        guard let self, !self.isEmpty else { return nil }
        return self
    }
}

#if DEBUG
#Preview {
    EditionRow(
        edition: Edition(
            id: ULID(ulidData: Data(repeating: 0, count: 16))!,
            workId: ULID(ulidData: Data(repeating: 0, count: 16))!,
            workTitle: "Beloved",
            editionTitle: "First Paperback Edition",
            isbn: "978-0-06-112008-4",
            publishedDate: "1987-09-02",
            pageCount: 324,
            formatId: nil,
            languageId: nil,
            notes: nil,
            description: nil,
            createdAt: "2024-01-01T00:00:00Z",
            updatedAt: nil,
            inventoryId: nil,
            coverPath: nil,
            coverSource: "manual"
        )
    )
    .padding()
    .background(Color("surfaceDefault"))
}
#endif
