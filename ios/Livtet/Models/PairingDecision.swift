import Foundation

struct PairingDecision: Codable {
    let event: String
    let deviceId: String?
    let sessionToken: String?
}

extension PairingDecision {
    enum CodingKeys: String, CodingKey {
        case event
        case deviceId = "device_id"
        case sessionToken = "session_token"
    }
}
