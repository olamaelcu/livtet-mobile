import SwiftUI
import LivtetJigsaw

struct PuzzleTabView: View {
    @StateObject private var viewModel = PuzzleTabViewModel()
    @EnvironmentObject var soundFX: SystemSoundFX
    @State private var showNewPuzzle = false

    var body: some View {
        NavigationStack {
            Group {
                if let puzzle = viewModel.puzzle, viewModel.showActivePuzzle {
                    JigsawPuzzleView(
                        puzzle: puzzle,
                        coverImage: viewModel.coverImage,
                        soundFX: soundFX,
                        onSolved: { viewModel.showActivePuzzle = false }
                    )
                } else {
                    emptyState
                }
            }
            .navigationTitle("Puzzle")
            .background(Color("surfaceDefault").ignoresSafeArea())
            .sheet(isPresented: $showNewPuzzle) {
                NewPuzzleSheet(viewModel: viewModel, isPresented: $showNewPuzzle)
            }
            .task { await viewModel.loadBooks() }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: "puzzlepiece.extension.fill")
                .font(.system(size: 48))
                .foregroundStyle(.brand)
            Text("Jigsaw Puzzles")
                .font(.livtetHeading(size: 24, weight: .semibold))
                .foregroundStyle(Color("textNormal"))
            Text("Turn your book covers into jigsaw puzzles.\nPick a book, choose a difficulty, and start solving.")
                .font(.livtetBody(size: 14))
                .foregroundStyle(Color("textQuiet"))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Button("New Puzzle") {
                showNewPuzzle = true
            }
            .buttonStyle(.borderedProminent)
            .tint(.brand)
            .padding(.top, 8)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
}
