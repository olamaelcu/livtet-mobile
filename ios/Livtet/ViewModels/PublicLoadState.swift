import Foundation

/// Load state shared by the public permalink screens.
enum PublicLoadState<Value> {
    case loading
    case loaded(Value)
    case failed(String)

    var value: Value? {
        if case let .loaded(value) = self { return value }
        return nil
    }

    var errorMessage: String? {
        if case let .failed(message) = self { return message }
        return nil
    }
}
