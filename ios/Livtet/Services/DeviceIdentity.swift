import Foundation

enum DeviceIdentity {
    private static let key = "ios_device_id"
    private static let keychain = KeychainService(service: "net.olamaelcu.livtet")

    private static let crockfordAlphabet = Array("0123456789ABCDEFGHJKMNPQRSTVWXYZ")

    static var current: String {
        if let existing = try? keychain.retrieve(key: key) {
            return existing
        }
        let newId = generateULID()
        try? keychain.store(key: key, value: newId)
        return newId
    }

    private static func generateULID() -> String {
        let timestamp = UInt64(Date().timeIntervalSince1970 * 1000)
        let timePart = encodeBase32(timestamp, length: 10)

        var randomPart = ""
        for _ in 0..<16 {
            randomPart.append(crockfordAlphabet[Int.random(in: 0..<32)])
        }

        return timePart + randomPart
    }

    private static func encodeBase32(_ value: UInt64, length: Int) -> String {
        var remaining = value
        var chars: [Character] = []
        for _ in 0..<length {
            chars.append(crockfordAlphabet[Int(remaining & 0x1F)])
            remaining >>= 5
        }
        return String(chars.reversed())
    }
}
