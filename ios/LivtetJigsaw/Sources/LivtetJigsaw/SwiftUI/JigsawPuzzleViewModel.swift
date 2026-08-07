import SwiftUI
import Combine

@MainActor
public final class JigsawPuzzleViewModel: ObservableObject {
    public let puzzle: Puzzle
    public let soundFX: any JigsawSoundFX
    public let coverImage: Image?
    @Published public var piecePositions: [(String, Anchor)] = []
    @Published public var solved: Bool = false

    public init(puzzle: Puzzle, coverImage: Image? = nil, soundFX: any JigsawSoundFX = NoOpSoundFX()) {
        self.puzzle = puzzle
        self.coverImage = coverImage
        self.soundFX = soundFX
        wireEvents()
        refreshPositions()
    }

    private func wireEvents() {
        puzzle.onTranslate { [weak self] _, _, _ in
            Task { @MainActor in
                self?.refreshPositions()
            }
        }
        puzzle.onConnect { [weak self] _, _ in
            Task { @MainActor in
                self?.soundFX.play(.pieceSnapped)
                self?.refreshPositions()
            }
        }
        puzzle.onDisconnect { [weak self] _, _ in
            Task { @MainActor in
                self?.soundFX.play(.pieceDropped)
                self?.refreshPositions()
            }
        }
        puzzle.onValid { [weak self] in
            Task { @MainActor in
                self?.solved = true
                self?.soundFX.play(.solved)
            }
        }
    }

    public func refreshPositions() {
        piecePositions = puzzle.pieces.compactMap { piece in
            guard let id = piece.id, let anchor = piece.centralAnchor else { return nil }
            return (id, anchor)
        }
    }

    public func onDragStart() {
        soundFX.play(.pieceLifted)
    }
}
