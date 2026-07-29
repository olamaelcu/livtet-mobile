import SwiftUI

/// Inline error banner for the dashboard. Mirrors the Android
/// `Text("Could not load dashboard: $error")` row in `DashboardScreen.kt`.
///
/// The banner is non-blocking: the rest of the dashboard paints its
/// last-known state above and below, so the user can still see their
/// library even when one of the four parallel calls fails.
struct ErrorBanner: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image.lucideInfo
                .resizable()
                .scaledToFit()
                .frame(width: 20, height: 20)
                .foregroundStyle(Color("semanticDangerForeground"))
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 4) {
                Text("Could not load dashboard")
                    .font(.livtetBody(size: 12, weight: .semibold))
                    .foregroundStyle(Color("semanticDangerForeground"))
                Text(message)
                    .font(.livtetBody(size: 11))
                    .foregroundStyle(Color("semanticDangerForeground").opacity(0.85))
                    .lineLimit(3)
            }

            Spacer()

            Button("Retry", action: onRetry)
                .font(.livtetBody(size: 12, weight: .semibold))
                .foregroundStyle(Color("semanticDangerForeground"))
                .accessibilityLabel("Retry")
                .accessibilityHint("Attempt to load the dashboard again")
        }
        .padding(12)
        .background(Color("semanticDangerBackground"))
        .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Error: Could not load dashboard")
        .accessibilityHint("An error occurred. Swipe up to retry.")
    }
}

#if DEBUG
#Preview {
    ErrorBanner(message: "Database connection refused on /var/lib/livtet.db") {
        // no-op
    }
    .padding()
    .background(Color("surfaceDefault"))
}
#endif
