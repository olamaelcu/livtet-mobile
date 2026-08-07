public struct Shuffler {
    public let fn: ([Piece]) -> [Piece]

    public init(_ fn: @escaping ([Piece]) -> [Piece]) {
        self.fn = fn
    }

    public func callAsFunction(_ pieces: [Piece]) -> [Piece] {
        fn(pieces)
    }
}
