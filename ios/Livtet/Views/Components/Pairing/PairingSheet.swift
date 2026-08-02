import SwiftUI

// MARK: - PairingSheet

/// Modal sheet presented when a deep link (`livtet://sync/...`) triggers a
/// pairing handshake or when ``SyncManager.state`` transitions to ``.pairing``.
///
/// Integrators should present this sheet from the root scene:
/// ```swift
/// .onChange(of: syncManager.state) { _, newState in
///     if case .pairing = newState { showPairingSheet = true }
/// }
/// .sheet(isPresented: $showPairingSheet) {
///     PairingSheet(syncManager: syncManager)
/// }
/// ```
struct PairingSheet: View {
    @ObservedObject var syncManager: SyncManager
    @Environment(\.dismiss) var dismiss

    /// Set `true` once pairing succeeds to auto-dismiss.
    @State private var pairedSuccessfully = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Image(systemName: "link.badge.plus")
                    .font(.livtetHeading(size: 48))
                    .foregroundColor(Color("brand"))

                Text("Pair with Desktop")
                    .font(.livtetHeading(size: 20, weight: .semibold))
                    .foregroundColor(Color("textNormal"))

                statusContent
            }
            .padding()
            .navigationTitle("Pairing")
            .navigationBarTitleDisplayMode(.inline)
            .background(Color("surfaceDefault").ignoresSafeArea())
            .onChange(of: syncManager.state) { _, newState in
                if case .paired = newState {
                    pairedSuccessfully = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                        dismiss()
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var statusContent: some View {
        switch syncManager.state {
        case .disconnected:
            VStack(spacing: 16) {
                Text("Ready to pair")
                    .font(.livtetBody(size: 14))
                    .foregroundColor(Color("textNormal"))
                    .accessibilityHidden(true)

                Text("Open the Livtet desktop app and scan the pairing code, or paste a pairing URL.")
                    .font(.livtetBody(size: 12))
                    .foregroundColor(Color("textQuiet"))
                    .multilineTextAlignment(.center)
                    .accessibilityLabel("Pairing instructions")
                    .accessibilityHint("Open the Livtet desktop app, go to Settings > Sync > Pair new device, and scan the QR code or paste the pairing URL shown in the desktop app.")

                Button("Cancel", role: .cancel) {
                    dismiss()
                }
                .accessibilityLabel("Cancel pairing")
            }

        case .pairing:
            VStack(spacing: 16) {
                ProgressView("Connecting\u{2026}")
                    .progressViewStyle(.circular)
                    .accessibilityLabel("Pairing in progress")
                    .accessibilityHint("Please wait while the connection is established with your desktop.")

                Text("Establishing a secure connection with your desktop\u{2026}")
                    .font(.livtetBody(size: 12))
                    .foregroundColor(Color("textQuiet"))
                    .multilineTextAlignment(.center)
                    .accessibilityHidden(true)
            }

        case .paired(let desktop):
            VStack(spacing: 16) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.livtetHeading(size: 48))
                    .foregroundColor(Color("semanticSuccessForeground"))
                    .accessibilityHidden(true)

                Text("Paired successfully!")
                    .font(.livtetHeading(size: 18, weight: .semibold))
                    .foregroundColor(Color("semanticSuccessForeground"))
                    .accessibilityLabel("Paired successfully")

                Text("Connected to \(desktop.name)")
                    .font(.livtetBody(size: 14))
                    .foregroundColor(Color("textNormal"))
                    .accessibilityLabel("Connected to \(desktop.name)")

                if pairedSuccessfully {
                    Text("This sheet will close automatically\u{2026}")
                        .font(.livtetBody(size: 11))
                        .foregroundColor(Color("textQuiet"))
                        .accessibilityLabel("Connection will close automatically")
                }
            }

        case .syncing(let progress):
            VStack(spacing: 16) {
                ProgressView(value: progress, total: 1.0)
                    .accessibilityLabel("Sync progress")
                    .accessibilityValue("\(Int(progress * 100)) percent")
                Text("Syncing library\u{2026}")
                    .font(.livtetBody(size: 13))
                    .foregroundColor(Color("textNormal"))
                    .accessibilityLabel("Syncing library")
            }

        case .error(let message):
            VStack(spacing: 16) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.livtetHeading(size: 40))
                    .foregroundColor(Color("semanticDangerForeground"))
                    .accessibilityHidden(true)

                Text("Pairing Failed")
                    .font(.livtetHeading(size: 18, weight: .semibold))
                    .foregroundColor(Color("textNormal"))
                    .accessibilityLabel("Pairing failed")

                Text(message)
                    .font(.livtetBody(size: 13))
                    .foregroundColor(Color("semanticDangerForeground"))
                    .multilineTextAlignment(.center)
                    .accessibilityLabel("Error message")

                Button("Try Again") {
                    // TODO: Re-trigger pairing via syncManager.startPairing()
                    // when SyncManager exposes that method.
                }
                .buttonStyle(.borderedProminent)
                .accessibilityLabel("Try again")
                .accessibilityHint("Attempt to pair again")

                Button("Cancel", role: .cancel) {
                    dismiss()
                }
                .foregroundColor(Color("textNormal"))
                .accessibilityLabel("Cancel")
            }
        }
    }
}

#if DEBUG
#Preview {
    PairingSheet(syncManager: SyncManager.shared)
}
#endif
