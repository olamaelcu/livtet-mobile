import XCTest
@testable import LivtetJigsaw

final class PieceTests: XCTestCase {
    func testInitDefaults() {
        let piece = Piece()
        XCTAssertEqual(piece.up, .none)
        XCTAssertEqual(piece.left, .none)
        XCTAssertFalse(piece.connected)
    }

    func testLocate() {
        let p = Piece()
        p.locateAt(100, 200)
        XCTAssertTrue(p.isAt(100, 200))
    }

    func testTranslate() {
        let puzzle = Puzzle()
        let p = puzzle.newPiece()
        p.locateAt(10, 10)
        p.translate(5, -3)
        XCTAssertTrue(p.isAt(15, 7))
    }

    func testAnchors() {
        let p = Piece()
        p.locateAt(100, 100)
        p.resize(JigsawSize.radius(50))
        XCTAssertEqual(p.rightAnchor, Anchor(x: 150, y: 100))
        XCTAssertEqual(p.downAnchor, Anchor(x: 100, y: 150))
        XCTAssertEqual(p.upAnchor, Anchor(x: 100, y: 50))
        XCTAssertEqual(p.leftAnchor, Anchor(x: 50, y: 100))
    }

    func testAutoConnect() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        let p1 = puzzle.newPiece(structure: Structure(right: .tab))
        p1.locateAt(100, 100)
        let p2 = puzzle.newPiece(structure: Structure(left: .slot))
        p2.locateAt(201, 100)
        puzzle.autoconnect()
        XCTAssertTrue(p1.connected)
        XCTAssertEqual(p1.rightConnection, p2)
        XCTAssertEqual(p2.leftConnection, p1)
    }

    func testDisconnect() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        let p1 = puzzle.newPiece(structure: Structure(right: .tab))
        p1.locateAt(100, 100)
        let p2 = puzzle.newPiece(structure: Structure(left: .slot))
        p2.locateAt(201, 100)
        puzzle.autoconnect()
        p1.disconnect()
        XCTAssertFalse(p1.connected)
        XCTAssertNil(p2.leftConnection)
    }

    func testDragConnected() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        let p1 = puzzle.newPiece(structure: Structure(right: .tab))
        p1.locateAt(100, 100)
        let p2 = puzzle.newPiece(structure: Structure(left: .slot))
        p2.locateAt(201, 100)
        puzzle.autoconnect()
        p1.drag(10, 10)
        XCTAssertTrue(p1.isAt(110, 110))
        XCTAssertTrue(p2.isAt(211, 110))
    }
}

final class ConnectorTests: XCTestCase {
    func testHorizontalMatch() {
        let p1 = Piece(structure: Structure(right: .tab))
        let p2 = Piece(structure: Structure(left: .slot))
        XCTAssertTrue(p1.horizontallyMatch(p2))
        XCTAssertFalse(p2.horizontallyMatch(p1))
    }

    func testVerticalMatch() {
        let p1 = Piece(structure: Structure(down: .tab))
        let p2 = Piece(structure: Structure(up: .slot))
        XCTAssertTrue(p1.verticallyMatch(p2))
    }

    func testNoMatchDiffInserts() {
        let p1 = Piece(structure: Structure(right: .tab))
        let p2 = Piece(structure: Structure(left: .tab))
        XCTAssertFalse(p1.horizontallyMatch(p2))
    }

    func testCloseTo() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        let p1 = puzzle.newPiece(structure: Structure(right: .tab))
        p1.locateAt(0, 0)
        let p2 = puzzle.newPiece(structure: Structure(left: .slot))
        p2.locateAt(101, 0)
        XCTAssertTrue(p1.horizontallyCloseTo(p2))
    }

    func testNotCloseTo() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        let p1 = puzzle.newPiece(structure: Structure(right: .tab))
        p1.locateAt(0, 0)
        let p2 = puzzle.newPiece(structure: Structure(left: .slot))
        p2.locateAt(200, 0)
        XCTAssertFalse(p1.horizontallyCloseTo(p2))
    }
}
