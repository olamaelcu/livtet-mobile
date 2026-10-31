import Foundation

/// User-facing pairing failures.
///
/// The old UniFFI pairing transport was removed when the app moved to the
/// `LivtetStore` facade, which does not expose a pairing or sync API yet.
/// The enum is kept because ``SyncManager`` reports these
/// messages through its `.error` state.
enum PairingError: LocalizedError {
    case invalidURL
    case syncUnavailable

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid pairing URL"
        case .syncUnavailable:
            return "Desktop sync is not available in this build"
        }
    }
}
