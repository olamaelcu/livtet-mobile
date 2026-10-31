import Inject
import SwiftUI

// MARK: - Bundle Version Helpers

extension Bundle {
    /// Human-readable version string: `"1.2.3 (456)"`
    var appVersion: String {
        let shortVersion = infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let buildVersion = infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return "\(shortVersion) (\(buildVersion))"
    }
}

// MARK: - SettingsView

/// Settings screen that replaces or augments the existing settings tab.
///
/// Contains:
/// - a sync section (pairing state, disconnect action)
/// - an About section with the app version
///
/// The "Discovered on this network", "Paired Devices" and plugin settings
/// sections were removed together with the UniFFI pairing/plugin APIs: the
/// `LivtetStore` facade does not expose them yet.
///
/// ## Integration with RootTabView
///
/// To wire this view into the app, add a `AppTab.settings` to ``AppTab``
/// (in ``RootTabView.swift``) and insert the corresponding `Tab(...)` / `.tabItem`.
struct SettingsView: View {
    @ObservedObject var syncManager = SyncManager.shared

    @State private var showPairingEntry = false

    @ObserveInjection var forceRedraw

    var body: some View {
        NavigationStack {
            Form {
                syncSection
                aboutSection
            }
            .scrollContentBackground(.hidden)
            .background(Color("surfaceDefault").ignoresSafeArea())
            .navigationTitle("Settings")
            .sheet(isPresented: $showPairingEntry) {
                ManualPairingSheet(syncManager: syncManager)
            }
        }
        .enableInjection()
    }

    // MARK: - Sync Section

    @ViewBuilder
    private var syncSection: some View {
        Section("Sync") {
            SyncStatusView(syncManager: syncManager)
                .accessibilityLabel("Sync status")

            Button {
                showPairingEntry = true
            } label: {
                Label(
                    syncManager.hasSavedSession ? "Pair another desktop" : "Pair a device",
                    systemImage: "qrcode"
                )
            }
            .accessibilityLabel("Pair a device")
            .accessibilityHint("Scan or enter the node ID shown on the desktop")

            if syncManager.hasSavedSession {
                Button("Disconnect", role: .destructive) {
                    syncManager.disconnect()
                }
                .foregroundColor(Color("dangerOnNormal"))
                .accessibilityLabel("Disconnect")
                .accessibilityHint("Disconnect from the paired desktop device")
            }
        }
        .listRowBackground(Color("surfaceRaised"))
    }

    // MARK: - About Section

    private var aboutSection: some View {
        Section("About") {
            HStack {
                Text("Version")
                    .foregroundColor(Color("textNormal"))
                Spacer()
                Text(Bundle.main.appVersion)
                    .foregroundColor(Color("textQuiet"))
                    .font(.livtetBody(size: 13))
            }
        }
        .listRowBackground(Color("surfaceRaised"))
    }
}

#if DEBUG
#Preview {
    SettingsView()
}
#endif
