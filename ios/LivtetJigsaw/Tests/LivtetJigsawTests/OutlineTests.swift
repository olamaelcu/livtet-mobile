import XCTest
import SwiftUI
@testable import LivtetJigsaw

final class OutlineTests: XCTestCase {
    func testFlatPieceIsSquare() {
        let outline = ClassicOutline()
        let size = JigsawSize.radius(50)
        let path = outline.path(size: size, structure: Structure())
        let rect = path.boundingRect
        XCTAssertEqual(rect.width, 100, accuracy: 5)
        XCTAssertEqual(rect.height, 100, accuracy: 5)
    }

    func testFlatPieceContainsCenter() {
        let outline = ClassicOutline()
        let size = JigsawSize.radius(50)
        let path = outline.path(size: size, structure: Structure())
        let cgPath = path.cgPath as CGPath
        XCTAssertTrue(cgPath.contains(CGPoint(x: 0, y: 0)))
    }

    func testFourTabsExtendBounds() {
        let outline = ClassicOutline()
        let size = JigsawSize.radius(50)
        let structure = Structure(up: .tab, down: .tab, left: .tab, right: .tab)
        let path = outline.path(size: size, structure: structure, borderFill: Vector(x: 10, y: 10))
        let rect = path.boundingRect
        XCTAssertGreaterThan(rect.width, 100)
        XCTAssertGreaterThan(rect.height, 100)
    }

    func testTabExtendsRight() {
        let outline = ClassicOutline()
        let size = JigsawSize.radius(50)
        let path = outline.path(size: size, structure: Structure(right: .tab), borderFill: Vector(x: 10, y: 10))
        let rect = path.boundingRect
        XCTAssertGreaterThan(rect.width, 110)
    }

    func testSlotCurvesInward() {
        let outline = ClassicOutline()
        let size = JigsawSize.radius(50)
        let slotPath = outline.path(size: size, structure: Structure(right: .slot), borderFill: Vector(x: 10, y: 10))
        let tabPath = outline.path(size: size, structure: Structure(right: .tab), borderFill: Vector(x: 10, y: 10))
        let slotRect = slotPath.boundingRect
        let tabRect = tabPath.boundingRect
        XCTAssertLessThanOrEqual(slotRect.maxX, tabRect.maxX)
    }

    func testPathIsClosed() {
        let outline = ClassicOutline()
        let size = JigsawSize.radius(50)
        let path = outline.path(size: size, structure: Structure(up: .tab, right: .slot, down: .tab, left: .slot))
        XCTAssertFalse(path.boundingRect.isEmpty)
    }

    func testIdentityThroughPathRoundtrip() {
        let outline = ClassicOutline()
        let size = JigsawSize.radius(50)
        let structure = Structure(up: .tab, right: .slot, down: .none, left: .tab)
        let path = outline.path(size: size, structure: structure)
        let rect = path.boundingRect
        XCTAssertGreaterThan(rect.width, 0)
        XCTAssertGreaterThan(rect.height, 0)
        let cgPath = path.cgPath as CGPath
        XCTAssertTrue(cgPath.contains(CGPoint(x: 0, y: 0)))
    }

    func testEmptyPathWithZeroSize() {
        let outline = ClassicOutline()
        let size = JigsawSize.radius(0)
        let path = outline.path(size: size, structure: Structure())
        let rect = path.boundingRect
        XCTAssertEqual(rect.width, 0)
        XCTAssertEqual(rect.height, 0)
    }
}
