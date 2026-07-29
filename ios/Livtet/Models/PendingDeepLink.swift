import Foundation

struct PendingDeepLink: Codable {
    let desktopId: String
    let address: String
    let port: UInt16
    let token: String
}
