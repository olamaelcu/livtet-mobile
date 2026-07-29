import Foundation

struct LivtetSyncURL {
    let desktopId: String
    // swiftlint:disable:next identifier_name
    let ip: String
    let port: UInt16
    let token: String
    let addresses: [String]
}

enum SyncURLParseError: LocalizedError {
    case missingRequiredParameter(String)
    case invalidFormat(String)

    var errorDescription: String? {
        switch self {
        case let .missingRequiredParameter(param):
            return "Missing required parameter '\(param)'"
        case let .invalidFormat(reason):
            return "Invalid sync URL format: \(reason)"
        }
    }
}

extension URL {
    func parseLivtetSyncURL() throws -> LivtetSyncURL? {
        guard scheme == "livtet", host == "sync" else { return nil }

        guard let components = URLComponents(url: self, resolvingAgainstBaseURL: false),
              let queryItems = components.queryItems
        else {
            throw SyncURLParseError.invalidFormat("Could not parse query items")
        }

        let dict = Dictionary(uniqueKeysWithValues: queryItems.map { ($0.name, $0.value ?? "") })
        let ipArrayItems = queryItems.filter { $0.name == "ip[]" }.compactMap(\.value)

        guard let version = dict["v"], version == "1" else {
            throw SyncURLParseError.invalidFormat("Missing or invalid version")
        }

        guard let desktopId = dict["desktopId"], !desktopId.isEmpty else {
            throw SyncURLParseError.missingRequiredParameter("desktopId")
        }

        guard let portString = dict["port"], let port = UInt16(portString) else {
            throw SyncURLParseError.missingRequiredParameter("port")
        }

        guard let token = dict["token"], !token.isEmpty else {
            throw SyncURLParseError.missingRequiredParameter("token")
        }

        let ulidPattern = try NSRegularExpression(pattern: "^[0-9A-Za-z]{26}$")
        let tokenRange = NSRange(token.startIndex..., in: token)
        guard ulidPattern.firstMatch(in: token, range: tokenRange) != nil else {
            throw SyncURLParseError.invalidFormat("token is not a valid ULID")
        }

        // Support both `ip[]` (new format) and single `ip` (legacy) parameters.
        let addresses: [String]
        let firstIP: String
        if !ipArrayItems.isEmpty {
            addresses = ipArrayItems.map(stripIPv6Brackets)
            firstIP = addresses[0]
        } else if let rawIP = dict["ip"], !rawIP.isEmpty {
            firstIP = stripIPv6Brackets(rawIP)
            addresses = [firstIP]
        } else {
            throw SyncURLParseError.missingRequiredParameter("ip[] or ip")
        }

        return LivtetSyncURL(desktopId: desktopId, ip: firstIP, port: port, token: token, addresses: addresses)
    }

    private func stripIPv6Brackets(_ address: String) -> String {
        if address.hasPrefix("[") && address.hasSuffix("]") {
            return String(address.dropFirst().dropLast())
        }
        return address
    }
}
