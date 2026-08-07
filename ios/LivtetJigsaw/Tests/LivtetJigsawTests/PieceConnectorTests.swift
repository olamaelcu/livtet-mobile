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

    func testPuzzleReframe() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        let p = puzzle.newPiece()
        p.locateAt(-100, -100)
        puzzle.reframe(Vector.zero, Vector(x: 500, y: 500))
        XCTAssertGreaterThanOrEqual(p.centralAnchor!.x, 0)
    }

    func testPuzzleShuffle() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        for _ in 0..<4 { let p = puzzle.newPiece(); p.locateAt(0, 0) }
        let before = puzzle.points
        puzzle.shuffle(500, 500)
        let after = puzzle.points
        XCTAssertNotEqual(before.count, 0)
        XCTAssertEqual(after.count, before.count)
    }

    func testPuzzleShuffleWith() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        for i in 0..<4 {
            let p = puzzle.newPiece()
            p.locateAt(CGFloat(i) * 10, CGFloat(i) * 10)
        }
        let shuffler = Shuffler { pieces in
            for piece in pieces { piece.relocateTo(500, 500) }
            return pieces
        }
        puzzle.shuffleWith(shuffler)
        for piece in puzzle.pieces {
            XCTAssertTrue(piece.isAt(500, 500))
        }
    }

    func testPuzzleExportImport() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        _ = puzzle.newPiece(structure: Structure(right: .tab))
        puzzle.pieces.first?.locateAt(100, 100)
        let dump = puzzle.export()
        let restored = Puzzle.import(dump)
        XCTAssertEqual(restored.pieces.count, 1)
        XCTAssertEqual(restored.pieceRadius.x, 50)
        XCTAssertTrue(restored.pieces[0].isAt(100, 100))
    }

    func testConnectionRequirements() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 100)
        let p1 = puzzle.newPiece(structure: Structure(right: .tab))
        p1.annotate(["flavour": "chocolate"])
        p1.locateAt(0, 0)
        let p2 = puzzle.newPiece(structure: Structure(left: .slot))
        p2.annotate(["flavour": "vanilla"])
        p2.locateAt(101, 0)

        puzzle.attachConnectionRequirement { a, b in
            return (a.metadata["flavour"] as? String) == (b.metadata["flavour"] as? String)
        }
        p1.tryConnectWith(p2)
        XCTAssertFalse(p1.connected)

        puzzle.clearConnectionRequirements()
        p1.tryConnectWith(p2)
        XCTAssertTrue(p1.connected)
    }

    func testPuzzlePoints() {
        let puzzle = Puzzle()
        let p1 = puzzle.newPiece(); p1.locateAt(10, 20)
        let p2 = puzzle.newPiece(); p2.locateAt(30, 40)
        let pts = puzzle.points
        XCTAssertEqual(pts[0].0, 10); XCTAssertEqual(pts[0].1, 20)
        XCTAssertEqual(pts[1].0, 30); XCTAssertEqual(pts[1].1, 40)
    }

    func testPuzzleMetadata() {
        let puzzle = Puzzle()
        let p = puzzle.newPiece()
        p.annotate(["test": "value"])
        XCTAssertEqual(p.metadata["test"] as? String, "value")
    }

    func testDragModeForceConnection() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        puzzle.forceConnectionWhileDragging()
        let p = puzzle.newPiece(structure: Structure(right: .tab))
        p.locateAt(100, 100)
        XCTAssertFalse(p.dragShouldDisconnect(10, 10))
    }

    func testDragModeForceDisconnection() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        puzzle.forceDisconnectionWhileDragging()
        let p = puzzle.newPiece(structure: Structure(right: .tab))
        p.locateAt(100, 100)
        XCTAssertTrue(p.dragShouldDisconnect(10, 10))
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
