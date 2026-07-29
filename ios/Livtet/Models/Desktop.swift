import Foundation

/// A desktop (or any other sync peer) discovered via Bonjour on the
/// local network. `id` is the stable device identity advertised in
/// the mDNS TXT `id` record; `deviceFlavor` is the platform hint
/// (`"desktop-tauri"`, `"ios-mobile"`, `"android-mobile"`,
/// `"ereader"`); `hostname` is the human-readable short host label.
struct Desktop: Codable {
    let id: String
    let name: String
    let host: String
    let port: UInt16
    let version: String?
    let deviceFlavor: String?
    let hostname: String?

    /// `host:port` formatted for use in the manual-pairing
    /// "listen on" field.
    var listenOn: String { "\(host):\(port)" }
}
