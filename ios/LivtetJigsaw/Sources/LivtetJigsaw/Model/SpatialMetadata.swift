public struct SpatialMetadata {
    public static func initialize(_ metadata: inout [String: Any], _ current: Vector, _ target: Vector? = nil) {
        metadata["currentPosition"] = current
        metadata["targetPosition"] = target ?? Vector.copy(current)
    }

    public static func solved(_ piece: Piece) -> Bool {
        guard let currentPos = piece.metadata["currentPosition"] as? Vector,
              let targetPos = piece.metadata["targetPosition"] as? Vector else { return false }
        guard let anchor = piece.centralAnchor else { return false }
        return Vector.isClose(anchor.asVector(), targetPos, tolerance: 2.0)
    }

    public static func relativePosition(_ puzzle: Puzzle) -> Bool {
        return puzzle.pieces.allSatisfy(SpatialMetadata.solved(_:))
    }

    public static func absolutePosition(_ piece: Piece) -> Bool {
        guard let targetPos = piece.metadata["targetPosition"] as? Vector,
              let anchor = piece.centralAnchor else { return false }
        return anchor.asVector() == targetPos
    }
}
