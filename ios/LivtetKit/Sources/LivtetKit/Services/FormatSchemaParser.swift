import Foundation
import LivtetKitFFI

public typealias ProgressUnit = LivtetKitFFI.ProgressUnit

/// Type of format as determined by its metadata_schema JSON
public enum FormatType: String, Codable {
    case physicalBook = "PhysicalBook"
    case ebook = "Ebook"
    case audiobook = "Audiobook"
    case `custom`
}

/// Parsed format metadata schema information
public struct FormatSchema: Codable, Equatable {
    public let type: FormatType
    public let pageCount: Int?
    public let virtualPageCount: Int?
    public let durationSeconds: Int?

    /// Determines the semantic unit for progress values
    public var progressUnit: ProgressUnit {
        switch type {
        case .physicalBook:
            return .page
        case .ebook:
            return .ratio
        case .audiobook:
            return .seconds
        case .custom:
            return .ratio
        }
    }
}

/// Parser for format metadata_schema JSON
struct FormatSchemaParser {

    /// Parse a metadata_schema JSON string into a FormatSchema
    static func parse(from jsonString: String) -> FormatSchema? {
        guard let data = jsonString.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }

        // Determine format type from the `required` array
        let required = json["required"] as? [String] ?? []

        let type: FormatType
        if required.contains("page_count") {
            type = .physicalBook
        } else if required.contains("virtual_page_count") {
            type = .ebook
        } else if required.contains("duration_seconds") {
            type = .audiobook
        } else {
            type = .custom
        }

        // Extract optional count fields from properties
        let properties = json["properties"] as? [String: Any] ?? [:]
        let pageCount = (properties["page_count"] as? [String: Any])?["minimum"] as? Int
        let virtualPageCount = (properties["virtual_page_count"] as? [String: Any])?["minimum"] as? Int
        let durationSeconds = (properties["duration_seconds"] as? [String: Any])?["minimum"] as? Int

        return FormatSchema(
            type: type,
            pageCount: pageCount,
            virtualPageCount: virtualPageCount,
            durationSeconds: durationSeconds
        )
    }

    /// Parse a metadata_schema JSON string from a FormatInfo object
    static func parse(from formatInfo: FormatInfo) -> FormatSchema? {
        return parse(from: formatInfo.metadataSchema)
    }
}

/// Extension to FormatInfo to add schema parsing
extension FormatInfo {
    /// Parsed format metadata schema, if available
    var formatSchema: FormatSchema? {
        return FormatSchemaParser.parse(from: self)
    }
}

/// Extension to ReadingProgress to add format-aware display
extension ReadingProgress {
    /// Format the progress value for display based on its unit
    func displayProgress(formatSchema: FormatSchema?) -> String {
        let unit = formatSchema.map { $0.progressUnit } ?? (progressUnit ?? .ratio)

        switch unit {
        case .page:
            let currentPage = Int(progress)
            if let totalPages = formatSchema?.pageCount {
                return "Page \(currentPage) of \(totalPages)"
            } else {
                return "Page \(currentPage)"
            }
        case .ratio:
            let percentage = Int(progress * 100)
            return "\(percentage)%"
        case .seconds:
            return formatSeconds(Int(progress))
        }
    }

    /// Validate that a progress value is within acceptable bounds
    func isValidProgress(formatSchema: FormatSchema?) -> Bool {
        let unit = formatSchema.map { $0.progressUnit } ?? (progressUnit ?? .ratio)

        switch unit {
        case .page:
            guard progress >= 1 else { return false }
            if let pageCount = formatSchema?.pageCount {
                return progress <= Double(pageCount)
            }
            return true
        case .ratio:
            return progress >= 0.0 && progress <= 1.0
        case .seconds:
            guard progress >= 0 else { return false }
            if let duration = formatSchema?.durationSeconds {
                return progress <= Double(duration)
            }
            return true
        }
    }

    /// Format seconds as a human-readable time string (e.g., "1h 23m" or "45m 30s")
    private func formatSeconds(_ seconds: Int) -> String {
        let hours = seconds / 3600
        let minutes = (seconds % 3600) / 60
        let secs = seconds % 60

        if hours > 0 {
            return "\(hours)h \(minutes)m"
        } else if minutes > 0 {
            return "\(minutes)m \(secs)s"
        } else {
            return "\(secs)s"
        }
    }
}

// MARK: - FFI FormatInfo Extension (for the generated FFI type)

#if canImport(Foundation)
extension FormatInfo {
    /// Parsed format metadata schema, if available
    var parsedSchema: FormatSchema? {
        return FormatSchemaParser.parse(from: self.metadataSchema)
    }
}
#endif