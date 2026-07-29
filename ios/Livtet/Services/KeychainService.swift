import Foundation
import Security

enum KeychainServiceError: LocalizedError {
    case itemNotFound
    case duplicateItem
    case unexpectedData
    case unhandledError(status: OSStatus)

    var errorDescription: String? {
        switch self {
        case .itemNotFound:
            return "Keychain item not found"
        case .duplicateItem:
            return "Keychain duplicate item"
        case .unexpectedData:
            return "Unexpected keychain data format"
        case let .unhandledError(status):
            return "Keychain error: \(status)"
        }
    }
}

final class KeychainService {
    private static let defaultService = "com.livtet.keychain"

    static let shared = KeychainService(service: defaultService)

    private let service: String

    init(service: String) {
        self.service = service
    }

    func store(key: String, value: String, accessGroup: String? = nil) throws {
        guard let data = value.data(using: .utf8) else {
            throw KeychainServiceError.unexpectedData
        }

        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecValueData as String: data
        ]

        if let group = accessGroup {
            query[kSecAttrAccessGroup as String] = group
        }

        SecItemDelete(query as CFDictionary)

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeychainServiceError.unhandledError(status: status)
        }
    }

    func retrieve(key: String) throws -> String {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status != errSecItemNotFound else {
            throw KeychainServiceError.itemNotFound
        }

        guard status == errSecSuccess,
            let data = result as? Data,
            let value = String(data: data, encoding: .utf8) else {
            throw KeychainServiceError.unhandledError(status: status)
        }

        return value
    }

    func delete(key: String) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]

        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainServiceError.unhandledError(status: status)
        }
    }

    func exists(key: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: false,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        let status = SecItemCopyMatching(query as CFDictionary, nil)
        return status == errSecSuccess
    }
}
