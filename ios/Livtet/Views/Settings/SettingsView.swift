import Combine
import Inject
import SwiftUI
import LivtetKitFFI

extension LivtetKitFFI.PairedDeviceMobile: Identifiable {
    public var id: String { deviceId.hexString }
}

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
/// - a "Discovered on this network" section fed by ``DiscoveryService``
/// - a "Paired Devices" section fed by ``LivtetCoreBridge.getPairedDevices()``
/// - an About section with the app version
///
/// ## Integration with RootTabView
///
/// To wire this view into the app, add a `AppTab.settings` to ``AppTab``
/// (in ``RootTabView.swift``) and insert the corresponding `Tab(...)` / `.tabItem`.
struct SettingsView: View {
    @ObservedObject var syncManager = SyncManager.shared

    @State private var discoveredDesktops: [Desktop] = []
    @State private var pairedDevices: [PairedDeviceMobile] = []
    @State private var pairedDevicesError: String?
    @State private var showManualPairSheet = false
    @State private var manualPrefillAddress: String?
    @State private var manualPrefillPort: String?
    @State private var showOverdrivePicker = false
    @State private var overdriveLibraryCode: String = ""

    @State private var cancellables = Set<AnyCancellable>()
    @State private var discoveryService: DiscoveryService?

    @ObserveInjection var forceRedraw

    private let pluginBridge: PluginBridge

    init(pluginBridge: PluginBridge = LivtetPluginBridgeAdapter()) {
        self.pluginBridge = pluginBridge
    }

    var body: some View {
        NavigationStack {
            Form {
                pluginsSection
                syncSection
                discoveredSection
                pairedDevicesSection
                aboutSection
            }
            .scrollContentBackground(.hidden)
            .background(Color("surfaceDefault").ignoresSafeArea())
            .onChange(of: overdriveLibraryCode) { savePluginSettings() }
            .navigationTitle("Settings")
            .toolbar {
                if case .disconnected = syncManager.state {
                    ToolbarItem(placement: .primaryAction) {
                        Button("Pair") {
                            manualPrefillAddress = nil
                            manualPrefillPort = nil
                            showManualPairSheet = true
                        }
                    }
                }
            }
            .sheet(isPresented: $showManualPairSheet) {
                ManualPairingSheet(
                    networkAddresses: discoveredDesktops.map { $0.host },
                    prefillAddress: manualPrefillAddress,
                    prefillPort: manualPrefillPort
                ) { name, address, port, deviceType in
                    do {
                        try LivtetCoreBridge.pairDevice(
                            name: name,
                            address: address,
                            port: port,
                            deviceType: deviceType
                        )
                        reloadPairedDevices()
                    } catch {
                        pairedDevicesError = "Pairing failed: \(error.localizedDescription)"
                    }
                }
            }
            .sheet(isPresented: $showOverdrivePicker) {
                OverdriveLibraryPicker(selectedCode: $overdriveLibraryCode)
            }
            .task { startDiscovery() }
            .onAppear { reloadPairedDevices(); loadPluginSettings() }
            .onDisappear { stopDiscovery() }
        }
        .enableInjection()
    }

    // MARK: - Plugins Section

    private var pluginsSection: some View {
        Section("Plugins") {
            Button {
                showOverdrivePicker = true
            } label: {
                HStack {
                    Text("Overdrive Library")
                        .foregroundColor(Color("textNormal"))
                    Spacer()
                    if let lib = OverdriveLibraryLoader.load().first(where: { $0.code == overdriveLibraryCode }) {
                        Text(lib.name)
                            .foregroundColor(Color("textQuiet"))
                            .font(.livtetBody(size: 13))
                            .lineLimit(1)
                    } else {
                        Text("Not set")
                            .foregroundColor(Color("textQuiet"))
                            .font(.livtetBody(size: 13))
                    }
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundColor(Color("textQuiet").opacity(0.5))
                }
            }
            .buttonStyle(.plain)
        }
        .listRowBackground(Color("surfaceRaised"))
    }

    private func loadPluginSettings() {
        do {
            overdriveLibraryCode = try pluginBridge.pluginGetSetting(
                pluginId: "overdrive", key: "library_id"
            ) ?? ""
        } catch {
            overdriveLibraryCode = ""
        }
    }

    private func savePluginSettings() {
        do {
            if overdriveLibraryCode.isEmpty {
                // Can't delete settings via the FFI; just skip
                return
            }
            try pluginBridge.pluginSaveSetting(
                pluginId: "overdrive", key: "library_id", value: overdriveLibraryCode
            )
        } catch {}
    }

    // MARK: - Sync Section

    @ViewBuilder
    private var syncSection: some View {
        Section("Sync") {
            SyncStatusView(syncManager: syncManager)
                .accessibilityLabel("Sync status")

            if syncManager.hasSavedSession {
                Button("Disconnect", role: .destructive) {
                    syncManager.disconnect()
                }
                .foregroundColor(Color("semanticDangerForeground"))
                .accessibilityLabel("Disconnect")
                .accessibilityHint("Disconnect from the paired desktop device")
            }
        }
        .listRowBackground(Color("surfaceRaised"))
    }

