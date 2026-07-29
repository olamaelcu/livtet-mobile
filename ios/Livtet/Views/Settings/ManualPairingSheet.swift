import SwiftUI

/// Modal sheet for manual device pairing. Used when mDNS discovery
/// isn't available (CI, sandboxed networks, restricted LANs) or the
/// user prefers to enter the device's address directly.
///
/// Supports QR code scanning via `QRScannerView`. When a QR code
/// containing a `livtet://sync?ip[]=...` URL is scanned, the form is
/// auto-filled. If multiple IPs are embedded in the URL (via repeated
/// `ip[]` query parameters), the user is prompted to pick one.
///
/// Calls `onPair` with the form values. The pairing logic itself
/// (which writes to `paired_devices`) lives in the Settings view
/// via the `SyncBridge` protocol — this sheet just collects input.
struct ManualPairingSheet: View {
    @Environment(\.dismiss) private var dismiss

    let networkAddresses: [String]
    let prefillAddress: String?
    let prefillPort: String?
    let onPair: (_ name: String, _ address: String, _ port: Int32, _ deviceType: String) -> Void

    @State private var name: String = "Desktop"
    @State private var address: String
    @State private var portText: String
    @State private var deviceType: String = "desktop"
    @State private var addressError: String?

    @State private var showScanner = false
    @State private var showAddressPicker = false
    @State private var pickerAddresses: [String] = []
    @State private var scannedHistory: [String] = []

    init(
        networkAddresses: [String] = [],
        prefillAddress: String? = nil,
        prefillPort: String? = nil,
        onPair: @escaping (_ name: String, _ address: String, _ port: Int32, _ deviceType: String) -> Void
    ) {
        self.networkAddresses = networkAddresses
        self.prefillAddress = prefillAddress
        self.prefillPort = prefillPort
        self.onPair = onPair
        self._address = State(initialValue: prefillAddress ?? "")
        self._portText = State(initialValue: prefillPort ?? "3120")
    }

    /// Pairs the address field with the suggested interface.
    private func applyAddress(_ candidate: String) {
        address = candidate
        addressError = nil
    }

    private var port: Int32? {
        Int32(portText)
    }

    private var canPair: Bool {
        guard !address.isEmpty, let p = port, p > 0, p < 65536 else { return false }
        return !name.isEmpty
    }

    private func submit() {
        addressError = validateAddress(address)
        guard addressError == nil, let p = port else { return }
        onPair(name, address, p, deviceType)
        dismiss()
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Device") {
                    TextField("Name", text: $name)
                    TextField("Address (e.g. 192.168.1.42)", text: $address)
                        .keyboardType(.numbersAndPunctuation)
                        .autocorrectionDisabled()
                        .onChange(of: address) { _, new in
                            addressError = validateAddress(new)
                        }
                    if let err = addressError {
                        Text(err)
                            .font(.caption)
                            .foregroundColor(Color("semanticDangerForeground"))
                    }
                    TextField("Port", text: $portText)
                        .keyboardType(.numberPad)
                    TextField("Device type", text: $deviceType)
                        .autocorrectionDisabled()
                }

                if !networkAddresses.isEmpty {
                    Section("Local interfaces (tap to fill)") {
                        ForEach(networkAddresses, id: \.self) { addr in
                            Button(addr) {
                                applyAddress(addr)
                            }
                            .foregroundColor(Color("textLink"))
                        }
                    }
                }

                if !scannedHistory.isEmpty {
                    Section("Recently scanned") {
                        ForEach(scannedHistory, id: \.self) { addr in
                            Button(addr) {
                                applyAddress(addr)
                            }
                            .foregroundColor(Color("textLink"))
                        }
                    }
                }

                Section {
                    Button {
                        submit()
                    } label: {
                        HStack {
                            Spacer()
                            Text("Pair")
                                .fontWeight(.semibold)
                            Spacer()
                        }
                    }
                    .disabled(!canPair)

                    Button {
                        showScanner = true
                    } label: {
                        HStack {
                            Spacer()
                            Image(systemName: "qrcode.viewfinder")
                            Text("Scan QR Code")
                            Spacer()
                        }
                    }
                }
            }
            .navigationTitle("Pair New Device")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .fullScreenCover(isPresented: $showScanner) {
                QRScannerView(
                    onScan: { scanned in handleScan(scanned) },
                    onCancel: { showScanner = false }
                )
            }
            .alert("Select an address", isPresented: $showAddressPicker, presenting: pickerAddresses) { addrs in
                ForEach(addrs, id: \.self) { addr in
                    Button(addr) { fillForm(address: addr, allAddresses: addrs) }
                }
                Button("Cancel", role: .cancel) {}
            } message: { _ in
                Text("The pairing URL contains multiple network addresses. Choose the one that matches your connection.")
            }
        }
    }

    private func handleScan(_ scanned: String) {
        showScanner = false
        guard let url = URL(string: scanned),
              let parsed = try? url.parseLivtetSyncURL()
        else { return }

        if parsed.addresses.count > 1 {
            pickerAddresses = parsed.addresses
            showAddressPicker = true
        } else {
            fillForm(address: parsed.ip, allAddresses: parsed.addresses)
        }

        portText = String(parsed.port)
        name = "Desktop (\(parsed.ip))"
    }

    private func fillForm(address chosen: String, allAddresses: [String]) {
        address = chosen
        addressError = nil
        for addr in allAddresses where !scannedHistory.contains(addr) {
            scannedHistory.append(addr)
        }
    }
}

/// Validates an IPv4 address string. Returns nil on success, an
/// error message on failure. iOS NSDManager gives us resolved IPs,
/// so manual entry here is the path for users who want to type
/// directly (e.g. testing, or for an off-LAN desktop).
private func validateAddress(_ addr: String) -> String? {
    let trimmed = addr.trimmingCharacters(in: .whitespaces)
    if trimmed.isEmpty { return "Address is required" }
    let parts = trimmed.split(separator: ".")
    guard parts.count == 4 else {
        return "Must be an IPv4 address (x.x.x.x)"
    }
    for p in parts {
        guard let n = Int(p), (0...255).contains(n) else {
            return "Invalid octet: \(p)"
        }
    }
    return nil
}

#if DEBUG
#Preview {
    ManualPairingSheet(
        networkAddresses: ["127.0.0.1", "192.168.1.42", "fe80::1"],
        onPair: { _, _, _, _ in }
    )
}
#endif
