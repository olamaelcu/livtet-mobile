import FastULID
import Foundation

// `DbId` is declared by the generated bindings (`LivtetTypes.swift`) as
// `String`; do not redeclare it here or every use site becomes ambiguous.

public extension ULID {
    var hexString: String {
        self.ulidData.map { String(format: "%02x", $0) }.joined()
    }

    static func fromHexString(_ hex: String) -> ULID? {
        guard hex.count == 32 else { return nil }
        var bytes = Data()
        var index = hex.startIndex
        for _ in 0..<16 {
            let nextIndex = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<nextIndex], radix: 16) else { return nil }
            bytes.append(byte)
            index = nextIndex
        }
        return ULID(ulidData: bytes)
    }
}

// MARK: - Codable conformances for generated enums
//
// The generated structs `EditionDetail`, `EditionPatch` and `ReadingProgress`
// declare `Codable` but embed `PublishedDate` / `ProgressUnit`, which UniFFI
// emits without `Codable` — so their conformance cannot be synthesized.
// Synthesis is only available in the declaring file, and generated sources are
// build artifacts, so the conformances are supplied by hand, explicitly, here.
//
// Nothing encodes or decodes these types: the only `JSONEncoder`/`JSONDecoder`
// users in the repo are network DTOs (`NetworkClient`, `OpenLibraryClient`,
// `GoogleBooksClient`, `OverdriveLibrary`), and there is no
// `PropertyListEncoder`, `NSKeyedArchiver` or `UserDefaults` use of them. The
// mappings below are therefore private to this module; keep them stable and
// minimal anyway.
//
// Wire mappings:
// - `ProgressUnit` → single string, the case name (`"percentage"`, `"page"`,
//   `"virtualPage"`, `"timestamp"`, `"chapter"`, `"cfi"`).
// - `PublishedDate` → keyed map `{ kind, year, month?, day? }`, where `kind` is
//   `"year" | "yearMonth" | "yearMonthDay"`.

extension ProgressUnit: Codable {
    /// Stable wire name for the `Codable` representation.
    private var codableName: String {
        switch self {
        case .percentage: return "percentage"
        case .page: return "page"
        case .virtualPage: return "virtualPage"
        case .timestamp: return "timestamp"
        case .chapter: return "chapter"
        case .cfi: return "cfi"
        }
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let name = try container.decode(String.self)
        switch name {
        case "percentage": self = .percentage
        case "page": self = .page
        case "virtualPage": self = .virtualPage
        case "timestamp": self = .timestamp
        case "chapter": self = .chapter
        case "cfi": self = .cfi
        default:
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Unknown ProgressUnit case: \(name)"
            )
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(codableName)
    }
}

extension PublishedDate: Codable {
    private enum Kind: String, Codable {
        case year
        case yearMonth
        case yearMonthDay
    }

    private enum CodingKeys: String, CodingKey {
        case kind
        case year
        case month
        case day
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        switch try container.decode(Kind.self, forKey: .kind) {
        case .year:
            self = .year(try container.decode(Int32.self, forKey: .year))
        case .yearMonth:
            self = .yearMonth(
                year: try container.decode(Int32.self, forKey: .year),
                month: try container.decode(UInt8.self, forKey: .month)
            )
        case .yearMonthDay:
            self = .yearMonthDay(
                year: try container.decode(Int32.self, forKey: .year),
                month: try container.decode(UInt8.self, forKey: .month),
                day: try container.decode(UInt8.self, forKey: .day)
            )
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case let .year(year):
            try container.encode(Kind.year, forKey: .kind)
            try container.encode(year, forKey: .year)
        case let .yearMonth(year, month):
            try container.encode(Kind.yearMonth, forKey: .kind)
            try container.encode(year, forKey: .year)
            try container.encode(month, forKey: .month)
        case let .yearMonthDay(year, month, day):
            try container.encode(Kind.yearMonthDay, forKey: .kind)
            try container.encode(year, forKey: .year)
            try container.encode(month, forKey: .month)
            try container.encode(day, forKey: .day)
        }
    }
}
