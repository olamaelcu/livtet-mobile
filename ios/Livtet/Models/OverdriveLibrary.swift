import Foundation

struct OverdriveLibrary: Codable, Equatable, Identifiable {
    let name: String
    let code: String

    var id: String { code }
}

/// Loads the vendored Overdrive library list from the app bundle.
enum OverdriveLibraryLoader {
    private static var _cache: [OverdriveLibrary]?

    static func load() -> [OverdriveLibrary] {
        if let cached = _cache { return cached }
        guard let url = Bundle.main.url(forResource: "overdrive-libraries", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let libraries = try? JSONDecoder().decode([OverdriveLibrary].self, from: data)
        else {
            _cache = []
            return []
        }
        _cache = libraries
        return libraries
    }
}
