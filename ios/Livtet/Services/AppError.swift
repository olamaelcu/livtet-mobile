import Foundation
import LivtetKit

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

    static func from(ffi: LivtetError) -> AppError {
        switch ffi {
        case let .Database(msg): return .database(msg)
        case let .NotFound(entity: entity, id: id): return .notFound("\(entity) not found (id: \(id))")
        case let .InvalidInput(msg): return .database("Invalid input: \(msg)")
        case let .Search(msg): return .database(msg)
        }
    }
}
