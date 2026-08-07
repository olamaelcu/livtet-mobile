public struct Structure: Equatable, Codable {
    public let up: Insert
    public let down: Insert
    public let left: Insert
    public let right: Insert

    public init(
        up: Insert = .none,
        down: Insert = .none,
        left: Insert = .none,
        right: Insert = .none
    ) {
        self.up = up
        self.down = down
        self.left = left
        self.right = right
    }

    public static func asStructure(_ like: Structure) -> Structure {
        like
    }

    public static func serialize(_ structure: Structure) -> String {
        [
            structure.up.rawValue,
            structure.right.rawValue,
            structure.down.rawValue,
            structure.left.rawValue,
        ].joined(separator: "-")
    }

    public static func deserialize(_ string: String) -> Structure {
        let parts = string.split(separator: "-")
        guard parts.count == 4 else {
            return Structure()
        }
        return Structure(
            up: Insert(rawValue: String(parts[0])) ?? .none,
            down: Insert(rawValue: String(parts[2])) ?? .none,
            left: Insert(rawValue: String(parts[3])) ?? .none,
            right: Insert(rawValue: String(parts[1])) ?? .none
        )
    }
}
