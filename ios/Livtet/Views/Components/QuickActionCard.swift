import SwiftUI

/// Tappable progress card on the Dashboard. Mirrors Android's
/// `QuickActionCard` — a Lucide icon, title, description, an optional
/// progress bar, and a chevron affordance on the trailing edge.
///
/// Pass `progressLabel: nil` to hide the progress meter (used for the
/// "Finish a Book" card, which has no measurable quota).
struct QuickActionCard: View {
    let icon: Image
    let title: String
    let description: String
    let progress: Double?
    let progressLabel: String?
    let tintColor: Color
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .center, spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: LivtetRadius.s, style: .continuous)
                        .fill(tintColor.opacity(0.50))
                        .frame(width: 40, height: 40)
                    icon
                        .resizable()
                        .scaledToFit()
                        .frame(width: 24, height: 24)
                        .foregroundStyle(tintColor.opacity(0.70))
                        .backgroundStyle(tintColor)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.livtetBody(size: 14, weight: .semibold))
                        .foregroundStyle(Color("textNormal"))
                    Text(description)
                        .font(.livtetBody(size: 12))
                        .foregroundStyle(Color("textQuiet"))

                    if let progress, let progressLabel {
                        HStack(spacing: 8) {
                            ProgressView(value: progress)
                                .progressViewStyle(.linear)
                                .tint(tintColor)
                            Text(progressLabel)
                                .font(.livtetCode(size: 11))
                                .foregroundStyle(Color("textQuiet"))
                        }
                        .padding(.top, 6)
                    }
                }

                Spacer(minLength: 8)

                Text("\u{203A}")
                    .font(.livtetHeading(size: 18, weight: .medium))
                    .foregroundStyle(Color("textQuiet").opacity(0.4))
            }
            .padding(12)
            .background(Color("surfaceRaised"))
            .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
        .accessibilityHint(description)
    }
}

#if DEBUG
#Preview {
    VStack(spacing: 8) {
        QuickActionCard(
            icon: Image.lucideBookPlus,
            title: "Add Your First Book",
            description: "Build your library to get started",
            progress: 0.3,
            progressLabel: "3/10 books",
            tintColor: Color("semanticSuccessForeground"),
            onTap: {}
        )
        QuickActionCard(
            icon: Image.lucideBookOpen,
            title: "Record Your Reading",
            description: "Keep your reading streak going",
            progress: 0.5,
            progressLabel: "7/14 days",
            tintColor: Color("semanticWarningForeground"),
            onTap: {}
        )
        QuickActionCard(
            icon: Image.lucideCircleCheck,
            title: "Finish a Book",
            description: "Complete a book to make progress",
            progress: nil,
            progressLabel: nil,
            tintColor: Color("semanticInformationalForeground"),
            onTap: {}
        )
    }
    .padding()
    .background(Color("surfaceDefault"))
}
#endif
