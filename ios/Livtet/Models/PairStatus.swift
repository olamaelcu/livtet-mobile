import Foundation

/// The claim the desktop files for this device.
///
/// `claim` is the secret the status stream requires; it proves the subscriber
/// is the device that asked to pair.
struct PairStatus: Codable {
    let status: String
    let claim: String?
}
