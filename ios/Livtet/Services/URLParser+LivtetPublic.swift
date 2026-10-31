import Foundation

/// Tabs the profile permalink can open, matching the platform's
/// `/profile/{didOrHandle}?tab=` values plus the shelf routes.
enum ProfileViewTab: String, CaseIterable, Hashable, Identifiable {
    case reviews
    case reading
    case finished
    case toRead = "to-read"
    case abandoned
    case shelves

    var id: String { rawValue }

    var label: String {
        switch self {
        case .reviews: return "Reviews"
        case .reading: return "Reading"
        case .finished: return "Finished"
        case .toRead: return "To Read"
        case .abandoned: return "Abandoned"
        case .shelves: return "Shelves"
        }
    }

    /// `bookShelving.metadata.status` this tab filters on, or nil for the
    /// all-reviews and shelves tabs.
    var shelvingStatus: String? {
        switch self {
        case .reading: return "reading"
        case .finished: return "read"
        case .toRead: return "to-read"
        case .abandoned: return "dnf"
        case .reviews, .shelves: return nil
        }
    }

    /// Unknown values fall back to the reviews tab, like the web page.
    static func normalize(_ raw: String?) -> ProfileViewTab {
        guard let raw, let tab = ProfileViewTab(rawValue: raw) else { return .reviews }
        return tab
    }
}

/// A platform permalink the app renders natively.
enum PublicRoute: Hashable, Identifiable {
    case book(did: String, rkey: String)
    case review(did: String, rkey: String)
    case profile(actor: String, tab: ProfileViewTab, shelf: String?)

    var id: String {
        switch self {
        case let .book(did, rkey):
            return "book/\(did)/\(rkey)"
        case let .review(did, rkey):
            return "review/\(did)/\(rkey)"
        case let .profile(actor, tab, shelf):
            return "profile/\(actor)/\(tab.rawValue)/\(shelf ?? "-")"
        }
    }
}

enum PublicLink {
    static let scheme = "livtet"
    static let host = "livtet.olamaelcu.net"
    static let bibliographAlias = "bibliograph"
    static let bibliographDid = "did:web:biblio.livtet.olamaelcu.net"

    static let maxSegmentLength = 512

    /// `did:plc:…` / `did:web:…`, or the catalog alias.
    static func resolveDid(_ value: String) -> String? {
        if value == bibliographAlias { return bibliographDid }
        guard value.hasPrefix("did:plc:") || value.hasPrefix("did:web:") else { return nil }
        let allowed = CharacterSet(
            charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._:%- "
        ).subtracting(.whitespaces)
        return value.unicodeScalars.allSatisfy { allowed.contains($0) } ? value : nil
    }

    /// Profiles are addressable by DID or handle.
    static func resolveProfileActor(_ value: String) -> String? {
        if let did = resolveDid(value) { return did }
        guard let first = value.first, first.isLetter || first.isNumber else { return nil }
        guard value.count <= 253 else { return nil }
        let allowed = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-")
        return value.unicodeScalars.allSatisfy { allowed.contains($0) } ? value : nil
    }

    /// atproto record key charset; shelf rkeys use the same alphabet.
    static func isRecordKey(_ value: String) -> Bool {
        guard !value.isEmpty, value.count <= maxSegmentLength else { return false }
        let allowed = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._~:-")
        return value.unicodeScalars.allSatisfy { allowed.contains($0) }
    }
}

extension URL {
    /// Parses one of the platform's shareable permalinks.
    ///
    /// Accepts `https://livtet.olamaelcu.net/...` (Universal Links) and the
    /// `livtet://book/...` scheme used for local verification and as the
    /// fallback when association is not verified. Anything else returns nil so
    /// the caller can leave the URL alone, including the `livtet://sync`
    /// pairing link, which is handled elsewhere.
    func parseLivtetPublicURL() -> PublicRoute? {
        guard let components = URLComponents(url: self, resolvingAgainstBaseURL: false) else {
            return nil
        }
        let scheme = (components.scheme ?? "").lowercased()
        let pathSegments = decodedPathSegments(components)
        let queryTab = components.queryItems?.first { $0.name == "tab" }?.value

        let segments: [String]
        switch scheme {
        case "https", "http":
            guard (components.host ?? "").lowercased() == PublicLink.host else { return nil }
            segments = pathSegments
        case PublicLink.scheme:
            // `livtet://book/<did>/<rkey>` — the authority is the prefix.
            guard let prefix = components.host?.lowercased() else { return nil }
            segments = [prefix] + pathSegments
        default:
            return nil
        }

        guard let kind = segments.first else { return nil }
        return Self.route(kind: kind, rest: Array(segments.dropFirst()), queryTab: queryTab)
    }

    private static func route(kind: String, rest: [String], queryTab: String?) -> PublicRoute? {
        switch kind {
        case "book":
            guard rest.count == 2, let did = PublicLink.resolveDid(rest[0]),
                  PublicLink.isRecordKey(rest[1])
            else { return nil }
            return .book(did: did, rkey: rest[1])
        case "review":
            guard rest.count == 2, let did = PublicLink.resolveDid(rest[0]),
                  PublicLink.isRecordKey(rest[1])
            else { return nil }
            return .review(did: did, rkey: rest[1])
        case "profile":
            guard let rawActor = rest.first, let actor = PublicLink.resolveProfileActor(rawActor) else {
                return nil
            }
            switch rest.count {
            case 1:
                return .profile(actor: actor, tab: ProfileViewTab.normalize(queryTab), shelf: nil)
            case 2 where rest[1] == "shelf":
                return .profile(actor: actor, tab: .shelves, shelf: nil)
            case 3 where rest[1] == "shelf":
                guard PublicLink.isRecordKey(rest[2]) else { return nil }
                return .profile(actor: actor, tab: .shelves, shelf: rest[2])
            default:
                return nil
            }
        case "u":
            // `/u/<did>` 303s to `/profile/<did>` on the platform; shared
            // links still exist even though no association file advertises it.
            guard rest.count == 1, let actor = PublicLink.resolveProfileActor(rest[0]) else {
                return nil
            }
            return .profile(actor: actor, tab: .reviews, shelf: nil)
        default:
            return nil
        }
    }

    /// Percent-decoded path segments with blanks, embedded slashes, and
    /// oversized values refused.
    private func decodedPathSegments(_ components: URLComponents) -> [String] {
        let raw = components.percentEncodedPath
        var out: [String] = []
        for part in raw.split(separator: "/", omittingEmptySubsequences: true) {
            guard let decoded = String(part).removingPercentEncoding else { return [] }
            guard !decoded.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
            guard !decoded.contains("/") else { return [] }
            guard decoded.count <= PublicLink.maxSegmentLength else { return [] }
            out.append(decoded)
        }
        return out
    }
}
