import SwiftUI

public struct JigsawPuzzleView: View {
    @StateObject private var viewModel: JigsawPuzzleViewModel
    @StateObject private var hintEngine: HintEngine
    @State private var dragLastTranslation: CGSize = .zero

    public let puzzle: Puzzle
    public let coverImage: Image?
    public let soundFX: any JigsawSoundFX
    public let onSolved: (() -> Void)?

    public init(puzzle: Puzzle, coverImage: Image? = nil,
                soundFX: any JigsawSoundFX = NoOpSoundFX(),
                onSolved: (() -> Void)? = nil) {
        self.puzzle = puzzle
        self.coverImage = coverImage
        self.soundFX = soundFX
        self.onSolved = onSolved
        _viewModel = StateObject(wrappedValue: JigsawPuzzleViewModel(puzzle: puzzle, coverImage: coverImage, soundFX: soundFX))
        _hintEngine = StateObject(wrappedValue: HintEngine(puzzle: puzzle))
    }

    public var body: some View {
        ZStack {
            Color("surfaceDefault", bundle: .main).ignoresSafeArea()

            if hintEngine.ghostOutlineVisible, let image = coverImage {
                image
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .opacity(0.15)
                    .allowsHitTesting(false)
            }

            ForEach(viewModel.piecePositions, id: \.0) { (id, anchor) in
                if let piece = puzzle.pieces.first(where: { $0.id == id }) {
                    PieceView(
                        piece: piece,
                        image: coverImage,
                        highlighted: hintEngine.highlightedPieceId == id
                    )
                    .position(x: anchor.x, y: anchor.y)
                    .gesture(dragGesture(for: piece))
                    .animation(.spring(response: 0.3, dampingFraction: 0.7), value: anchor.x)
                    .animation(.spring(response: 0.3, dampingFraction: 0.7), value: anchor.y)
                }
            }
        }
        .toolbar {
            ToolbarItemGroup(placement: .bottomBar) {
                Button { hintEngine.toggleGhostOutline() } label: {
                    Image(systemName: hintEngine.ghostOutlineVisible ? "eye.fill" : "eye")
                        .foregroundStyle(hintEngine.ghostOutlineVisible ? .brand : .secondary)
                }
                .disabled(coverImage == nil)

                Button { hintEngine.highlightNearestPiece() } label: {
                    Image(systemName: "lightbulb")
                }

                Button { hintEngine.snapPieceToTarget() } label: {
                    Image(systemName: "wand.and.stars")
                }
            }
        }
        .onChange(of: viewModel.solved) { newValue in
            if newValue { onSolved?() }
        }
    }

    private func dragGesture(for piece: Piece) -> some Gesture {
        DragGesture()
            .onChanged { value in
                viewModel.onDragStart()
                let delta = CGSize(
                    width: value.translation.width - dragLastTranslation.width,
                    height: value.translation.height - dragLastTranslation.height
                )
                piece.drag(delta.width, delta.height, quiet: true)
                dragLastTranslation = value.translation
            }
            .onEnded { _ in
                dragLastTranslation = .zero
                piece.drop()
                puzzle.validate()
                viewModel.refreshPositions()
            }
    }
}

struct PieceView: View {
    let piece: Piece
    let image: Image?
    let highlighted: Bool

    var body: some View {
        let shape = PieceShape(
            pieceSize: piece.size,
            structure: Structure(up: piece.up, down: piece.down, left: piece.left, right: piece.right)
        )

        ZStack {
            if let image {
                image
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: piece.diameter.x, height: piece.diameter.y)
                    .clipShape(shape)
            } else {
                shape
                    .fill(LinearGradient(
                        colors: [.blue.opacity(0.3), .purple.opacity(0.5)],
                        startPoint: .topLeading, endPoint: .bottomTrailing
                    ))
            }

            shape
                .stroke(highlighted ? Color.yellow : Color.black, lineWidth: highlighted ? 3 : 1.5)
        }
        .frame(width: piece.diameter.x, height: piece.diameter.y)
    }
}
