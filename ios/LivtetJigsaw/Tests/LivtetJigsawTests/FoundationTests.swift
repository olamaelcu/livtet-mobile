import XCTest
@testable import LivtetJigsaw

final class VectorTests: XCTestCase {
    func testZero() { XCTAssertEqual(Vector.zero, Vector(x: 0, y: 0)) }
    func testAdd() { XCTAssertEqual(Vector(x: 2, y: 3) + Vector(x: 1, y: 4), Vector(x: 3, y: 7)) }
    func testSubtract() { XCTAssertEqual(Vector(x: 5, y: 8) - Vector(x: 2, y: 3), Vector(x: 3, y: 5)) }
    func testScale() { XCTAssertEqual(Vector(x: 2, y: 3) * 2, Vector(x: 4, y: 6)) }
    func testDivide() { XCTAssertEqual(Vector(x: 6, y: 9) / 3, Vector(x: 2, y: 3)) }
    func testCast() { XCTAssertEqual(Vector.cast(5), Vector(x: 5, y: 5)) }
    func testDistance() { XCTAssertEqual(Vector.distance(.zero, Vector(x: 3, y: 4)), 5) }
    func testIsClose() { XCTAssertTrue(Vector.isClose(Vector(x: 1, y: 1), Vector(x: 1.001, y: 0.999), tolerance: 0.01)) }
    func testMultiply() { XCTAssertEqual(Vector.multiply(Vector(x: 2, y: 3), Vector(x: 4, y: 5)), Vector(x: 8, y: 15)) }
}

final class AnchorTests: XCTestCase {
    func testTranslated() { XCTAssertEqual(Anchor(x: 10, y: 20).translated(dx: 5, dy: -3), Anchor(x: 15, y: 17)) }
    func testTranslateMutate() { var a = Anchor(x: 10, y: 20); a.translate(5, -3); XCTAssertEqual(a, Anchor(x: 15, y: 17)) }
    func testDiff() { let d = Anchor(x: 10, y: 20).diff(Anchor(x: 5, y: 5)); XCTAssertEqual(d.0, 5); XCTAssertEqual(d.1, 15) }
    func testIsAt() { XCTAssertTrue(Anchor(x: 10, y: 20).isAt(10, 20)) }
    func testAsVector() { XCTAssertEqual(Anchor(x: 10, y: 20).asVector(), Vector(x: 10, y: 20)) }
}

final class InsertTests: XCTestCase {
    func testComplement() { XCTAssertEqual(Insert.tab.complement, .slot) }
    func testComplementReverse() { XCTAssertEqual(Insert.slot.complement, .tab) }
    func testComplementNone() { XCTAssertEqual(Insert.none.complement, .none) }
}

final class StructureTests: XCTestCase {
    func testDefaultInit() {
        let s = Structure()
        XCTAssertEqual(s.up, .none); XCTAssertEqual(s.down, .none)
        XCTAssertEqual(s.left, .none); XCTAssertEqual(s.right, .none)
    }
    func testSerialize() {
        let s = Structure(up: .tab, down: .slot)
        XCTAssertEqual(Structure.serialize(s), "tab-none-slot-none")
    }
    func testDeserialize() {
        let s = Structure.deserialize("tab-none-slot-none")
        XCTAssertEqual(s.up, .tab)
        XCTAssertEqual(s.down, .slot)
    }
    func testRoundtrip() {
        let s = Structure(up: .tab, right: .slot, left: .tab, down: .none)
        XCTAssertEqual(Structure.deserialize(Structure.serialize(s)), s)
    }
}
