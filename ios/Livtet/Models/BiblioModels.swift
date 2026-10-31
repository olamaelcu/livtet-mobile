import Foundation

/// Wire models for the public catalog and PDS reads behind the book, review,
/// and profile permalinks.
///
/// Every property is optional or defaulted: the same shapes come from
/// Bibliograph's hydrated views and from raw repository records, and a record
/// written by an older client is missing whatever the lexicon added since.

struct BiblioTextRef: Decodable {
    let uri: String?
    let cid: String?
    let name: String?
}

struct BiblioBookResponse: Decodable {
    let uri: String?
    let cid: String?
    let value: BiblioBook
}

struct BiblioBook: Decodable {
    let uri: String?
    let title: String?
    let description: String?
    let coverImageUrl: String?
    let publishedYear: Int?
    let publishDate: String?
    let language: String?
    let pages: Int?
    let format: BiblioFormat?
    let genres: [BiblioGenre]?
    let contributors: [BiblioContributor]?
    let identifiers: [BiblioIdentifier]?
}

struct BiblioContributor: Decodable {
    let bookUri: String?
    let contributor: BiblioTextRef?
    let role: String?
    /// Raw-record spelling of a contribution; the AppView uses `contributor`.
    let subject: BiblioTextRef?

    var person: BiblioTextRef? { contributor ?? subject }
}

struct BiblioGenre: Decodable {
    let uri: String?
    let name: String?
    let emoji: String?
    let description: String?
}

struct BiblioFormat: Decodable {
    let uri: String?
    let description: String?
    let emoji: String?
    let unit: String?
}

struct BiblioIdentifier: Decodable {
    let resource: String?
    let uri: String?
}

struct BiblioReview: Decodable {
    let uri: String?
    let did: String
    let record: BiblioReviewRecord
}

struct BiblioReviewRecord: Decodable {
    let text: String
    /// The edition this review is about; Bibliograph writes a bare at-uri
    /// string while livtet-service writes a strongRef, so both are accepted.
    let bookUri: String?
    let rating: Double?
    let createdAt: String?

    private enum CodingKeys: String, CodingKey {
        case text
        case book
        case rating
        case createdAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        text = try container.decodeIfPresent(String.self, forKey: .text) ?? ""
        rating = try container.decodeIfPresent(Double.self, forKey: .rating)
        createdAt = try container.decodeIfPresent(String.self, forKey: .createdAt)
        if let string = try? container.decodeIfPresent(String.self, forKey: .book) {
            bookUri = string
        } else if let ref = try? container.decodeIfPresent(BiblioTextRef.self, forKey: .book) {
            bookUri = ref.uri
        } else {
            bookUri = nil
        }
    }
}

struct BiblioReviewResponse: Decodable {
    let uri: String?
    let cid: String?
    let value: BiblioReviewRecord?
}

struct BiblioReviewList: Decodable {
    let reviews: [BiblioReview]
    let cursor: String?
}

struct BiblioReviewEnvelope: Decodable {
    let uri: String?
    let value: BiblioReviewRecord
}

struct BiblioReviewListing: Decodable {
    let records: [BiblioReviewEnvelope]
    let cursor: String?
}

struct BiblioShelving: Decodable {
    let uri: String?
    let value: BiblioShelvingValue
}

struct BiblioShelvingValue: Decodable {
    let book: BiblioShelvingBook?
    let shelf: String?
    let metadata: BiblioShelvingMetadata?
}

struct BiblioShelvingBook: Decodable {
    let uri: String?
    let cid: String?
    /// Inline spelling written by reader apps: the edition URI lives here.
    let ref: String?
    let title: String?

    private enum CodingKeys: String, CodingKey {
        case uri
        case cid
        case ref
        case title
    }

    /// The catalog edition URI, whichever spelling the record used.
    var atUri: String? { uri ?? ref }

    init(from decoder: Decoder) throws {
        if let single = try? decoder.singleValueContainer(),
           let string = try? single.decode(String.self) {
            uri = string
            cid = nil
            ref = nil
            title = nil
            return
        }
        let container = try decoder.container(keyedBy: CodingKeys.self)
        uri = try container.decodeIfPresent(String.self, forKey: .uri)
        cid = try container.decodeIfPresent(String.self, forKey: .cid)
        ref = try container.decodeIfPresent(String.self, forKey: .ref)
        title = try container.decodeIfPresent(String.self, forKey: .title)
    }
}

