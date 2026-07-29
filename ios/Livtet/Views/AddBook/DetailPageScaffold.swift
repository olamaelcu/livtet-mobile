import SwiftUI

struct DetailPageScaffold<Content: View>: View {
    let title: String
    let onSkip: () -> Void
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Image(systemName: "chevron.left").foregroundStyle(Color("textNormal"))
                    .accessibilityHidden(true)
                Text(title).font(.livtetHeading(size: 16, weight: .semibold))
                Spacer()
            }
            .padding(.horizontal, 16).padding(.vertical, 12)
            .accessibilityElement(children: .combine)
            .accessibilityLabel("Step: \(title)")
            ScrollView { content.padding(.horizontal, 16).padding(.vertical, 16) }
            Divider()
            Button("Skip") { onSkip() }
                .font(.livtetBody(size: 14, weight: .semibold))
                .tint(Color("textQuiet"))
                .frame(maxWidth: .infinity).padding(.vertical, 12)
                .accessibilityLabel("Skip this step")
                .accessibilityHint("Continue to the next step without providing this information")
        }
        .background(Color("surfaceDefault").ignoresSafeArea())
    }
}
