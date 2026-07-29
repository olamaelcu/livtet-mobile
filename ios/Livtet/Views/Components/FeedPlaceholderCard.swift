import SwiftUI

/// "Coming Soon" placeholder for the social feed. Mirrors Android's
/// `FeedPlaceholderCard`. Renders a newspaper emoji, the section name,
/// a one-line description, and a faint "Coming Soon" label.
struct FeedPlaceholderCard: View {
    var body: some View {
        VStack(spacing: 8) {
            Text("\u{1F4F0}")
                .font(.system(size: 32))
                .accessibilityHidden(true)

            Text("Feed")
                .font(.livtetHeading(size: 18, weight: .semibold))
                .foregroundStyle(Color("textQuiet"))

            Text(
                "Friend activity and recommendations will appear here once the social feed is ready."
            )
            .font(.livtetBody(size: 12))
            .foregroundStyle(Color("textQuiet").opacity(0.7))
            .multilineTextAlignment(.center)
            .padding(.horizontal, 16)

            Text("Coming Soon")
                .font(.livtetBody(size: 12, weight: .medium))
                .foregroundStyle(Color("textQuiet").opacity(0.5))
                .padding(.top, 4)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
        .background(Color("surfaceRaised"))
        .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Feed")
        .accessibilityHint("Friend activity and recommendations will appear here once the social feed is ready")
    }
}

#if DEBUG
#Preview {
    FeedPlaceholderCard()
        .padding()
        .background(Color("surfaceDefault"))
}
#endif
