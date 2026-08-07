public final class Manufacturer {
    public var insertsGenerator: InsertsGenerator = InsertSequence.fixed
    public var metadata: [[String: Any]] = []
    public var headAnchor: Anchor?
    private var puzzleOptions: (pieceRadius: CGFloat, proximity: CGFloat) = (50, 1)
    private var puzzleWidth: Int = 5
    private var puzzleHeight: Int = 5

    public init() {}

    public func withMetadata(_ m: [[String: Any]]) -> Self { metadata = m; return self }
    public func withInsertsGenerator(_ g: @escaping InsertsGenerator) -> Self { insertsGenerator = g; return self }
    public func withHeadAt(_ anchor: Anchor) -> Self { headAnchor = anchor; return self }
    public func withStructure(_ opts: (pieceRadius: CGFloat, proximity: CGFloat)) -> Self { puzzleOptions = opts; return self }
    public func withDimensions(_ width: Int, _ height: Int) -> Self { puzzleWidth = width; puzzleHeight = height; return self }

    public func build() -> Puzzle {
        let puzzle = Puzzle(pieceRadius: puzzleOptions.pieceRadius, proximity: puzzleOptions.proximity)
        let positioner = Positioner(puzzle: puzzle, headAnchor: headAnchor)

        for y in 0..<puzzleHeight {
            let vertSeq = InsertSequence(insertsGenerator)
            let horizSeq = InsertSequence(insertsGenerator)

            for x in 0..<puzzleWidth {
                let left = horizSeq.previousComplement()
                let up = vertSeq.previousComplement()
                let right = horizSeq.current(puzzleWidth)
                let down = vertSeq.current(puzzleHeight)

                _ = horizSeq.next()
                _ = vertSeq.next()

                let piece = puzzle.newPiece(
                    structure: Structure(up: up, down: down, left: left, right: right)
                )
                piece.centerAround(positioner.naturalAnchor(x, y))
            }
        }

        annotateAll(puzzle.pieces)
        return puzzle
    }

    private func annotateAll(_ pieces: [Piece]) {
        for (i, piece) in pieces.enumerated() {
            let baseMeta = i < metadata.count ? metadata[i] : [:]
            var meta: [String: Any] = [:]
            baseMeta.forEach { meta[$0] = $1 }
            meta["id"] = meta["id"] as? String ?? String(i + 1)
            piece.annotate(meta)
        }
    }
}

private struct Positioner {
    let puzzle: Puzzle
    let offset: Vector

    init(puzzle: Puzzle, headAnchor: Anchor?) {
        self.puzzle = puzzle
        self.offset = headAnchor?.asVector() ?? puzzle.pieceDiameter
    }

    func naturalAnchor(_ x: Int, _ y: Int) -> Anchor {
        Anchor(
            x: CGFloat(x) * offset.x + offset.x,
            y: CGFloat(y) * offset.y + offset.y
        )
    }
}
