import SwiftUI

@MainActor
public final class HintEngine: ObservableObject {
    @Published public var ghostOutlineVisible: Bool = false
    @Published public var highlightedPieceId: String?
    @Published public var hintCount: Int = 0

    private let puzzle: Puzzle

    public init(puzzle: Puzzle) {
        self.puzzle = puzzle
    }

    public func toggleGhostOutline() { ghostOutlineVisible.toggle() }

    public func highlightNearestPiece() {
        hintCount += 1
        highlightedPieceId = nil
        Task {
            try? await Task.sleep(nanoseconds: 100_000_000)
            highlightedPieceId = nearestUnsolvedPiece()?.id
        }
    }

    public func snapPieceToTarget() {
        hintCount += 1
        guard let piece = nearestUnsolvedPiece(),
              let target = piece.metadata["targetPosition"] as? Vector else { return }
        withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) {
            piece.relocateTo(target.x, target.y)
        }
        puzzle.autoconnect()
        puzzle.validate()
    }

    private func nearestUnsolvedPiece() -> Piece? {
        puzzle.pieces
            .filter { !SpatialMetadata.solved($0) }
            .min(by: { a, b in
                let aDist = distanceToTarget(a)
                let bDist = distanceToTarget(b)
                return aDist < bDist
            })
    }

    private func distanceToTarget(_ piece: Piece) -> CGFloat {
        guard let target = piece.metadata["targetPosition"] as? Vector,
              let anchor = piece.centralAnchor else { return .infinity }
        return Vector.distance(anchor.asVector(), target)
    }
}
