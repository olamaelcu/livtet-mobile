import SwiftUI

struct SyncStatusView: View {
    @ObservedObject var syncManager: SyncManager

    var body: some View {
        HStack(spacing: 8) {
            switch syncManager.state {
            case .disconnected:
                Image(systemName: "link.slash")
                    .accessibilityHidden(true)
                Text("Not paired")
                    .accessibilityLabel("Not paired")
                    .accessibilityHint("Tap to pair with a desktop device")

            case .pairing:
                ProgressView()
                    .progressViewStyle(.circular)
                    .accessibilityLabel("Pairing in progress")
                Text("Pairing\u{2026}")
                    .accessibilityHidden(true)

            case .paired(let desktop):
                Image(systemName: "link")
                    .accessibilityHidden(true)
                Text("Paired with \(desktop.name)")
                    .accessibilityLabel("Paired with \(desktop.name)")
                    .accessibilityHint("Connected to desktop device")

            case .syncing(let progress):
                ProgressView(value: progress, total: 1.0)
                    .frame(maxWidth: 80)
                    .accessibilityLabel("Sync progress")
                    .accessibilityValue("\(Int(progress * 100)) percent complete")
                Text("Syncing\u{2026}")
                    .accessibilityHidden(true)

            case .error(let message):
                Image(systemName: "exclamationmark.triangle")
                    .accessibilityHidden(true)
                Text(message)
                    .accessibilityLabel("Sync error")
                    .accessibilityHint("An error occurred during sync")
            }
        }
        .foregroundColor(Color("textNormal"))
        .font(.livtetBody(size: 13))
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(Color("surfaceRaised"))
        .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.s, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

#if DEBUG
#Preview {
    SyncStatusView(syncManager: SyncManager.shared)
}
#endif
