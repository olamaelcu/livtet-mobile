import LivtetKit
import SwiftUI

/// A single work row in the Library list.
///
/// Flat display of each work — no edition grouping. Shows the work title and
/// its contributor names, both of which come straight from `WorkSummary`.
struct WorkRow: View {
    let work: WorkSummary

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Color("surfaceDefault")
                .frame(width: 48, height: 72)
                .overlay {
                    Image(systemName: "book.closed")
                        .font(.livtetHeading(size: 20))
                        .foregroundStyle(Color("textQuiet").opacity(0.4))
                }
                .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.s, style: .continuous))
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 2) {
                Text(work.title)
                    .font(.livtetHeading(size: 14, weight: .semibold))
                    .foregroundStyle(Color("textNormal"))
                    .lineLimit(2)

                if !work.authors.isEmpty {
                    Text(work.authors.joined(separator: ", "))
                        .font(.livtetBody(size: 12))
                        .foregroundStyle(Color("textQuiet"))
                        .lineLimit(1)
                }

                if let description = work.description.nilIfEmpty {
                    Text(description)
                        .font(.livtetBody(size: 12))
                        .foregroundStyle(Color("textQuiet"))
                        .lineLimit(2)
                }
            }

            Spacer(minLength: 0)
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(
            work.authors.isEmpty
                ? work.title
                : "\(work.title) by \(work.authors.joined(separator: ", "))"
        )
    }
}

private extension Optional where Wrapped == String {
    var nilIfEmpty: String? {
        guard let self, !self.isEmpty else { return nil }
        return self
    }
}
