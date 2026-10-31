import Foundation

/// A device paired with this instance. `Identifiable` and
/// `Equatable` for SwiftUI use.
struct PairedDeviceRecord: Identifiable, Equatable, Codable {
    /// The desktop-side `DbId` as a string. Acts as the SwiftUI
    /// `id` for list rendering.
    let id: String
    let name: String
    let listenOn: String
    let deviceType: String
    let pairedAt: String
    let lastSyncAt: String?
}

/// A bundled plugin installed on the device.
struct InstalledPluginRecord: Identifiable, Equatable, Codable {
    let id: String
    let pluginId: String
    let name: String
    let version: String
    let enabled: Bool
    let sourcePath: String

    var url: URL? { URL(string: sourcePath) }
}

/// Non-loopback network addresses of the device.
struct NetworkAddressesRecord: Equatable, Codable {
    let addresses: [String]
}
