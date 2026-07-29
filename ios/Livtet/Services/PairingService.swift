import Foundation
import LivtetKitFFI
import UIKit

enum PairingError: LocalizedError {
    case invalidURL
    case networkError(String)
    case desktopUnreachable
    case pairingRejected
    case tokenPersistenceFailed

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid pairing URL"
        case let .networkError(msg):
            return msg
        case .desktopUnreachable:
            return "Desktop is unreachable"
        case .pairingRejected:
            return "Pairing was rejected"
        case .tokenPersistenceFailed:
            return "Failed to save pairing token"
        }
    }
}

protocol PairingServiceProtocol {
    func pair(with url: URL) async throws -> PairedDesktop
}

final class PairingService: PairingServiceProtocol {
    private let keychain: KeychainService
    private let deviceIdentity: DeviceIdentityService

    init(keychain: KeychainService = .shared, deviceIdentity: DeviceIdentityService = .shared) {
        self.keychain = keychain
        self.deviceIdentity = deviceIdentity
    }

    func pair(with url: URL) async throws -> PairedDesktop {
        let parsed: LivtetSyncURL
        do {
            guard let result = try url.parseLivtetSyncURL() else {
                throw PairingError.invalidURL
            }
            parsed = result
        } catch is SyncURLParseError {
            throw PairingError.invalidURL
        }

        let deviceId = deviceIdentity.deviceId
        let deviceName = await MainActor.run { UIDevice.current.name }

        let config = SyncConfig(
            host: parsed.ip,
            port: parsed.port,
            deviceId: deviceId,
            deviceName: deviceName,
            sessionToken: parsed.token
        )

        do {
            try await Task.detached {
                try pairWithDesktop(config: config)
            }.value
        } catch let error as MobileError {
            throw PairingError.networkError(AppError.from(ffi: error).errorDescription ?? error.localizedDescription)
        }

        do {
            try keychain.store(key: "session_token", value: parsed.token)
        } catch {
            throw PairingError.tokenPersistenceFailed
        }

        return PairedDesktop(
            id: parsed.desktopId,
            name: deviceName,
            host: parsed.ip,
            port: parsed.port,
            sessionToken: parsed.token,
            lastSeenAt: Date()
        )
    }
}
