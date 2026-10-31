import Foundation

/// A pairing target decoded from `livtet://sync?v=2&…` or from a bare node id.
///
/// Version 2 replaces the single-use token with the peer's iroh node id. The
/// address is optional: when it is absent, the peer is resolved over Bonjour by
/// that id.
struct LivtetSyncURL: Equatable {
    let nodeId: String
    let host: String?
    let port: UInt16?
    let name: String?
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
    /// Decode a `livtet://sync` pairing link, or `nil` when the URL is not one.
    func parseLivtetSyncURL() throws -> LivtetSyncURL? {
        guard scheme == "livtet", host == "sync" else { return nil }

        guard let components = URLComponents(url: self, resolvingAgainstBaseURL: false),
              let queryItems = components.queryItems
        else {
            throw SyncURLParseError.invalidFormat("Could not parse query items")
        }

        let dict = Dictionary(
            queryItems.map { ($0.name, $0.value ?? "") },
            uniquingKeysWith: { first, _ in first }
        )
        let ipArrayItems = queryItems.filter { $0.name == "ip[]" }.compactMap(\.value)
            // Foundation percent-encodes the whole query when a URL contains
            // literal `[`/`]` (as the `ip[]` key does), which leaves `%5B`/`%5D`
            // escapes in the parsed values. Decode once more so bracketed IPv6
            // literals survive the round trip.
            .map { $0.removingPercentEncoding ?? $0 }

        guard let version = dict["v"], version == "2" else {
            throw SyncURLParseError.invalidFormat("unsupported version")
        }

        guard let rawNodeId = dict["nodeId"] ?? dict["node_id"],
              PairingInput.isNodeId(rawNodeId)
        else {
            throw SyncURLParseError.missingRequiredParameter("nodeId")
        }

        var addresses = ipArrayItems.map(stripIPv6Brackets)
        if addresses.isEmpty, let single = dict["ip"], !single.isEmpty {
            addresses = [stripIPv6Brackets(single)]
        }
        if addresses.isEmpty, let named = dict["host"], !named.isEmpty {
            addresses = [stripIPv6Brackets(named)]
        }

        let port = dict["port"].flatMap(UInt16.init)
        if dict["port"] != nil, port == nil {
            throw SyncURLParseError.invalidFormat("port is not a number")
        }

        let name = dict["name"].flatMap { $0.isEmpty ? nil : $0 }

        return LivtetSyncURL(
            nodeId: rawNodeId,
            host: addresses.first,
            port: port,
            name: name,
            addresses: addresses
        )
    }

    private func stripIPv6Brackets(_ address: String) -> String {
        if address.hasPrefix("["), address.hasSuffix("]") {
            return String(address.dropFirst().dropLast())
        }
        return address
    }
}

/// Turning what a scanner or paste field produced into a pairing target.
enum PairingInput {
    private static let hexLength = 64
    private static let ulidLength = 26

    /// Accept a full `livtet://sync?v=2&…` URL or a bare node id.
    static func target(from raw: String) -> LivtetSyncURL? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if let url = URL(string: trimmed), url.scheme == "livtet" {
            return try? url.parseLivtetSyncURL()
        }
        guard isNodeId(trimmed) else { return nil }
        return LivtetSyncURL(
            nodeId: trimmed,
            host: nil,
            port: nil,
            name: nil,
            addresses: []
        )
    }

    /// A node id is either iroh's 64-character lowercase hex endpoint id or the
    /// 26-character Crockford-base32 ULID older peers report as their id.
    static func isNodeId(_ value: String) -> Bool {
        if value.count == hexLength, value.allSatisfy(\.isHexDigit) {
            return value == value.lowercased()
        }
        if value.count == ulidLength {
            return value.unicodeScalars.allSatisfy {
                CharacterSet.alphanumerics.contains($0)
            }
        }
        return false
    }
}
