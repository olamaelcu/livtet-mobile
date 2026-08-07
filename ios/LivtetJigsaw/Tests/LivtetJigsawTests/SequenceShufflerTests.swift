import XCTest
@testable import LivtetJigsaw

final class SequenceTests: XCTestCase {
    func testFixedGenerator() {
        let seq = InsertSequence(InsertSequence.fixed)
        XCTAssertEqual(seq.next(), .tab)
        XCTAssertEqual(seq.next(), .tab)
    }

    func testTwoAndTwoPattern() {
        let gen = InsertSequence.twoAndTwo
        XCTAssertEqual(gen(0, 0), .tab)
        XCTAssertEqual(gen(1, 1), .tab)
        XCTAssertEqual(gen(2, 2), .slot)
        XCTAssertEqual(gen(3, 3), .slot)
        XCTAssertEqual(gen(4, 4), .tab)
    }

    func testTwoAndTwoSequence() {
        let seq = InsertSequence(InsertSequence.twoAndTwo)
        XCTAssertEqual(seq.next(), .tab)
        XCTAssertEqual(seq.next(), .tab)
        XCTAssertEqual(seq.next(), .slot)
        XCTAssertEqual(seq.next(), .slot)
        XCTAssertEqual(seq.next(), .tab)
    }

    func testPreviousComplement() {
        let seq = InsertSequence(InsertSequence.fixed)
        _ = seq.next()
        XCTAssertEqual(seq.previousComplement(), .slot)
        _ = seq.next()
        XCTAssertEqual(seq.previousComplement(), .slot)
    }

    func testCurrentAtEnd() {
        let seq = InsertSequence(InsertSequence.fixed)
        _ = seq.next()
        _ = seq.next()
        XCTAssertEqual(seq.current(2), .none)
    }

    func testCurrentNotAtEnd() {
        let seq = InsertSequence(InsertSequence.fixed)
        _ = seq.next()
        XCTAssertEqual(seq.current(3), .tab)
    }
}

final class ShufflerTests: XCTestCase {
    func testRandomShufflerWithinBounds() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        for _ in 0..<4 {
            let p = puzzle.newPiece()
            p.locateAt(0, 0)
        }
        puzzle.shuffleWith(Shuffler.random(maxX: 500, maxY: 500))
        for p in puzzle.pieces {
            XCTAssertGreaterThanOrEqual(p.centralAnchor!.x, 0)
            XCTAssertLessThanOrEqual(p.centralAnchor!.x, 500)
            XCTAssertGreaterThanOrEqual(p.centralAnchor!.y, 0)
            XCTAssertLessThanOrEqual(p.centralAnchor!.y, 500)
        }
    }

    func testPadderGridLayout() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        for _ in 0..<9 {
            let p = puzzle.newPiece()
            p.locateAt(0, 0)
        }
        puzzle.shuffleWith(Shuffler.padder(100, 3, 3))
        XCTAssertEqual(puzzle.pieces.count, 9)
    }

    func testNoiseJitter() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        let p = puzzle.newPiece()
        p.locateAt(100, 100)
        let before = p.centralAnchor!
        puzzle.shuffleWith(Shuffler.noise(Vector(x: 10, y: 10)))
        let after = p.centralAnchor!
        let dx = abs(after.x - before.x)
        let dy = abs(after.y - before.y)
        XCTAssertLessThanOrEqual(dx, 10 + 0.001)
        XCTAssertLessThanOrEqual(dy, 10 + 0.001)
    }
}