struct BiblioShelvingMetadata: Decodable {
    /// `reading`, `read`, `to-read`, or `dnf`.
    let status: String?
    let pages: Int?
}

struct BiblioShelvingEnvelope: Decodable {
    let uri: String?
    let value: BiblioShelvingValue
}

struct BiblioShelvingListing: Decodable {
    let records: [BiblioShelvingEnvelope]
    let cursor: String?
}

struct BiblioShelfValue: Decodable {
    let name: String?
}

struct BiblioShelfEnvelope: Decodable {
    let uri: String?
    let value: BiblioShelfValue
}

struct BiblioShelfListing: Decodable {
    let records: [BiblioShelfEnvelope]
    let cursor: String?
}

/// `app.bsky.actor.getProfile` view.
struct BiblioActor: Decodable {
    let did: String
    let handle: String?
    let displayName: String?
    let description: String?
    let avatar: String?
}

struct BiblioBacklink: Decodable {
    let did: String
    let collection: String
    let rkey: String
}

struct BiblioBacklinks: Decodable {
    let records: [BiblioBacklink]?
    let cursor: String?
}

struct BiblioImageResponse: Decodable {
    let url: String?
}

/// The three parts of an `at://` URI this app needs.
struct AtUri {
    let did: String
    let collection: String
    let rkey: String

    init?(string: String?) {
        guard let string, string.hasPrefix("at://") else { return nil }
        let parts = string.dropFirst("at://".count).split(separator: "/", omittingEmptySubsequences: false)
        guard parts.count == 3, !parts[0].isEmpty, !parts[1].isEmpty, !parts[2].isEmpty else {
            return nil
        }
        did = String(parts[0])
        collection = String(parts[1])
        rkey = String(parts[2])
    }

    var uri: String { "at://\(did)/\(collection)/\(rkey)" }
}

/// Contributor roles the book screen presents as the author line.
let authorLikeRoles: Set<String> = ["author", "intro_author", "foreword_author", "epilogue_author"]

extension BiblioBook {
    /// Author line: the contributor roles the catalog treats as authorship.
    var authorLine: String? {
        let names = (contributors ?? [])
            .filter { $0.role.map(authorLikeRoles.contains) == true }
            .compactMap { $0.person?.name }
            .filter { !$0.isEmpty }
        return names.isEmpty ? nil : names.joined(separator: ", ")
    }

    /// First ISBN in the edition's identifiers, stripped of its `isbn:` prefix.
    var isbn: String? {
        let resources: Set<String> = ["isbn", "isbn10", "isbn13"]
        return (identifiers ?? [])
            .first { resources.contains($0.resource ?? "") }?
            .uri?
            .replacingOccurrences(of: "isbn:", with: "")
            .trimmingCharacters(in: .whitespaces)
            .nilIfEmpty
    }
}

extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

extension Array {
    func chunked(into size: Int) -> [[Element]] {
        guard size > 0 else { return [self] }
        return stride(from: 0, to: count, by: size).map {
            Array(self[$0 ..< Swift.min($0 + size, count)])
        }
    }
}

/// Formats an atproto `datetime` for display. Returns nil when nothing parses,
/// which the screens treat as "print nothing".
enum PublicDateFormatting {
    private static let withFractional: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let plain: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    private static let display: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .none
        return formatter
    }()

    static func date(from raw: String?) -> Date? {
        guard let raw, !raw.isEmpty else { return nil }
        return withFractional.date(from: raw) ?? plain.date(from: raw)
    }

    static func string(from raw: String?) -> String? {
        guard let date = date(from: raw) else { return nil }
        return display.string(from: date)
    }
}

/// `9.5/10`, `9/10`, or nil when the review carries no rating.
func ratingLabel(_ rating: Double?) -> String? {
    guard let rating else { return nil }
    let text = rating == rating.rounded() ? String(Int(rating)) : String(rating)
    return "\(text)/10"
}
