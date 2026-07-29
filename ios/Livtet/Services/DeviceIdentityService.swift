import Foundation
import Security

final class DeviceIdentityService {
    static let shared = DeviceIdentityService()

    private let keychain = KeychainService.shared
    private let key = "deviceId"

    private static let crockfordAlphabet: [Character] = [
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
        "A", "B", "C", "D", "E", "F", "G", "H", "J", "K",
        "M", "N", "P", "Q", "R", "S", "T", "V", "W", "X",
        "Y", "Z"
    ]

    private init() {}

    var deviceId: String {
        if let existing = try? keychain.retrieve(key: key) {
            return existing
        }
        let newId = generateULID()
        try? keychain.store(key: key, value: newId)
        return newId
    }

    private func generateULID() -> String {
        let timestamp = UInt64(Date().timeIntervalSince1970 * 1000)
        let timePart = encodeBase32(timestamp, length: 10)

        var randomBytes = [UInt8](repeating: 0, count: 16)
        let status = SecRandomCopyBytes(kSecRandomDefault, randomBytes.count, &randomBytes)
        guard status == errSecSuccess else {
            var fallback = ""
            for _ in 0..<16 {
                fallback.append(Self.crockfordAlphabet[Int.random(in: 0..<32)])
            }
            return timePart + fallback
        }

        var randomPart = ""
        for byte in randomBytes {
            randomPart.append(Self.crockfordAlphabet[Int(byte) % 32])
        }

        return timePart + randomPart
    }

    private func encodeBase32(_ value: UInt64, length: Int) -> String {
        var remaining = value
        var chars: [Character] = []
        for _ in 0..<length {
            chars.append(Self.crockfordAlphabet[Int(remaining & 0x1F)])
            remaining >>= 5
        }
        return String(chars.reversed())
    }
}
