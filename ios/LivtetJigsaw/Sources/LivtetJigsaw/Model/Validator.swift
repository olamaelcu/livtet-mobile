import Foundation

public typealias ValidationListener = () -> Void

public protocol Validator: AnyObject {
    var valid: Bool { get set }
    func isValid(_ puzzle: Puzzle) -> Bool
    func validate(_ puzzle: Puzzle)
    func onValid(_ f: @escaping ValidationListener)
}

public final class NullValidator: Validator {
    public var valid: Bool = false
    private var listeners: [ValidationListener] = []

    public init() {}

    public func isValid(_ puzzle: Puzzle) -> Bool { false }

    public func validate(_ puzzle: Puzzle) {}

    public func onValid(_ f: @escaping ValidationListener) { listeners.append(f) }
}

public final class PieceValidator: Validator {
    public var valid: Bool = false
    private var listeners: [ValidationListener] = []
    private let check: (Piece) -> Bool

    public init(_ check: @escaping (Piece) -> Bool) { self.check = check }

    public func isValid(_ puzzle: Puzzle) -> Bool {
        puzzle.pieces.allSatisfy(check)
    }

    public func validate(_ puzzle: Puzzle) {
        let wasValid = valid
        valid = puzzle.pieces.allSatisfy(check)
        if !wasValid && valid { listeners.forEach { $0() } }
    }

    public func onValid(_ f: @escaping ValidationListener) { listeners.append(f) }
}

public final class PuzzleValidator: Validator {
    public var valid: Bool = false
    private var listeners: [ValidationListener] = []
    private let check: (Puzzle) -> Bool

    public init(_ check: @escaping (Puzzle) -> Bool) { self.check = check }

    public func isValid(_ puzzle: Puzzle) -> Bool { check(puzzle) }

    public func validate(_ puzzle: Puzzle) {
        let wasValid = valid
        valid = check(puzzle)
        if !wasValid && valid { listeners.forEach { $0() } }
    }

    public func onValid(_ f: @escaping ValidationListener) { listeners.append(f) }

    public static func relativeRefs(_ expected: [[CGFloat]]) -> (Puzzle) -> Bool {
        return { puzzle in
            guard puzzle.refs.count == expected.count else { return false }
            return zip(puzzle.refs, expected).allSatisfy { ref, exp in
                guard ref.count == 2, exp.count == 2 else { return false }
                return abs(ref[0] - exp[0]) < 0.01 && abs(ref[1] - exp[1]) < 0.01
            }
        }
    }
}
