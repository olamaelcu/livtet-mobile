import SwiftUI
import LivtetKit
import LivtetJigsaw

enum PuzzleDifficulty: String, CaseIterable {
    case easy = "2x2"
    case medium = "3x3"
    case hard = "4x4"

    var size: Int {
        switch self {
        case .easy: return 2
        case .medium: return 3
        case .hard: return 4
        }
    }
}

@MainActor
final class PuzzleTabViewModel: ObservableObject {
    @Published var books: [WorkSummary] = []
    @Published var selectedBook: WorkSummary?
    @Published var difficulty: PuzzleDifficulty = .medium
    @Published var puzzle: Puzzle?
    @Published var coverImage: Image?
    @Published var showActivePuzzle = false

    private let libraryBridge: LibraryBridge

    init(libraryBridge: LibraryBridge = LivtetLibraryBridgeAdapter()) {
        self.libraryBridge = libraryBridge
    }

    func loadBooks() async {
        do {
            books = try await libraryBridge.listWorks(
                limit: 100, offset: 0, sortDirection: .desc,
                formatIds: [], languageIds: []
            )
        } catch {
            books = []
        }
    }

    var eligibleBooks: [WorkSummary] {
        books
    }

    func startPuzzle() {
        puzzle = buildPuzzle()
        showActivePuzzle = true
        Task { await loadCover() }
    }

    /// Loads the selected work's first attached cover, if any. Covers live on
    /// the edition's `digital_inventory` row, so this walks
    /// work → editions → edition detail.
    private func loadCover() async {
        coverImage = nil
        guard let book = selectedBook,
              let editions = try? await libraryBridge.getEditionsForWork(workId: book.id),
              let edition = editions.first(where: { $0.hasFile }),
              let detail = try? await libraryBridge.getEdition(id: edition.id),
              let coverPath = detail.file?.coverPath,
              let data = try? Data(contentsOf: URL(fileURLWithPath: coverPath)),
              let uiImage = UIImage(data: data) else {
            return
        }
        coverImage = Image(uiImage: uiImage)
    }

    private func buildPuzzle() -> Puzzle {
        let m = Manufacturer()
        _ = m.withDimensions(difficulty.size, difficulty.size)
        _ = m.withInsertsGenerator(InsertSequence.twoAndTwo)

        let puzzle = m.build()
        for piece in puzzle.pieces {
            if let anchor = piece.centralAnchor {
                SpatialMetadata.initialize(
                    &piece.metadata, anchor.asVector()
                )
            }
        }
        let targetPositions = puzzle.pieces.compactMap { $0.centralAnchor }
        puzzle.shuffle(300, 300)

        for (i, piece) in puzzle.pieces.enumerated() where i < targetPositions.count {
            piece.metadata["targetPosition"] = targetPositions[i].asVector()
        }

        return puzzle
    }
}
