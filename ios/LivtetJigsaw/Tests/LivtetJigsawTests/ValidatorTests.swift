import XCTest
@testable import LivtetJigsaw

final class ValidatorTests: XCTestCase {
    func testNullValidatorNeverValid() {
        let puzzle = Puzzle()
        _ = puzzle.newPiece()
        puzzle.validate()
        XCTAssertFalse(puzzle.valid)
    }

    func testPieceValidatorChecksEachPiece() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        let p = puzzle.newPiece()
        p.annotate(["solved": true])

        puzzle.attachValidator(PieceValidator { piece in
            return piece.metadata["solved"] as? Bool == true
        })
        puzzle.validate()
        XCTAssertTrue(puzzle.valid)
    }

    func testPieceValidatorNotAllPiecesMatch() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        let p1 = puzzle.newPiece()
        p1.annotate(["solved": true])
        let p2 = puzzle.newPiece()
        p2.annotate(["solved": false])

        puzzle.attachValidator(PieceValidator { $0.metadata["solved"] as? Bool == true })
        puzzle.validate()
        XCTAssertFalse(puzzle.valid)
    }

    func testValidatorFiresOnValid() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        let p = puzzle.newPiece()
        p.annotate(["ready": false])

        var fired = false
        puzzle.attachValidator(PieceValidator { $0.metadata["ready"] as? Bool == true })
        puzzle.onValid { fired = true }
        puzzle.validate()
        XCTAssertFalse(fired)

        p.annotate(["ready": true])
        puzzle.validate()
        XCTAssertTrue(fired)
    }

    func testValidatorDoesNotFireOnRepeat() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        let p = puzzle.newPiece()
        p.annotate(["ready": true])

        var fireCount = 0
        puzzle.attachValidator(PieceValidator { $0.metadata["ready"] as? Bool == true })
        puzzle.onValid { fireCount += 1 }
        puzzle.validate()
        XCTAssertEqual(fireCount, 1)
        puzzle.validate()
        XCTAssertEqual(fireCount, 1)
    }

    func testPuzzleValidator() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        _ = puzzle.newPiece()
        _ = puzzle.newPiece()

        puzzle.attachValidator(PuzzleValidator { p in
            p.pieces.count == 2
        })
        puzzle.validate()
        XCTAssertTrue(puzzle.valid)
    }

    func testSpatialMetadataSolved() {
        let puzzle = Puzzle(pieceRadius: 50)
        let p = puzzle.newPiece()
        p.locateAt(100, 100)
        p.annotate(["targetPosition": Vector(x: 100, y: 100)])
        p.annotate(["currentPosition": Vector(x: 100, y: 100)])
        puzzle.attachValidator(PieceValidator(SpatialMetadata.solved))
        puzzle.validate()
        XCTAssertTrue(puzzle.valid)
    }

    func testSpatialMetadataNotSolvedWhenMoved() {
        let puzzle = Puzzle(pieceRadius: 50)
        let p = puzzle.newPiece()
        p.locateAt(100, 100)
        p.annotate(["targetPosition": Vector(x: 500, y: 500)])
        puzzle.attachValidator(PieceValidator(SpatialMetadata.solved))
        puzzle.validate()
        XCTAssertFalse(puzzle.valid)
    }
}
