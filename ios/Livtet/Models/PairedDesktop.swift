import Foundation

struct PairedDesktop: Identifiable, Codable, Equatable {
    let id: String
    var name: String
    var host: String
    var port: UInt16
    var sessionToken: String
    var lastSeenAt: Date
}
