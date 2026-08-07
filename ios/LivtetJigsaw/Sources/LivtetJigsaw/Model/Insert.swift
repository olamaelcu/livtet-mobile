public enum Insert: String, Equatable, Codable {
    case none
    case tab
    case slot

    public var complement: Insert {
        switch self {
        case .tab: return .slot
        case .slot: return .tab
        case .none: return .none
        }
    }
}
