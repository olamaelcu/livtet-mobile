import XCTest
@testable import LivtetJigsaw

final class ManufacturerTests: XCTestCase {
    func testBuild2x2() {
        let puzzle = Manufacturer()
            .withDimensions(2, 2)
            .withInsertsGenerator(InsertSequence.fixed)
            .build()
        XCTAssertEqual(puzzle.pieces.count, 4)
    }

    func testBuild3x3() {
        let puzzle = Manufacturer()
            .withDimensions(3, 3)
            .build()
        XCTAssertEqual(puzzle.pieces.count, 9)
    }

    func testBuildAssignsIds() {
        let puzzle = Manufacturer()
            .withDimensions(2, 2)
            .build()
        XCTAssertEqual(puzzle.pieces[0].id, "1")
        XCTAssertEqual(puzzle.pieces[1].id, "2")
        XCTAssertEqual(puzzle.pieces[3].id, "4")
    }

    func testBuildWithMetadata() {
        let puzzle = Manufacturer()
            .withDimensions(2, 2)
            .withMetadata([
                ["color": "red"], ["color": "blue"],
                ["color": "green"], ["color": "yellow"]
            ])
            .build()
        XCTAssertEqual(puzzle.pieces[0].metadata["color"] as? String, "red")
        XCTAssertEqual(puzzle.pieces[3].metadata["color"] as? String, "yellow")
    }

    func testBuildCornerPiecesFlatEdges() {
        let puzzle = Manufacturer()
            .withDimensions(3, 3)
            .withInsertsGenerator(InsertSequence.twoAndTwo)
            .build()
        XCTAssertEqual(puzzle.pieces[0].left, .none, "top-left left should be flat")
        XCTAssertEqual(puzzle.pieces[0].up, .none, "top-left up should be flat")
        XCTAssertEqual(puzzle.pieces[2].right, .none, "top-right right should be flat")
        XCTAssertEqual(puzzle.pieces[2].up, .none, "top-right up should be flat")
        XCTAssertEqual(puzzle.pieces[6].left, .none, "bottom-left left should be flat")
        XCTAssertEqual(puzzle.pieces[6].down, .none, "bottom-left down should be flat")
        XCTAssertEqual(puzzle.pieces[8].right, .none, "bottom-right right should be flat")
        XCTAssertEqual(puzzle.pieces[8].down, .none, "bottom-right down should be flat")
    }

    func testBuildPiecesArePositioned() {
        let puzzle = Manufacturer()
            .withDimensions(2, 2)
            .build()
        for piece in puzzle.pieces {
            XCTAssertNotNil(piece.centralAnchor, "Piece at index should have centralAnchor set")
        }
    }

    func testBuildPiecesBelongToPuzzle() {
        let puzzle = Manufacturer()
            .withDimensions(2, 2)
            .build()
        for piece in puzzle.pieces {
            XCTAssertTrue(piece.puzzle === puzzle)
        }
    }
}
