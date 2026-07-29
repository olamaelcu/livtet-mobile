import Foundation
import LivtetKitFFI
import os.log

enum FFIErrorBridge {
    private static let logger = os.Logger(subsystem: "net.olamaelcu.livtet", category: "FFI")

    static func wrap<T>(_ block: () throws -> T, context: String) throws -> T {
        do {
            return try block()
        } catch let error as MobileError {
            let appError = AppError.from(ffi: error)
            logger.error("\(context, privacy: .public) failed: \(appError.localizedDescription, privacy: .public)")
            throw appError
        } catch let error as AppError {
            logger.error("\(context, privacy: .public) failed: \(error.localizedDescription, privacy: .public)")
            throw error
        } catch {
            let appError = AppError.unknown(error.localizedDescription)
            logger.error("\(context, privacy: .public) failed: \(appError.localizedDescription, privacy: .public)")
            throw appError
        }
    }

    static func wrap<T>(_ block: () async throws -> T, context: String) async throws -> T {
        do {
            return try await block()
        } catch let error as MobileError {
            let appError = AppError.from(ffi: error)
            logger.error("\(context, privacy: .public) failed: \(appError.localizedDescription, privacy: .public)")
            throw appError
        } catch let error as AppError {
            logger.error("\(context, privacy: .public) failed: \(error.localizedDescription, privacy: .public)")
            throw error
        } catch {
            let appError = AppError.unknown(error.localizedDescription)
            logger.error("\(context, privacy: .public) failed: \(appError.localizedDescription, privacy: .public)")
            throw appError
        }
    }
}
