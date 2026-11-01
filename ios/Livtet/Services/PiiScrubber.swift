import Foundation
import Sentry

/// Strips personally identifiable information from Sentry events before they
/// are sent. Applied from `options.beforeSend` in `LivtetApp`.
enum PiiScrubber {
    private static let exactKeys: Set<String> = ["device_id", "sync_token", "reading_progress"]
    private static let sensitivePrefixes = ["auth_", "secret_"]

    static func scrub(_ event: Event) {
        event.extra = scrubExtra(event.extra)
        event.tags = scrubTags(event.tags)
    }

    private static func isSensitive(_ key: String) -> Bool {
        let lowercased = key.lowercased()
        if exactKeys.contains(lowercased) { return true }
        return sensitivePrefixes.contains { lowercased.hasPrefix($0) }
    }

    private static func scrubExtra(_ extra: [String: Any]?) -> [String: Any]? {
        guard let extra else { return nil }
        return extra.filter { !isSensitive($0.key) }
    }

    private static func scrubTags(_ tags: [String: String]?) -> [String: String]? {
        guard let tags else { return nil }
        return tags.filter { !isSensitive($0.key) }
    }
}
