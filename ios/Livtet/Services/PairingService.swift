import Foundation

/// User-facing pairing failures reported through `SyncManager.state`.
enum PairingError: LocalizedError {
    case invalidURL
    case peerNotFound
    case identityMismatch
    case noClaim
    case rejected
    case timedOut

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "That code is not a Livtet pairing code"
        case .peerNotFound:
            return "Could not find the desktop on this network"
        case .identityMismatch:
            return "That desktop did not match the code you scanned"
        case .noClaim:
            return "The desktop did not accept the pairing request"
        case .rejected:
            return "The pairing was rejected on the desktop"
        case .timedOut:
            return "The desktop did not answer in time"
        }
    }
}
