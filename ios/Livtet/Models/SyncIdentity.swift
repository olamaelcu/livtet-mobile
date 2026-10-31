import Foundation

/// `GET /sync/identity`: the identity a peer advertises over HTTP.
///
/// A device that found a peer over Bonjour confirms that the node id it saw in
/// the TXT record is the one this daemon actually holds before claiming.
struct SyncIdentity: Codable, Equatable {
    let nodeId: String
    let deviceId: String

    enum CodingKeys: String, CodingKey {
        case nodeId = "node_id"
        case deviceId = "device_id"
    }
}
