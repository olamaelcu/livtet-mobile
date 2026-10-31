import SwiftUI

/// Manual pairing entry: a node id (typed, pasted, or scanned) and, when
/// Bonjour is unavailable, the desktop's address.
///
/// The desktop shows a QR code containing either its node id or a
/// `livtet://sync?v=2&nodeId=…&host=…&port=…` link; both are accepted.
struct ManualPairingSheet: View {
    @ObservedObject var syncManager: SyncManager = .shared
    @Environment(\.dismiss) private var dismiss

    @State private var nodeId = ""
    @State private var address = ""
    @State private var portText = String(NetworkClient.defaultPort)
    @State private var showScanner = false
    @State private var validationError: String?

    private var port: UInt16? {
        UInt16(portText).flatMap { $0 == 0 ? nil : $0 }
    }

    private var canPair: Bool {
        PairingInput.isNodeId(nodeId.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Node ID", text: $nodeId)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .font(.system(.footnote, design: .monospaced))
                        .accessibilityLabel("Desktop node ID")
                        .accessibilityHint("The node ID shown in the desktop's Sync settings")

                    Button {
                        showScanner = true
                    } label: {
                        Label("Scan a code", systemImage: "qrcode.viewfinder")
                    }
                    .accessibilityLabel("Scan pairing code")
                } header: {
                    Text("Desktop")
                } footer: {
                    Text("The desktop shows this in Settings → Sync → Pair a device.")
                }

                Section {
                    TextField("Address (optional)", text: $address)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        .accessibilityLabel("Desktop address")

                    TextField("Port", text: $portText)
                        .keyboardType(.numberPad)
                        .accessibilityLabel("Desktop port")
                } header: {
                    Text("Network")
                } footer: {
                    Text("Leave the address blank to find the desktop over Bonjour.")
                }

                if let validationError {
                    Section {
                        Text(validationError)
                            .foregroundColor(Color("dangerOnNormal"))
                    }
                }
            }
            .navigationTitle("Pair with Desktop")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Pair") { pair() }
                        .disabled(!canPair)
                }
            }
            .sheet(isPresented: $showScanner) {
                QRScannerView(
                    onScan: { scanned in
                        showScanner = false
                        apply(scanned)
                    },
                    onCancel: { showScanner = false }
                )
                .ignoresSafeArea()
            }
        }
    }

    /// Fill the form from a scanned code, then pair immediately: the user has
    /// already expressed intent by scanning.
    private func apply(_ scanned: String) {
        guard let target = PairingInput.target(from: scanned) else {
            validationError = PairingError.invalidURL.localizedDescription
            return
        }
        nodeId = target.nodeId
        if let host = target.host {
            address = host
        }
        if let scannedPort = target.port {
            portText = String(scannedPort)
        }
        pair(with: target)
    }

    private func pair() {
        validationError = nil
        guard canPair else {
            validationError = PairingError.invalidURL.localizedDescription
            return
        }
        let trimmedAddress = address.trimmingCharacters(in: .whitespacesAndNewlines)
        var components = URLComponents()
        components.scheme = "livtet"
        components.host = "sync"
        components.queryItems = [
            URLQueryItem(name: "v", value: "2"),
            URLQueryItem(name: "nodeId", value: nodeId.trimmingCharacters(in: .whitespacesAndNewlines))
        ]
        if !trimmedAddress.isEmpty {
            components.queryItems?.append(URLQueryItem(name: "host", value: trimmedAddress))
            if let port {
                components.queryItems?.append(URLQueryItem(name: "port", value: String(port)))
            }
        }
        guard let url = components.url else {
            validationError = PairingError.invalidURL.localizedDescription
            return
        }
        pair(with: PairingInput.target(from: url.absoluteString))
    }

    private func pair(with target: LivtetSyncURL?) {
        guard let target else {
            validationError = PairingError.invalidURL.localizedDescription
            return
        }
        dismiss()
        Task {
            await syncManager.pair(with: target)
        }
    }
}

#if DEBUG
#Preview {
    ManualPairingSheet(syncManager: SyncManager.shared)
}
#endif
