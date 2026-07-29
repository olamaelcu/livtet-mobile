import Foundation
import LivtetKitFFI

enum AppError: LocalizedError, Equatable {
    case database(String)
    case notFound(String)
    case initFailed(String)
    case platform(String)
    case network(String)
    case bridge(String)
    case sync(String)
    case keychain(String)
    case decoding(String)
    case unknown(String)

    var errorDescription: String? {
        switch self {
        case let .database(msg),
             let .notFound(msg),
             let .initFailed(msg),
             let .platform(msg),
             let .network(msg),
             let .bridge(msg),
             let .sync(msg),
             let .keychain(msg),
             let .decoding(msg),
             let .unknown(msg):
            return msg
        }
    }

    static func from(ffi: MobileError) -> AppError {
        switch ffi {
        case let .Database(msg): return .database(msg)
        case let .NotFound(msg): return .notFound(msg)
        case let .Init(msg): return .initFailed(msg)
        case let .Platform(msg): return .platform(msg)
        case let .Network(msg): return .network(msg)
        case let .ProviderError(category: category, retryAfterSeconds: retryAfterSeconds, providerId: providerId):
            let retry = retryAfterSeconds.map { " retry=\($0)s" } ?? ""
            return .network("provider=\(providerId) category=\(category)\(retry)")
        case let .IsbnConflict(workId, _, conflictingIsbn):
            return .database("ISBN conflict on work \(workId): \(conflictingIsbn)")
        case .RegistryLocked:
            return .database("Registry is locked")
        case let .SaveRolledBack(detail):
            return .database("Save rolled back: \(detail)")
        }
    }
}
