import LivtetKitFFI
import SwiftUI

/// A small "card" rendering of a literary quotation for the
/// Library tab's empty state. Shows the quote body in italic body
/// type, the author in semibold, and the source work in italic
/// beneath, in a quiet-but-readable color palette that matches the
/// rest of the app's design system.
///
/// Color and contrast notes (see the commit history for the
/// change): the prior revision used `textQuiet` for the body and
/// `textQuiet.opacity(0.85)` for the source on a
/// `surfaceHighlighted` fill (now `surfaceDefault` after the Web
/// Awesome token alignment) — all three colors in the same
/// low-contrast band, so the card nearly disappeared into the
/// surrounding `surfaceDefault` and the body/source were visually
/// indistinguishable. This revision:
///
/// - body and author lines use `textNormal` (a step darker than
///   `textQuiet`) so the body is comfortably above AA contrast
///   against the card fill.
/// - the source line uses `textQuiet` (no opacity) so the
///   attribution hierarchy reads top-down without fading the
///   source into illegibility.
/// - the card fill is `surfaceRaised` (one step lighter than the
///   merged `surfaceDefault`) so the card has a defined
///   edge against the `surfaceDefault` background.
/// - a 1 pt `surfaceBorder` outline is applied **only in light
///   mode** to give the card a hard silhouette in the appearance
///   where the fills are closest in value. Dark mode is left
///   borderless because the surface-value gap there is already
///   enough to define the card.
///
/// Stacked layout matches the way the source data file is structured
/// (three lines per quote, separated by `===`), and the rounded
/// `LivtetRadius.l` card echoes the `BookRow` treatment so the
/// two surfaces feel like siblings — but the quote card is one
/// step more present (raised fill vs. the row's highlighted fill)
/// because empty-state fillers are ambient, while content rows are
/// user-actionable.
struct EmptyStateQuoteView: View {
    @Environment(\.colorScheme) private var colorScheme

    let message: EmptyMessage

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Quote body
            Text(message.text)
                .font(.livtetBody(size: 14))
                .italic()
                .foregroundStyle(Color("textNormal"))
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)

            // Author and title (work) on a single attribution line:
            // name semibold, comma, title in italic. Putting them on
            // the same line keeps the quote card compact and lets the
            // body read as the dominant element.
            (
                Text(message.author)
                    .font(.livtetBody(size: 12, weight: .semibold))
                    .foregroundStyle(Color("textNormal"))
                + Text(", ")
                    .font(.livtetBody(size: 12))
                    .foregroundStyle(Color("textQuiet"))
                + Text(message.material)
                    .font(.livtetBody(size: 12))
                    .italic()
                    .foregroundStyle(Color("textQuiet"))
            )
            .lineLimit(2)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous)
                .fill(Color("surfaceRaised"))
        )
        .overlay(
            RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous)
                .strokeBorder(
                    Color("surfaceBorder"),
                    lineWidth: colorScheme == .light ? 1 : 0
                )
        )
    }
}

#if DEBUG
#Preview {
    EmptyStateQuoteView(
        message: EmptyMessage(
            text: "I really want some meaning. It used to be easy to toss it off. Now it's harder and harder. You have to navigate just to find something that has nourishment. It's the absence of nourishment. What do you do in place of nourishment? It's usually junk.",
            author: "Toni Morrison",
            material: "Nobel Lecture"
        )
    )
    .padding()
    .background(Color("surfaceDefault"))
}
#endif