    // MARK: - Discovered Section

    @ViewBuilder
    private var discoveredSection: some View {
        Section("Discovered on this network") {
            if discoveredDesktops.isEmpty {
                Text("No desktops found yet\u{2026}")
                    .font(.livtetBody(size: 13))
                    .foregroundColor(Color("textQuiet"))
                    .accessibilityLabel("No desktops found")
                    .accessibilityHint("Ensure the Livtet desktop app is running and on the same network")
            } else {
                ForEach(discoveredDesktops, id: \.host) { desktop in
                    Button {
                        manualPrefillAddress = desktop.host
                        manualPrefillPort = String(desktop.port)
                        showManualPairSheet = true
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(desktop.name)
                                .font(.livtetBody(size: 14))
                                .foregroundColor(Color("textNormal"))
                            Text("\(desktop.host):\(desktop.port)")
                                .font(.livtetBody(size: 12))
                                .foregroundColor(Color("textQuiet"))
                        }
                    }
                    .listRowBackground(Color("surfaceRaised"))
                    .accessibilityLabel("\(desktop.name)")
                    .accessibilityHint("Tap to pair with \(desktop.name) at \(desktop.host):\(desktop.port)")
                }
            }
        }
        .listRowBackground(Color("surfaceRaised"))
    }

    // MARK: - Paired Devices Section

    @ViewBuilder
    private var pairedDevicesSection: some View {
        Section("Paired Devices") {
            if let error = pairedDevicesError {
                Text(error)
                    .font(.livtetBody(size: 12))
                    .foregroundColor(Color("semanticDangerForeground"))
                    .listRowBackground(Color("surfaceRaised"))
                    .accessibilityLabel("Error")
            } else if pairedDevices.isEmpty {
                Text("No paired devices.")
                    .font(.livtetBody(size: 13))
                    .foregroundColor(Color("textQuiet"))
                    .listRowBackground(Color("surfaceRaised"))
                    .accessibilityLabel("No paired devices")
            } else {
                ForEach(pairedDevices) { device in
                    pairedDeviceRow(device)
                }
            }
        }
        .listRowBackground(Color("surfaceRaised"))
    }

    @ViewBuilder
    private func pairedDeviceRow(_ device: PairedDeviceMobile) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(device.name ?? device.id)
                    .font(.livtetBody(size: 14))
                    .foregroundColor(Color("textNormal"))
                Spacer()
                if !device.deviceType.isEmpty {
                    Text(device.deviceType)
                        .font(.livtetBody(size: 11))
                        .foregroundColor(Color("textQuiet"))
                }
            }

            Text("Listens on \(device.listenOn)")
                .font(.livtetBody(size: 12))
                .foregroundColor(Color("textQuiet"))

            HStack {
                if let lastSync = device.lastSyncAt {
                    Text("Last sync \(lastSync)")
                        .font(.livtetBody(size: 11))
                        .foregroundColor(Color("textQuiet"))
                }
                Spacer()
                Button("Unpair", role: .destructive) {
                    unpair(device)
                }
                .font(.livtetBody(size: 12))
                .foregroundColor(Color("semanticDangerForeground"))
                .accessibilityLabel("Unpair \(device.name ?? device.id)")
                .accessibilityHint("Remove this device from your paired devices list")
            }
        }
        .listRowBackground(Color("surfaceRaised"))
        .accessibilityElement(children: .combine)
        .accessibilityLabel(device.name ?? device.id)
        .accessibilityHint("Paired device, tap to view details")
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

    // MARK: - Discovery lifecycle

    private func startDiscovery() {
        let service = DiscoveryService()
        discoveryService = service
        service.desktops
            .receive(on: DispatchQueue.main)
            .assign(to: \.discoveredDesktops, on: self)
            .store(in: &cancellables)
        reloadPairedDevices()
    }

    private func stopDiscovery() {
        discoveryService?.stop()
        discoveryService = nil
        cancellables.removeAll()
    }

    // MARK: - Paired devices

    private func reloadPairedDevices() {
        guard LivtetCoreBridge.isSyncPoolReady else {
            pairedDevicesError = "Sync pool not initialized, please restart the app"
            pairedDevices = []
            return
        }
        do {
            pairedDevices = try LivtetCoreBridge.getPairedDevices()
            pairedDevicesError = nil
        } catch {
            pairedDevices = []
            pairedDevicesError = "Could not load paired devices: \(error.localizedDescription)"
        }
    }

    private func unpair(_ device: PairedDeviceMobile) {
        do {
            try LivtetCoreBridge.unpairDevice(deviceId: device.deviceId)
            reloadPairedDevices()
        } catch {
            pairedDevicesError = "Unpair failed: \(error.localizedDescription)"
        }
    }
}

#if DEBUG
#Preview {
    SettingsView()
}
#endif
