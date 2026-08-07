import SwiftUI
import LivtetKit
import LivtetKitFFI
import LivtetJigsaw

public enum PuzzleDifficulty: String, CaseIterable {
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
public final class PuzzleTabViewModel: ObservableObject {
    @Published var books: [Book] = []
    @Published var selectedBook: Book?
    @Published var difficulty: PuzzleDifficulty = .medium
    @Published var puzzle: Puzzle?
    @Published var coverImage: Image?
    @Published var showActivePuzzle = false

    private let libraryBridge: LibraryBridge

    public init(libraryBridge: LibraryBridge = LivtetLibraryBridgeAdapter()) {
        self.libraryBridge = libraryBridge
    }

    public func loadBooks() async {
        do {
            books = try libraryBridge.listBooksWithFilters(
                limit: 100, offset: 0, order: .descending,
                filters: BookListFilters(formatIds: [], languageIds: [])
            )
        } catch {
            books = []
        }
    }

    public var eligibleBooks: [Book] {
        books
    }

    public func startPuzzle() {
        guard let book = selectedBook else {
            puzzle = buildPuzzle()
            showActivePuzzle = true
            return
        }
        if let editions = try? libraryBridge.getEditionsWithCoversForWork(workId: book.id),
           let coverPath = editions.first(where: { $0.coverPath != nil })?.coverPath,
           let url = URL(string: "file://\(coverPath)"),
           let data = try? Data(contentsOf: url),
           let uiImage = UIImage(data: data) {
            coverImage = Image(uiImage: uiImage)
        }
        puzzle = buildPuzzle()
        showActivePuzzle = true
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
