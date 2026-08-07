public struct Shuffler {
    public let fn: ([Piece]) -> [Piece]

    public init(_ fn: @escaping ([Piece]) -> [Piece]) {
        self.fn = fn
    }

    public func callAsFunction(_ pieces: [Piece]) -> [Piece] {
        fn(pieces)
    }
}

extension Shuffler {
    public static func random(maxX: CGFloat, maxY: CGFloat) -> Shuffler {
        Shuffler { pieces in
            for piece in pieces {
                piece.relocateTo(CGFloat.random(in: 0...maxX), CGFloat.random(in: 0...maxY))
            }
            return pieces
        }
    }

    public static func padder(_ pad: CGFloat, _ cols: Int, _ rows: Int) -> Shuffler {
        Shuffler { pieces in
            for (i, piece) in pieces.enumerated() {
                let col = i % cols
                let row = i / cols
                piece.relocateTo(CGFloat(col) * pad + pad, CGFloat(row) * pad + pad)
            }
            return pieces
        }
    }

    public static func columns() -> Shuffler {
        Shuffler { pieces in
            guard pieces.count >= 4 else { return pieces }
            let shuffled = pieces.shuffled()
            for (i, piece) in shuffled.enumerated() {
                if i < pieces.count {
                    piece.relocateTo(
                        pieces[i].centralAnchor!.x,
                        pieces[i].centralAnchor!.y
                    )
                }
            }
            return shuffled
        }
    }

    public static func grid() -> Shuffler {
        Shuffler { pieces in
            let count = pieces.count
            let cols = max(1, Int(sqrt(Double(count))))
            for (i, piece) in pieces.enumerated() {
                let col = i % cols
                let row = i / cols
                piece.relocateTo(
                    CGFloat(col * 100),
                    CGFloat(row * 100)
                )
            }
            return pieces
        }
    }

    public static func line() -> Shuffler {
        Shuffler { pieces in
            let positions = pieces.compactMap { $0.centralAnchor }
            let shuffled = pieces.shuffled()
            for (i, piece) in shuffled.enumerated() where i < pieces.count {
                piece.relocateTo(positions[i].x, positions[i].y)
            }
            return shuffled
        }
    }

    public static func noise(_ magnitude: Vector) -> Shuffler {
        Shuffler { pieces in
            for piece in pieces {
                guard let anchor = piece.centralAnchor else { continue }
                let dx = CGFloat.random(in: -magnitude.x...magnitude.x)
                let dy = CGFloat.random(in: -magnitude.y...magnitude.y)
                piece.relocateTo(anchor.x + dx, anchor.y + dy)
            }
            return pieces
        }
    }
}
