# LivtetJigsaw — Port headbreaker to Swift

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.
> Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **MANDATORY WORKTREE:** `/home/vrgl/Code/olamaelcu/livtet-ecosystem/mobile--feat-livtet-jigsaw`
> Every subagent: `workdir=/home/vrgl/Code/olamaelcu/livtet-ecosystem/mobile--feat-livtet-jigsaw`
>
> **JS source ref (fetch per-task):** `https://raw.githubusercontent.com/flbulgarelli/headbreaker/master/src/<file>.js`
>
> **Spec:** `docs/superpowers/specs/2026-08-05-livtet-jigsaw-port-design.md`

**Goal:** Port headbreaker's jigsaw model + Classic outline geometry into a local
SPM package `LivtetJigsaw`, wire it into the Livtet app as a new Puzzle tab.

**Architecture:** 15 JS model files → 15 Swift files in `LivtetJigsaw/Model/` (pure
Swift). SwiftUI views in `LivtetJigsaw/SwiftUI/`. App glue in `Livtet/Views/Puzzle/`.

**Dependency order across tasks:**
T1 (scaffold) → T2 (foundation) → T3 (Connector+Piece) → T4 (Puzzle) → T5 (Sequence, Shuffler)
→ T6 (Validator, SpatialMetadata) → T7 (Manufacturer) → T8 (Outline) → T9 (model tests)
→ T10 (SwiftUI) → T11 (app integration) → T12 (verify)

T5 and T6 can run in parallel. T8 can run once T2 is done (Outline only needs Vector, Anchor).

---

## Inter-Task Type Contracts

Every subagent MUST match these signatures exactly. Files are additive — later
tasks add to Model/, never modify earlier files without explicit instruction.

```swift
// Vector.swift
public struct Vector: Equatable, Codable, AdditiveArithmetic {
    public let x: CGFloat; public let y: CGFloat
    public init(x: CGFloat, y: CGFloat)
    public static let zero: Vector
    public static func +(l: Vector, r: Vector) -> Vector
    public static func -(l: Vector, r: Vector) -> Vector
    public static func *(l: Vector, r: CGFloat) -> Vector
    public static func /(l: Vector, r: CGFloat) -> Vector
    public static func cast(_ v: CGFloat) -> Vector // { Vector(x: v, y: v) }
    public static func cast(_ v: Int) -> Vector
    public static func plus(_ a: Vector, _ b: Vector) -> Vector
    public static func minus(_ a: Vector, _ b: Vector) -> Vector
    public static func multiply(_ a: Vector, _ b: Vector) -> Vector
    public static func copy(_ v: Vector) -> Vector  // { v }
    public static func distance(_ a: Vector, _ b: Vector) -> CGFloat
    public static func isClose(_ a: Vector, _ b: Vector, tolerance: CGFloat) -> Bool
}

// Anchor.swift
public struct Anchor: Equatable, Codable {
    public let x: CGFloat; public let y: CGFloat
    public init(x: CGFloat, y: CGFloat)
    public func translated(dx: CGFloat, dy: CGFloat) -> Anchor
    public mutating func translate(_ dx: CGFloat, _ dy: CGFloat)
    public func diff(_ other: Anchor) -> (CGFloat, CGFloat)
    public func isAt(_ x: CGFloat, _ y: CGFloat) -> Bool
    public func asVector() -> Vector
    public func asPair() -> (CGFloat, CGFloat)
}

// Size.swift — prefixed to avoid Swift.Size
public struct JigsawSize: Equatable {
    public let radius: Vector
    public var diameter: Vector { Vector(x: radius.x * 2, y: radius.y * 2) }
    public init(radius: Vector)
    public static func radius(_ value: CGFloat) -> JigsawSize
    public static func radius(_ value: Vector) -> JigsawSize
    public static func diameter(_ value: CGFloat) -> JigsawSize
    public static func diameter(_ value: Vector) -> JigsawSize
}

// Pair.swift — helper for (CGFloat, CGFloat) tuples
public struct Pair {
    public static func isNull(_ x: CGFloat, _ y: CGFloat) -> Bool
    public static func eq(_ a: (CGFloat, CGFloat), _ b: (CGFloat, CGFloat)) -> Bool
}

// Insert.swift
public enum Insert: String, Equatable, Codable { case none, tab, slot
    public var complement: Insert { get }  // tab↔slot, none→none
}

// Structure.swift
public struct Structure: Equatable, Codable {
    public let up, down, left, right: Insert
    public init(up: Insert = .none, down: Insert = .none,
                left: Insert = .none, right: Insert = .none)
    public static func asStructure(_ like: Structure) -> Structure  // { like }
    public static func serialize(_ s: Structure) -> String  // "up-right-down-left"
    public static func deserialize(_ string: String) -> Structure
}

// Piece.swift — final class
public typealias TranslationListener = (Piece, CGFloat, CGFloat) -> Void
public typealias ConnectionListener = (Piece, Piece) -> Void
public struct PieceConfig { public var centralAnchor: Anchor?; public var metadata: [String: Any]?; public var size: JigsawSize?; public init() }

public final class Piece {
    public let up, down, left, right: Insert
    public var metadata: [String: Any] = [:]
    public var centralAnchor: Anchor!
    public internal(set) weak var upConnection: Piece?
    public internal(set) weak var downConnection: Piece?
    public internal(set) weak var leftConnection: Piece?
    public internal(set) weak var rightConnection: Piece?
    public internal(set) weak var puzzle: Puzzle?
    public var size: JigsawSize { _size ?? puzzle?.pieceSize ?? JigsawSize.radius(50) }
    public var radius: Vector { size.radius }
    public var diameter: Vector { size.diameter }
    public var proximity: CGFloat { puzzle?.proximity ?? 1 }
    public var connected: Bool { ... }
    public var connections: [Piece?] { [rightConnection, downConnection, leftConnection, upConnection] }
    public var presentConnections: [Piece] { connections.compactMap { $0 } }
    public var inserts: [Insert] { [right, down, left, up] }
    public var id: String? { metadata["id"] as? String }
    public var horizontalConnector: Connector { _horizontalConnector ?? puzzle?.horizontalConnector ?? HorizontalConnector() }
    public var verticalConnector: Connector { _verticalConnector ?? puzzle?.verticalConnector ?? VerticalConnector() }
    public init(structure: Structure = Structure(), config: PieceConfig = PieceConfig())
    public func annotate(_ m: [String: Any])  // merges
    public func belongTo(_ p: Puzzle)
    public func centerAround(_ a: Anchor)   // throws if already set
    public func locateAt(_ x: CGFloat, _ y: CGFloat)
    public func resize(_ s: JigsawSize)
    public func configure(_ config: PieceConfig)
    public var downAnchor: Anchor { centralAnchor.translated(dx: 0, dy: radius.y) }
    public var rightAnchor: Anchor { centralAnchor.translated(dx: radius.x, dy: 0) }
    public var upAnchor: Anchor { centralAnchor.translated(dx: 0, dy: -radius.y) }
    public var leftAnchor: Anchor { centralAnchor.translated(dx: -radius.x, dy: 0) }
    public func translate(_ dx: CGFloat, _ dy: CGFloat, quiet: Bool = false)
    public func recenterAround(_ a: Anchor, quiet: Bool = false)
    public func relocateTo(_ x: CGFloat, _ y: CGFloat, quiet: Bool = false)
    public func isAt(_ x: CGFloat, _ y: CGFloat) -> Bool
    public func push(_ dx: CGFloat, _ dy: CGFloat, quiet: Bool = false, pushedPieces: inout [Piece])
    public func drag(_ dx: CGFloat, _ dy: CGFloat, quiet: Bool = false)
    public func drop()
    public func dragAndDrop(_ dx: CGFloat, _ dy: CGFloat)
    public func dragShouldDisconnect(_ dx: CGFloat, _ dy: CGFloat) -> Bool
    public func tryConnectWith(_ other: Piece, back: Bool = false)
    public func tryConnectHorizontallyWith(_ other: Piece, back: Bool = false)
    public func tryConnectVerticallyWith(_ other: Piece, back: Bool = false)
    public func canConnectHorizontallyWith(_ other: Piece) -> Bool
    public func canConnectVerticallyWith(_ other: Piece) -> Bool
    public func connectHorizontallyWith(_ other: Piece, back: Bool = false)
    public func connectVerticallyWith(_ other: Piece, back: Bool = false)
    public func disconnect()
    public func fireTranslate(_ dx: CGFloat, _ dy: CGFloat)
    public func fireConnect(_ other: Piece)
    public func fireDisconnect(_ others: [Piece])
    public func onTranslate(_ f: @escaping TranslationListener)
    public func onConnect(_ f: @escaping ConnectionListener)
    public func onDisconnect(_ f: @escaping ConnectionListener)
    public func horizontallyCloseTo(_ other: Piece) -> Bool
    public func verticallyCloseTo(_ other: Piece) -> Bool
    public func horizontallyMatch(_ other: Piece) -> Bool
    public func verticallyMatch(_ other: Piece) -> Bool
    public func export(compact: Bool = false) -> [String: Any]
    public static func `import`(_ dump: [String: Any]) -> Piece
}

// Puzzle.swift — final class
public final class Puzzle {
    public let pieceSize: JigsawSize
    public let proximity: CGFloat
    public let horizontalConnector: HorizontalConnector
    public let verticalConnector: VerticalConnector
    public var validator: Validator
    public var dragMode: DragMode
    public private(set) var pieces: [Piece]
    public init(pieceRadius: CGFloat = 50, proximity: CGFloat = 1)
    public func newPiece(structure: Structure = Structure(), config: PieceConfig = PieceConfig()) -> Piece
    public func addPiece(_: Piece)
    public func addPieces(_: [Piece])
    public func annotate(_: [[String: Any]])
    public func relocateTo(_: [(CGFloat, CGFloat)])
    public func autoconnect()
    public func autoconnectWith(_: Piece)
    public func disconnect()
    public func shuffle(_ maxX: CGFloat, _ maxY: CGFloat)
    public func shuffleWith(_ shuffler: Shuffler)
    public func translate(_ dx: CGFloat, _ dy: CGFloat)
    public func reframe(_ min: Vector, _ max: Vector)
    public var points: [(CGFloat, CGFloat)] { pieces.map { ($0.centralAnchor.x, $0.centralAnchor.y) } }
    public var refs: [[CGFloat]] { ... }
    public var metadata: [[String: Any]] { pieces.map { $0.metadata } }
    public var head: Piece? { pieces.first }
    public var headAnchor: Anchor? { head?.centralAnchor }
    public var pieceDiameter: Vector { pieceSize.diameter }
    public var pieceRadius: Vector { pieceSize.radius }
    public func attachHorizontalConnectionRequirement(_ req: @escaping ConnectionRequirement)
    public func attachVerticalConnectionRequirement(_ req: @escaping ConnectionRequirement)
    public func attachConnectionRequirement(_ req: @escaping ConnectionRequirement)
    public func clearConnectionRequirements()
    public func attachValidator(_ v: Validator)
    public func isValid() -> Bool
    public func validate()
    public func updateValidity()
    public var valid: Bool { validator.valid }
    public var connected: Bool { pieces.allSatisfy { $0.connected } }
    public func forceConnectionWhileDragging()
    public func forceDisconnectionWhileDragging()
    public func tryDisconnectionWhileDragging()
    public func dragShouldDisconnect(_ piece: Piece, _ dx: CGFloat, _ dy: CGFloat) -> Bool
    public func onTranslate(_ f: @escaping (Piece, CGFloat, CGFloat) -> Void)
    public func onConnect(_ f: @escaping (Piece, Piece) -> Void)
    public func onDisconnect(_ f: @escaping (Piece, Piece) -> Void)
    public func onValid(_ f: @escaping ValidationListener)
    public func export(options: ExportOptions = ExportOptions()) -> [String: Any]
    public static func `import`(_ dump: [String: Any]) -> Puzzle
}

// Connector.swift
public typealias ConnectionRequirement = (Piece, Piece) -> Bool
public let noConnectionRequirements: ConnectionRequirement = { _, _ in true }
public protocol Connector: AnyObject {
    func closeTo(_ a: Piece, _ b: Piece, proximity: CGFloat) -> Bool
    func match(_ a: Piece, _ b: Piece) -> Bool
    func canConnectWith(_ a: Piece, _ b: Piece, proximity: CGFloat) -> Bool
    func connectWith(_ a: Piece, _ b: Piece, proximity: CGFloat, back: Bool)
    func attract(_ a: Piece, _ b: Piece, back: Bool)
}

// Shuffler.swift — callable struct
public struct Shuffler {
    public let fn: ([Piece]) -> [Piece]
    public init(_ fn: @escaping ([Piece]) -> [Piece])
    public func callAsFunction(_ pieces: [Piece]) -> [Piece]
    public static func random(maxX: CGFloat, maxY: CGFloat) -> Shuffler
    public static func padder(_ pad: CGFloat, _ cols: Int, _ rows: Int) -> Shuffler
    public static func columns() -> Shuffler
    public static func grid() -> Shuffler
    public static func line() -> Shuffler
    public static func noise(_ magnitude: Vector) -> Shuffler
}

// Validator.swift
public typealias ValidationListener = () -> Void
public protocol Validator: AnyObject {
    var valid: Bool { get set }
    func isValid(_ puzzle: Puzzle) -> Bool
    func validate(_ puzzle: Puzzle)
    func onValid(_ f: @escaping ValidationListener)
}
```

---

### Task 1: Scaffold `LivtetJigsaw` SPM package

**Files:**
- Create: `ios/LivtetJigsaw/Package.swift`
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/.gitkeep`
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/SwiftUI/.gitkeep`
- Create: `ios/LivtetJigsaw/Tests/LivtetJigsawTests/ModelTests.swift`

**No dependencies.** First task, runs alone.

- [ ] **Step 1: Create directories**

```bash
mkdir -p ios/LivtetJigsaw/Sources/LivtetJigsaw/Model
mkdir -p ios/LivtetJigsaw/Sources/LivtetJigsaw/SwiftUI
mkdir -p ios/LivtetJigsaw/Tests/LivtetJigsawTests
```

- [ ] **Step 2: Write Package.swift**

```swift
// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "LivtetJigsaw",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "LivtetJigsaw", targets: ["LivtetJigsaw"])
    ],
    dependencies: [],
    targets: [
        .target(name: "LivtetJigsaw", path: "Sources/LivtetJigsaw"),
        .testTarget(name: "LivtetJigsawTests", dependencies: ["LivtetJigsaw"],
                    path: "Tests/LivtetJigsawTests")
    ]
)
```

- [ ] **Step 3: Create placeholder test**

`Tests/LivtetJigsawTests/ModelTests.swift`:
```swift
import XCTest
@testable import LivtetJigsaw

final class SmokeTest: XCTestCase {
    func testPackageLoads() {
        // Verified by compilation — remove when real tests are added
        XCTAssertTrue(true)
    }
}
```

- [ ] **Step 4: Verify swift build resolves**

```bash
cd ios/LivtetJigsaw && swift build 2>&1
```

Expected: succeeds with no source files (empty module).

- [ ] **Step 5: Verify swift test runs**

```bash
cd ios/LivtetJigsaw && swift test 2>&1
```

Expected: 1 test passes.

- [ ] **Step 6: Commit**

```bash
git add ios/LivtetJigsaw/
git commit -m "feat(livtet-jigsaw): scaffold SPM package structure"
```

---

### Task 2: Port foundation types

**Files:**
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Vector.swift`
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Anchor.swift`
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Size.swift`
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Pair.swift`
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Insert.swift`
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Structure.swift`
- Create: `ios/LivtetJigsaw/Tests/LivtetJigsawTests/FoundationTests.swift`
- Delete: `ios/LivtetJigsaw/Tests/LivtetJigsawTests/ModelTests.swift`

**JS sources:** `vector.js`, `anchor.js`, `size.js`, `pair.js`, `insert.js`, `structure.js`, `prelude.js`

**No task dependencies.** Runs after Task 1.

**Instructions for subagent:** Port each JS file to the corresponding Swift file. Match
the inter-task contract signatures exactly. Port all static methods and value semantics.

- [ ] **Step 1: Write Vector.swift**

Port `vector.js`. Implement all static methods listed in the contract section.
JS `vector(x, y)` is `Vector(x: x, y: y)`. JS `Vector.zero()` is `Vector.zero`.
JS `Vector.plus(v, w)` is `Vector.plus(v, w)`.

- [ ] **Step 2: Write Anchor.swift**

Port `anchor.js`. Anchor takes `x, y: CGFloat`. `Anchor(x:y:)` is the constructor.
JS `anchor.translate(dx, dy)` → Swift `mutating translate(_:,_:)`.
JS `anchor.translated(dx, dy)` → Swift `func translated(dx:dy:) -> Anchor`.
JS `anchor.diff(other)` → `func diff(_:) -> (CGFloat, CGFloat)`. `anchor.asPair()` → `(x, y)`.

- [ ] **Step 3: Write Size.swift**

Port `size.js`. `JigsawSize(radius:)`. The `radius(_:)` and `diameter(_:)` factory
overloads. JS `size.radius` → `radius`. JS `size.diameter` → `diameter`.

- [ ] **Step 4: Write Pair.swift**

Port `pair.js`. Just static helpers: `isNull(_:_:)` (`x==0 && y==0`, using `CGFloat.ulpOfOne`
tolerance), `eq(_:_:)` (component equality with `CGFloat.ulpOfOne` tolerance).

- [ ] **Step 5: Write Insert.swift**

Port `insert.js`. Three-case enum: `none`, `tab`, `slot`. `complement` property.

- [ ] **Step 6: Write Structure.swift**

Port `structure.js`. Holds four Insert values. `serialize` must match JS ordering:
`up-right-down-left` (the JS `Structure.serialize` uses `[piece.up, piece.right, piece.down, piece.left]`).
`deserialize` reverses. Use lowercase string tokens: `"tab"`, `"slot"`, `"none"`.

- [ ] **Step 7: Write FoundationTests.swift**

```swift
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
        // JS order: up-right-down-left → "tab-none-slot-none"
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
```

- [ ] **Step 8: Build and test**

```bash
cd ios/LivtetJigsaw && swift test 2>&1
```

Expected: all FoundationTests pass.

- [ ] **Step 9: Commit**

```bash
git add ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Vector.swift \
        ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Anchor.swift \
        ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Size.swift \
        ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Pair.swift \
        ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Insert.swift \
        ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Structure.swift \
        ios/LivtetJigsaw/Tests/LivtetJigsawTests/FoundationTests.swift
git rm -f ios/LivtetJigsaw/Tests/LivtetJigsawTests/ModelTests.swift 2>/dev/null; true
git commit -m "feat(livtet-jigsaw): port foundation types"
```

---

### Task 3: Port Connector + Piece

**Files:**
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Connector.swift`
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Piece.swift`
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Puzzle.swift` (minimal stub — Task 4 replaces)
- Create: `ios/LivtetJigsaw/Tests/LivtetJigsawTests/PieceConnectorTests.swift`

**Depends on:** Task 2 (needs Vector, Anchor, Insert, Structure, JigsawSize, Pair).

**JS sources:** `connector.js`, `piece.js`

**Instructions:** Port `connector.js` → `Connector.swift` and `piece.js` → `Piece.swift`.
`Puzzle.swift` here is a minimal forward stub (just enough for Piece to reference it).
Task 4 replaces it.

- [ ] **Step 1: Write Connector.swift**

Port `connector.js`. Two concrete structs: `HorizontalConnector`, `VerticalConnector`.
Both implement the `Connector` protocol from the contract section.

**HorizontalConnector:**
- `closeTo`: `b.leftAnchor.x` is close to `a.rightAnchor.x` AND `b.leftAnchor.y` is close to `a.rightAnchor.y`, each within proximity tolerance using `Vector.isClose`. OR the reverse with `b.rightAnchor` vs `a.leftAnchor`.
- `match`: `a.right.isComplementOf(b.left)` → `a.right.complement == b.left` OR `a.left.isComplementOf(b.right)` → same reversed using Swift property `complement`.
- `canConnectWith`: `closeTo(a,b,proximity) && (match(a,b) || a.connected || b.connected) && requirement(a,b)`.
- `connectWith`: If `a.right.complement == b.left`: set `a.rightConnection = b` and `b.leftConnection = a`. Fire `a.fireConnect(b)`, `b.fireConnect(a)`. If `back`: check `a.left.complement == b.right` similarly.
- `attract`: snap one piece's anchor to the other then connect. If `a.right.complement == b.left`, set `b.recenterAround(a.rightAnchor)` then call `connectWith(a,b,proximity,back)`.

**VerticalConnector:** Same logic but operates on up/down axes.

- [ ] **Step 2: Write Piece.swift**

Full implementation from `piece.js`. Key details:

**translate:** If `(dx,dy)` is non-null (not both ~0), move `centralAnchor.translate(dx, dy)` and fire listeners unless quiet.

**push:** Translate self, then push all connected pieces not yet in `pushedPieces`. Mutates the inout array.

**drag:** If `dragShouldDisconnect` → `disconnect()` then `translate(dx,dy,quiet)`. Else `push(dx,dy,quiet)`.

**disconnect:** For each non-nil connection (up/down/left/right), null out the reciprocal side on the other piece, null out own connection, collect all in a list, then `fireDisconnect(list)`.

**Horizontal/vertical convenience methods** (connectHorizontallyWith, attractHorizontally, tryConnectHorizontallyWith, etc.): delegate to `horizontalConnector`/`verticalConnector`.

**dragShouldDisconnect:** `puzzle?.dragShouldDisconnect(self, dx, dy) ?? false`.

**Connector property:** `getConnector(kind:)` returns `_horizontalConnector`/`_verticalConnector` if set, otherwise delegates to `puzzle?.[kind]Connector`, otherwise creates a new `HorizontalConnector()`. This matches JS behavior.

**Listeners:** store in arrays on Piece. Fire methods call each listener with (self, ...). `fireDisconnect` calls each listener for each disconnected piece.

**export/import:** Serialize centralAnchor as `["x": x, "y": y]`, structure as string, connections as ids (or null). When `compact: true`, omit connections.

- [ ] **Step 3: Write Puzzle.swift (minimal stub)**

Minimal implementation for Task 3 — just enough for Piece to compile and tests to run:

```swift
public struct ExportOptions { public var compact: Bool = false; public init() }

public enum DragMode { case forceConnection, forceDisconnection, tryDisconnection }

public final class Puzzle {
    public let pieceSize: JigsawSize
    public let proximity: CGFloat
    public let horizontalConnector: HorizontalConnector
    public let verticalConnector: VerticalConnector
    public var validator: Validator = NullValidator()
    public var dragMode: DragMode = .tryDisconnection
    public private(set) var pieces: [Piece] = []

    public init(pieceRadius: CGFloat = 50, proximity: CGFloat = 1) {
        self.pieceSize = JigsawSize.radius(pieceRadius)
        self.proximity = proximity
        self.horizontalConnector = HorizontalConnector()
        self.verticalConnector = VerticalConnector()
    }

    public func newPiece(structure: Structure = Structure(), config: PieceConfig = PieceConfig()) -> Piece {
        let piece = Piece(structure: structure, config: config)
        pieces.append(piece)
        piece.belongTo(self)
        return piece
    }

    public func addPiece(_ piece: Piece) { pieces.append(piece); piece.belongTo(self) }

    public func autoconnectWith(_ piece: Piece) {
        pieces.filter { $0 !== piece }.forEach { other in
            piece.tryConnectWith(other)
            other.tryConnectWith(piece, back: true)
        }
    }

    // Stubs for the rest — Task 4 fills these in
    public func autoconnect() { pieces.forEach { autoconnectWith($0) } }
    public func translate(_ dx: CGFloat, _ dy: CGFloat) { pieces.forEach { $0.translate(dx, dy) } }
    public func dragShouldDisconnect(_ piece: Piece, _ dx: CGFloat, _ dy: CGFloat) -> Bool {
        switch dragMode {
        case .forceConnection: return false
        case .forceDisconnection: return true
        case .tryDisconnection: return false  // simplified stub
        }
    }
    public func attachValidator(_ v: Validator) { validator = v }
    public func validate() { validator.validate(self) }
    public func isValid() -> Bool { validator.isValid(self) }
    public var valid: Bool { validator.valid }
    public var head: Piece? { pieces.first }
    public var headAnchor: Anchor? { head?.centralAnchor }
    public var pieceDiameter: Vector { pieceSize.diameter }
    public var pieceRadius: Vector { pieceSize.radius }
    public var connected: Bool { pieces.allSatisfy { $0.connected } }
    public var points: [(CGFloat, CGFloat)] { pieces.map { ($0.centralAnchor.x, $0.centralAnchor.y) } }
    public var metadata: [[String: Any]] { pieces.map { $0.metadata } }
    public func disconnect() { pieces.forEach { $0.disconnect() } }
    public func onTranslate(_ f: @escaping (Piece, CGFloat, CGFloat) -> Void) { pieces.forEach { $0.onTranslate(f) } }
    public func onConnect(_ f: @escaping (Piece, Piece) -> Void) { pieces.forEach { $0.onConnect(f) } }
    public func onDisconnect(_ f: @escaping (Piece, Piece) -> Void) { pieces.forEach { $0.onDisconnect(f) } }
    public func onValid(_ f: @escaping ValidationListener) { validator.onValid(f) }
}
```

Also add a `NullValidator` inline (Task 6 writes the full version; this is a minimal conformer):

```swift
// At bottom of Puzzle.swift
public final class NullValidator: Validator {
    public var valid: Bool = false
    public var listeners: [ValidationListener] = []
    public func isValid(_ puzzle: Puzzle) -> Bool { false }
    public func validate(_ puzzle: Puzzle) { /* no-op */ }
    public func onValid(_ f: @escaping ValidationListener) { listeners.append(f) }
}
```

And forward-declare the `Validator` protocol (Task 6 will write the full one):

```swift
// At top of Puzzle.swift (before the NullValidator class)
public protocol Validator: AnyObject {
    var valid: Bool { get set }
    func isValid(_ puzzle: Puzzle) -> Bool
    func validate(_ puzzle: Puzzle)
    func onValid(_ f: @escaping ValidationListener)
}
public typealias ValidationListener = () -> Void
```

Wait — `Validator` protocol must match the contract. Let me define it properly from the start.

At the top of Puzzle.swift before Puzzle class:

```swift
public typealias ValidationListener = () -> Void

public protocol Validator: AnyObject {
    var valid: Bool { get set }
    func isValid(_ puzzle: Puzzle) -> Bool
    func validate(_ puzzle: Puzzle)
    func onValid(_ f: @escaping ValidationListener)
}
```

- [ ] **Step 4: Write PieceConnectorTests.swift**

```swift
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
        let piece = Piece()
        piece.locateAt(100, 200)
        XCTAssertTrue(piece.isAt(100, 200))
    }

    func testTranslate() {
        let puzzle = Puzzle()
        let piece = puzzle.newPiece()
        piece.locateAt(10, 10)
        piece.translate(5, -3)
        XCTAssertTrue(piece.isAt(15, 7))
    }

    func testAnchors() {
        let piece = Piece()
        piece.locateAt(100, 100)
        piece.resize(JigsawSize.radius(50))
        XCTAssertEqual(piece.rightAnchor, Anchor(x: 150, y: 100))
        XCTAssertEqual(piece.downAnchor, Anchor(x: 100, y: 150))
        XCTAssertEqual(piece.upAnchor, Anchor(x: 100, y: 50))
        XCTAssertEqual(piece.leftAnchor, Anchor(x: 50, y: 100))
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

    func testNoMatchDifferentInserts() {
        let p1 = Piece(structure: Structure(right: .tab))
        let p2 = Piece(structure: Structure(left: .tab))
        XCTAssertFalse(p1.horizontallyMatch(p2))
    }

    func testCloseTo() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        let p1 = puzzle.newPiece(structure: Structure(right: .tab))
        p1.locateAt(0, 0)
        let p2 = puzzle.newPiece(structure: Structure(left: .slot))
        p2.locateAt(101, 0)  // rightAnchor.x=50, leftAnchor.x=51 → diff=1 < proximity=10
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
```

- [ ] **Step 5: Build and test**

```bash
cd ios/LivtetJigsaw && swift test 2>&1
```

Expected: FoundationTests + PieceConnectorTests all pass.

- [ ] **Step 6: Commit**

```bash
git add ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Connector.swift \
        ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Piece.swift \
        ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Puzzle.swift \
        ios/LivtetJigsaw/Tests/LivtetJigsawTests/PieceConnectorTests.swift
git commit -m "feat(livtet-jigsaw): port Connector, Piece, Puzzle stub"
```

---

### Task 4: Port Puzzle (full implementation)

**Files:**
- Overwrite: `ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Puzzle.swift`
- Update: `ios/LivtetJigsaw/Tests/LivtetJigsawTests/PieceConnectorTests.swift` (replace Puzzle stub usage)

**Depends on:** Task 3 (needs Piece, Connector, NullValidator).

**JS sources:** `puzzle.js`, `drag-mode.js`

**Instructions:** Replace the Puzzle stub from Task 3 with the full implementation.
The existing Piece and Connector types must remain source-compatible. Add all missing
methods: `shuffle`, `shuffleWith`, `reframe`, `attachHorizontalConnectionRequirement`,
`attachVerticalConnectionRequirement`, `forceConnectionWhileDragging`, etc.

- [ ] **Step 1: Rewrite Puzzle.swift with full implementation**

Take the stub from Task 3 and add all missing methods. Reference `puzzle.js` for
exact behavior. Key additions:

- **shuffle(maxX, maxY):** Uses `Shuffler.random(maxX, maxY)`. Disconnects all, shuffles, autoconnects.
- **shuffleWith(shuffler):** Disconnects, runs shuffler fn, autoconnects.
- **reframe(min, max):** Computes dx/dy offsets to fit pieces within bounding box. Translates all pieces.
- **attachHorizontalConnectionRequirement:** Sets `horizontalConnector.attachRequirement(req)`.
- **attachVerticalConnectionRequirement:** Sets `verticalConnector.attachRequirement(req)`.
- **attachConnectionRequirement:** Sets both.
- **clearConnectionRequirements:** Resets both to `noConnectionRequirements`.
- **forceConnectionWhileDragging:** Sets `dragMode = .forceConnection`.
- **forceDisconnectionWhileDragging:** Sets `dragMode = .forceDisconnection`.
- **tryDisconnectionWhileDragging:** Sets `dragMode = .tryDisconnection`.
- **dragShouldDisconnect:** ForceConnection → false, ForceDisconnection → true, TryDisconnection → not already connected or moving away from connections.
- **export/import:** Like headbreaker's `Puzzle.export()` / `Puzzle.import()`.
- **refs:** Return list of `[x/diameter.x, y/diameter.y]` for each piece.
- **head:** First piece or nil.
- **updateValidity:** Call `validator.validate(self)`.
- **annotate:** calls `piece.annotate(metadata[index])` per piece.
- **relocateTo:** calls `piece.relocateTo(points[index].0, points[index].1)` per piece.

- [ ] **Step 2: Update tests for full Puzzle**

Add tests:
```swift
func testPuzzleReframe() {
    let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
    let p = puzzle.newPiece()
    p.locateAt(-100, -100)
    puzzle.reframe(Vector.zero, Vector(x: 500, y: 500))
    // piece should have moved into bounds
    XCTAssertGreaterThanOrEqual(p.centralAnchor.x, 0)
}

func testPuzzleShuffle() {
    let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
    for _ in 0..<4 { _ = puzzle.newPiece(); puzzle.pieces.last?.locateAt(0, 0) }
    let before = puzzle.points
    puzzle.shuffle(500, 500)
    let after = puzzle.points
    XCTAssertNotEqual("\(before)", "\(after)")
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
```

- [ ] **Step 3: Build and test**

```bash
cd ios/LivtetJigsaw && swift test 2>&1
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Puzzle.swift \
        ios/LivtetJigsaw/Tests/LivtetJigsawTests/PieceConnectorTests.swift
git commit -m "feat(livtet-jigsaw): full Puzzle implementation (shuffle, reframe, requirements, export)"
```

---

### Task 5: Port Sequence + Shuffler

**Files:**
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Sequence.swift`
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Shuffler.swift`
- Create: `ios/LivtetJigsaw/Tests/LivtetJigsawTests/SequenceShufflerTests.swift`

**Depends on:** Task 4 (needs Piece, but only as array element; Shuffler takes `[Piece]`).

**JS sources:** `sequence.js`, `shuffler.js`

- [ ] **Step 1: Write Sequence.swift**

Port `sequence.js`. Requirements:

```swift
public typealias InsertsGenerator = (Int, Int) -> Insert

public final class InsertSequence {
    private let generator: InsertsGenerator
    private var index: Int = 0
    private var previous: Insert?

    public init(_ generator: @escaping InsertsGenerator)

    public func next() -> Insert
    // Returns generator(index, self.index) and increments index

    public func previousComplement() -> Insert
    // Returns self.previous?.complement ?? .none

    public func current(_ totalCount: Int) -> Insert
    // If index == totalCount, returns .none, else calls generator(index, ...)

    public static let fixed: InsertsGenerator = { _ in .tab }  // port of fixed from sequence.js
    public static let twoAndTwo: InsertsGenerator   // port of twoAndTwo from sequence.js
}
```

The `fixed` generator always returns `.tab`. `twoAndTwo` alternates `.tab`/`.slot` every 2.

- [ ] **Step 2: Write Shuffler.swift**

Port `shuffler.js`. Requirements:

```swift
public struct Shuffler {
    public let fn: ([Piece]) -> [Piece]
    public init(_ fn: @escaping ([Piece]) -> [Piece])
    public func callAsFunction(_ pieces: [Piece]) -> [Piece]

    // Creates random positions within [0, maxX/maxY] and sorts by index
    public static func random(maxX: CGFloat, maxY: CGFloat) -> Shuffler

    // Pads to grid then applies shuffler: arranges pieces in a cols x rows grid
    // spaced by pad distance, then shuffles them
    public static func padder(_ pad: CGFloat, _ cols: Int, _ rows: Int) -> Shuffler

    // Column-based shuffle: transpose grid columns
    public static func columns() -> Shuffler

    // Grid-based shuffle: randomize within grid cells
    public static func grid() -> Shuffler

    // Line shuffle: randomize rows, keeping columns stable
    public static func line() -> Shuffler

    // Add random noise to each piece's position
    public static func noise(_ magnitude: Vector) -> Shuffler
}
```

Match headbreaker's shufer.js semantics exactly — `random` assigns random `(x,y)` within bounds,
`padder` arranges pieces in a rows×cols grid padded by (pad, pad) spacing,
`columns` shuffles column order, `grid` randomizes within 3×3 grid cells.

- [ ] **Step 3: Write SequenceShufflerTests.swift**

```swift
import XCTest
@testable import LivtetJigsaw

final class SequenceTests: XCTestCase {
    func testFixedGenerator() {
        let seq = InsertSequence(InsertSequence.fixed)
        XCTAssertEqual(seq.next(), .tab)
        XCTAssertEqual(seq.next(), .tab)
    }

    func testTwoAndTwo() {
        let g = InsertSequence.twoAndTwo
        XCTAssertEqual(g(0, 0), .tab)
        XCTAssertEqual(g(0, 1), .tab)  // first two, same
        XCTAssertEqual(g(2, 2), .slot) // next two
        XCTAssertEqual(g(3, 3), .slot)
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
        // current(1) means we're at the end → .none
        XCTAssertEqual(seq.current(1), .none)
    }
}

final class ShufflerTests: XCTestCase {
    func testRandomShuffler() {
        let puzzle = Puzzle(pieceRadius: 50, proximity: 10)
        for _ in 0..<4 {
            let p = puzzle.newPiece()
            p.locateAt(0, 0)
        }
        puzzle.shuffleWith(Shuffler.random(maxX: 500, maxY: 500))
        // All pieces should be within bounds
        for p in puzzle.pieces {
            XCTAssertGreaterThanOrEqual(p.centralAnchor.x, 0)
            XCTAssertLessThanOrEqual(p.centralAnchor.x, 500)
        }
    }
}
```

- [ ] **Step 4: Build and test**

```bash
cd ios/LivtetJigsaw && swift test 2>&1
```

- [ ] **Step 5: Commit**

```bash
git add ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Sequence.swift \
        ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Shuffler.swift \
        ios/LivtetJigsaw/Tests/LivtetJigsawTests/SequenceShufflerTests.swift
git commit -m "feat(livtet-jigsaw): port Sequence + Shuffler"
```

---

### Task 6: Port Validator + SpatialMetadata

**Files:**
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Validator.swift`
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/SpatialMetadata.swift`
- Create: `ios/LivtetJigsaw/Tests/LivtetJigsawTests/ValidatorTests.swift`

**Depends on:** Task 4 (needs Puzzle, Piece).

**JS sources:** `validator.js`, `spatial-metadata.js`

**Can run in parallel with:** Task 5.

- [ ] **Step 1: Write Validator.swift AND remove inline definitions from Puzzle.swift**

**After** writing Validator.swift, edit `Puzzle.swift` to remove the inline `Validator` protocol,
`ValidationListener` typealias, and `NullValidator` class that were put there in Task 3.
These are now defined in their proper files (Validator.swift has the protocol + implementations;
the typealias moves to Validator.swift too).

Replace the protocol forward declaration + NullValidator block in Puzzle.swift with:
```swift
// Validator protocol and implementations are in Validator.swift
```

**Validator.swift must contain the protocol declaration as well** (not duplicated in Puzzle.swift):

```swift
public typealias ValidationListener = () -> Void

public protocol Validator: AnyObject {
    var valid: Bool { get set }
    func isValid(_ puzzle: Puzzle) -> Bool
    func validate(_ puzzle: Puzzle)
    func onValid(_ f: @escaping ValidationListener)
}

```swift
// Validator.swift — protocol declared in Puzzle.swift; this file adds implementations

public final class NullValidator: Validator {
    public var valid: Bool = false
    private var listeners: [ValidationListener] = []
    public init() {}
    public func isValid(_ puzzle: Puzzle) -> Bool { false }
    public func validate(_ puzzle: Puzzle) { /* no-op */ }
    public func onValid(_ f: @escaping ValidationListener) { listeners.append(f) }
}

public final class PieceValidator: Validator {
    public var valid: Bool = false
    private var listeners: [ValidationListener] = []
    private let check: (Piece) -> Bool

    public init(_ check: @escaping (Piece) -> Bool) { self.check = check }
    public func isValid(_ puzzle: Puzzle) -> Bool { puzzle.pieces.allSatisfy(check) }
    public func validate(_ puzzle: Puzzle) {
        let wasValid = valid
        valid = puzzle.pieces.allSatisfy(check)
        if !wasValid && valid { listeners.forEach { $0() } }
    }
    public func onValid(_ f: @escaping ValidationListener) { listeners.append(f) }
}

public final class PuzzleValidator: Validator {
    public var valid: Bool = false
    private var listeners: [ValidationListener] = []
    private let check: (Puzzle) -> Bool

    public init(_ check: @escaping (Puzzle) -> Bool) { self.check = check }
    public func isValid(_ puzzle: Puzzle) -> Bool { check(puzzle) }
    public func validate(_ puzzle: Puzzle) {
        let wasValid = valid
        valid = check(puzzle)
        if !wasValid && valid { listeners.forEach { $0() } }
    }
    public func onValid(_ f: @escaping ValidationListener) { listeners.append(f) }

    public static func relativeRefs(_ expected: [[CGFloat]]) -> (Puzzle) -> Bool {
        return { puzzle in
            guard puzzle.refs.count == expected.count else { return false }
            return zip(puzzle.refs, expected).allSatisfy { ref, exp in
                abs(ref[0] - exp[0]) < 0.01 && abs(ref[1] - exp[1]) < 0.01
            }
        }
    }
}
```

- [ ] **Step 2: Write SpatialMetadata.swift**

Port `spatial-metadata.js`. Provides helper static methods for common metadata checks:

```swift
public struct SpatialMetadata {
    // Initialize piece metadata with current and target position
    public static func initialize(_ metadata: inout [String: Any], _ current: Vector, _ target: Vector? = nil) {
        metadata["currentPosition"] = current
        metadata["targetPosition"] = target ?? Vector.copy(current)
    }

    // Check if all pieces are at their target positions (solved)
    public static func solved(_ piece: Piece) -> Bool {
        guard let current = piece.metadata["currentPosition"] as? Vector,
              let target = piece.metadata["targetPosition"] as? Vector else { return false }
        return Vector.isClose(current, target, tolerance: 1)
    }

    // Check relative position (refs match)
    public static func relativePosition(_ puzzle: Puzzle) -> Bool {
        return puzzle.pieces.allSatisfy(SpatialMetadata.solved(_:))
    }

    // Check absolute position
    public static func absolutePosition(_ piece: Piece) -> Bool {
        guard let target = piece.metadata["targetPosition"] as? Vector else { return false }
        return piece.centralAnchor.asVector() == target
    }
}
```

- [ ] **Step 3: Write ValidatorTests.swift**

```swift
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

    func testSpatialMetadataSolved() {
        let puzzle = Puzzle(pieceRadius: 50)
        let p = puzzle.newPiece()
        p.locateAt(100, 100)
        p.annotate(["targetPosition": Vector(x: 100, y: 100)])
        puzzle.attachValidator(PieceValidator(SpatialMetadata.solved))
        puzzle.validate()
        XCTAssertTrue(puzzle.valid)
    }
}
```

- [ ] **Step 4: Build and test**

```bash
cd ios/LivtetJigsaw && swift test 2>&1
```

- [ ] **Step 5: Commit**

```bash
git add ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Validator.swift \
        ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/SpatialMetadata.swift \
        ios/LivtetJigsaw/Tests/LivtetJigsawTests/ValidatorTests.swift
git commit -m "feat(livtet-jigsaw): port Validator + SpatialMetadata"
```

---

### Task 7: Port Manufacturer

**Files:**
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Manufacturer.swift`
- Create: `ios/LivtetJigsaw/Tests/LivtetJigsawTests/ManufacturerTests.swift`

**Depends on:** Task 4 + Task 5 (needs Puzzle, Piece, InsertSequence, Anchor, JigsawSize).

**JS sources:** `manufacturer.js`

- [ ] **Step 1: Write Manufacturer.swift**

Port `manufacturer.js`. Builds a rectangular puzzle with autogenerated inserts:

```swift
public final class Manufacturer {
    public var insertsGenerator: InsertsGenerator = InsertSequence.fixed
    public var metadata: [[String: Any]] = []
    public var headAnchor: Anchor?
    public var structure: [String: Any]?

    public func withMetadata(_ m: [[String: Any]]) { self.metadata = m }
    public func withInsertsGenerator(_ g: @escaping InsertsGenerator) { self.insertsGenerator = g }
    public func withHeadAt(_ anchor: Anchor) { self.headAnchor = anchor }
    public func withStructure(_ s: [String: Any]) { self.structure = s }
    public func withDimensions(_ width: Int, _ height: Int) { self.width = width; self.height = height }

    private var width: Int = 5
    private var height: Int = 5

    public func build() -> Puzzle
    // Creates Puzzle, iterates width×height, creates pieces with appropriate
    // inserts using InsertSequence, positions them via Positioner, annotates metadata
}
```

The `Positioner` is an internal helper — same as in `manufacturer.js`:

```swift
private struct Positioner {
    let puzzle: Puzzle
    let offset: Vector

    init(puzzle: Puzzle, headAnchor: Anchor?) {
        self.puzzle = puzzle
        self.offset = headAnchor?.asVector() ?? puzzle.pieceDiameter
    }

    func naturalAnchor(_ x: Int, _ y: Int) -> Anchor {
        Anchor(x: CGFloat(x) * offset.x + offset.x,
               y: CGFloat(y) * offset.y + offset.y)
    }
}
```

In `build()`:
1. Create `Puzzle(self.structure ?? [:])`
2. For each row y, create a vertical InsertSequence
3. For each col x, create a horizontal InsertSequence
4. Piece inserts = {left: horizPrev, up: vertPrev, right: horizCurrent, down: vertCurrent}
5. Position piece at `positioner.naturalAnchor(x, y)`
6. Annotate with metadata[id = String(index+1)] merged from `self.metadata[index]`

- [ ] **Step 2: Write ManufacturerTests.swift**

```swift
import XCTest
@testable import LivtetJigsaw

final class ManufacturerTests: XCTestCase {
    func testBuild2x2() {
        let m = Manufacturer()
        m.withDimensions(2, 2)
        m.withInsertsGenerator(InsertSequence.fixed)
        let puzzle = m.build()
        XCTAssertEqual(puzzle.pieces.count, 4)
    }

    func testBuildAssignsIds() {
        let m = Manufacturer()
        m.withDimensions(2, 2)
        let puzzle = m.build()
        XCTAssertEqual(puzzle.pieces[0].id, "1")
        XCTAssertEqual(puzzle.pieces[3].id, "4")
    }

    func testBuildWithMetadata() {
        let m = Manufacturer()
        m.withDimensions(2, 2)
        m.withMetadata([
            ["color": "red"], ["color": "blue"],
            ["color": "green"], ["color": "yellow"]
        ])
        let puzzle = m.build()
        XCTAssertEqual(puzzle.pieces[0].metadata["color"] as? String, "red")
        XCTAssertEqual(puzzle.pieces[3].metadata["color"] as? String, "yellow")
    }

    func testBuild3x3() {
        let m = Manufacturer()
        m.withDimensions(3, 3)
        let puzzle = m.build()
        XCTAssertEqual(puzzle.pieces.count, 9)
        // Check corners have flat outer edges
        XCTAssertEqual(puzzle.pieces[0].left, .none)
        XCTAssertEqual(puzzle.pieces[0].up, .none)
        XCTAssertEqual(puzzle.pieces[6].left, .none)
        XCTAssertEqual(puzzle.pieces[6].down, .none)
    }
}
```

- [ ] **Step 3: Build and test**

```bash
cd ios/LivtetJigsaw && swift test 2>&1
```

- [ ] **Step 4: Commit**

```bash
git add ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Manufacturer.swift \
        ios/LivtetJigsaw/Tests/LivtetJigsawTests/ManufacturerTests.swift
git commit -m "feat(livtet-jigsaw): port Manufacturer"
```

---

### Task 8: Port Outline (Classic bezier geometry)

**Files:**
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Outline.swift`
- Create: `ios/LivtetJigsaw/Tests/LivtetJigsawTests/OutlineTests.swift`

**Depends on:** Task 2 (needs Vector, Anchor, JigsawSize. Does NOT need Piece — works on raw geometry).

**JS sources:** `outline.js`, `between.js`

**Can run in parallel with:** Tasks 5, 6, 7 (only needs foundation types).

**This is also needed by the SwiftUI layer (Task 10) for `PieceShape`.**

- [ ] **Step 1: Write Outline.swift**

Port `outline.js` + `between.js`. The `Classic` outline generates bezier control points
for a knob (tab) or slot insert on each edge of a piece.

```swift
public protocol Outline {
    func path(for pieceSize: JigsawSize, structure: Structure, softness: CGFloat, borderFill: CGFloat) -> Path
}

public struct ClassicOutline: Outline {
    public let softness: CGFloat
    public let borderFill: CGFloat

    public init(softness: CGFloat = 0.18, borderFill: CGFloat = 10)

    public func path(for pieceSize: JigsawSize, structure: Structure, softness: CGFloat = 0.18,
                     borderFill: CGFloat = 10) -> Path
}
```

The `path(for:structure:softness:borderFill:)` generates a SwiftUI `Path` for a single piece.
The path traces clockwise: top edge (left→right), right edge (top→bottom), bottom edge
(right→left), left edge (bottom→top). Each edge has a knob or slot if the corresponding
Insert != .none.

**Algorithm (from outline.js klass Classic):**
```
For each edge (top, right, bottom, left):
  - Calculate the start and end points based on piece radius and borderFill
  - If the edge has a tab: generate a bezier curve with control points
    extending outward by (radius + borderFill * softnessFactor)
  - If the edge has a slot: generate an inverted bezier curve
  - If the edge is flat (.none): draw a straight line
```

Port the bezier control point calculations from `between.js` (the `between` function generates
mid-points between two points with a tilt factor for the bezier curve smoothness).

The JS `Classic.outline` function returns an array of line segments. For SwiftUI Path,
convert to: `path.move(to:)`, `path.addLine(to:)`, `path.addCurve(to:control1:control2:)`.

- [ ] **Step 2: Write OutlineTests.swift**

```swift
import XCTest
import SwiftUI
@testable import LivtetJigsaw

final class OutlineTests: XCTestCase {
    func testFlatPieceIsSquare() {
        let outline = ClassicOutline(softness: 0, borderFill: 0)
        let size = JigsawSize.radius(50)
        let path = outline.path(for: size, structure: Structure())
        // Flat piece: path bounding rect should be ~100×100
        let rect = path.boundingRect
        XCTAssertEqual(rect.width, 100, accuracy: 1)
        XCTAssertEqual(rect.height, 100, accuracy: 1)
    }

    func testPieceWithTabExtendsRight() {
        let outline = ClassicOutline(softness: 0.18, borderFill: 10)
        let size = JigsawSize.radius(50)
        let path = outline.path(for: size, structure: Structure(right: .tab))
        // With borderFill=10, tab extends beyond radius
        let rect = path.boundingRect
        XCTAssertGreaterThan(rect.width, 100)
    }

    func testPieceWithSlotHasInwardCurve() {
        let outline = ClassicOutline(softness: 0.18, borderFill: 10)
        let size = JigsawSize.radius(50)
        let path = outline.path(for: size, structure: Structure(right: .slot))
        // Slot curves inward
        let rect = path.boundingRect
        XCTAssertLessThanOrEqual(rect.maxX, 110)
    }

    func testFourTabsAllSides() {
        let outline = ClassicOutline(softness: 0.18, borderFill: 10)
        let size = JigsawSize.radius(50)
        let structure = Structure(up: .tab, down: .tab, left: .tab, right: .tab)
        let path = outline.path(for: size, structure: structure)
        let rect = path.boundingRect
        XCTAssertGreaterThan(rect.width, 110)
        XCTAssertGreaterThan(rect.height, 110)
    }
}
```

- [ ] **Step 3: Build and test**

Note: Outline.swift imports SwiftUI for `Path`. The Package.swift already targets iOS.
`swift build` from the SPM package directory should work.

```bash
cd ios/LivtetJigsaw && swift test 2>&1
```

- [ ] **Step 4: Commit**

```bash
git add ios/LivtetJigsaw/Sources/LivtetJigsaw/Model/Outline.swift \
        ios/LivtetJigsaw/Tests/LivtetJigsawTests/OutlineTests.swift
git commit -m "feat(livtet-jigsaw): port Outline (Classic bezier geometry)"
```

---

### Task 9: Complete model tests

**Files:**
- Update: `ios/LivtetJigsaw/Tests/LivtetJigsawTests/PieceConnectorTests.swift`
- Update: `ios/LivtetJigsaw/Tests/LivtetJigsawTests/SequenceShufflerTests.swift`
- Update: `ios/LivtetJigsaw/Tests/LivtetJigsawTests/ValidatorTests.swift`
- Update: `ios/LivtetJigsaw/Tests/LivtetJigsawTests/ManufacturerTests.swift`

**Depends on:** Tasks 3–8 all done.

**Instructions:** Add edge-case and regression tests matching headbreaker's JS test suite.
Refer to `https://raw.githubusercontent.com/flbulgarelli/headbreaker/master/test/` for each
test file. Add at minimum:

- Test 4-piece puzzle connect/disconnect cycle
- Test shuffle then autoconnect restores connections
- Test push with 3+ connected pieces
- Test twoAndTwo generator produces correct alternating pattern through a full 3×3 Manufacturer
- Test reconnecting after partial disconnect
- Test drag mode forceConnection (connected piece moves as group)
- Test drag mode forceDisconnection (connected piece disconnects)
- Test validator fires exactly once (not on repeated validates)
- Test export/import round-trip with multiple pieces and metadata

**No new files** — extend existing test files.

- [ ] **Step 1: Add tests, verify they all pass**

```bash
cd ios/LivtetJigsaw && swift test 2>&1
```

- [ ] **Step 2: Commit**

```bash
git add ios/LivtetJigsaw/Tests/
git commit -m "test(livtet-jigsaw): complete model test coverage from JS test suite"
```

---

### Task 10: Build SwiftUI layer

**Files:**
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/SwiftUI/JigsawSoundFX.swift`
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/SwiftUI/PieceShape.swift`
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/SwiftUI/JigsawPuzzleViewModel.swift`
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/SwiftUI/HintEngine.swift`
- Create: `ios/LivtetJigsaw/Sources/LivtetJigsaw/SwiftUI/JigsawPuzzleView.swift`

**Depends on:** Tasks 3–8 (needs Puzzle, Piece, Outline, Manufacturer, SpatialMetadata).

- [ ] **Step 1: Write JigsawSoundFX.swift**

```swift
import Foundation

public enum JigsawSoundEvent {
    case pieceLifted, pieceSnapped, pieceDropped, hintUsed, solved
}

public protocol JigsawSoundFX {
    func play(_ event: JigsawSoundEvent)
}

public struct NoOpSoundFX: JigsawSoundFX {
    public init() {}
    public func play(_ event: JigsawSoundEvent) {}
}
```

- [ ] **Step 2: Write PieceShape.swift**

```swift
import SwiftUI

public struct PieceShape: Shape {
    let pieceSize: JigsawSize
    let structure: Structure
    let outline: any Outline

    public init(pieceSize: JigsawSize, structure: Structure, outline: any Outline = ClassicOutline())

    public func path(in rect: CGRect) -> Path {
        // Delegate to outline.path(for: pieceSize, structure: structure)
        // Scale the path to fit rect
        outline.path(for: pieceSize, structure: structure)
    }
}
```

The `PieceShape` wraps `Outline.path(for:structure:)`. The SwiftUI `Path` from the
outline is origin-centered, and `PieceShape` handles translation to piece position.

- [ ] **Step 3: Write JigsawPuzzleViewModel.swift**

```swift
import SwiftUI
import Combine

public final class JigsawPuzzleViewModel: ObservableObject {
    public let puzzle: Puzzle
    public let soundFX: JigsawSoundFX
    public let coverImage: Image?
    @Published public var pieces: [Piece] = []
    @Published public var solved: Bool = false
    @Published public var highlightedPieceId: String?

    public init(puzzle: Puzzle, coverImage: Image? = nil, soundFX: JigsawSoundFX = NoOpSoundFX())

    // Hook into puzzle events for SwiftUI re-rendering
    private func wireEvents() {
        puzzle.onTranslate { [weak self] _, _, _ in
            DispatchQueue.main.async { self?.objectWillChange.send() }
        }
        puzzle.onConnect { [weak self] _, _ in
            DispatchQueue.main.async {
                self?.soundFX.play(.pieceSnapped)
                self?.objectWillChange.send()
            }
        }
        puzzle.onDisconnect { [weak self] _, _ in
            DispatchQueue.main.async {
                self?.soundFX.play(.pieceDropped)
                self?.objectWillChange.send()
            }
        }
        puzzle.onValid { [weak self] in
            DispatchQueue.main.async {
                self?.solved = true
                self?.soundFX.play(.solved)
            }
        }
    }
}
```

- [ ] **Step 4: Write HintEngine.swift**

```swift
import SwiftUI

public final class HintEngine: ObservableObject {
    @Published public var ghostOutlineVisible: Bool = false
    @Published public var highlightedPieceId: String?
    @Published public var pieces: [Piece]
    @Published public var hintCount: Int = 0

    public init(pieces: [Piece])

    public func toggleGhostOutline() { ghostOutlineVisible.toggle() }

    public func highlightNearestPiece() {
        hintCount += 1
        // Find unsolved piece closest to its target
        highlightedPieceId = nil  // reset animation
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { [weak self] in
            self?.highlightedPieceId = self?.nearestUnsolvedPiece()?.id
        }
    }

    public func snapPieceToTarget() {
        hintCount += 1
        guard let piece = nearestUnsolvedPiece() else { return }
        guard let target = piece.metadata["targetPosition"] as? Vector else { return }
        withAnimation(.spring()) {
            piece.relocateTo(target.x, target.y)
        }
    }

    private func nearestUnsolvedPiece() -> Piece? {
        pieces
            .filter { !SpatialMetadata.solved($0) }
            .min(by: { a, b in
                let aDist = distanceToTarget(a)
                let bDist = distanceToTarget(b)
                return aDist < bDist
            })
    }

    private func distanceToTarget(_ piece: Piece) -> CGFloat {
        guard let target = piece.metadata["targetPosition"] as? Vector else { return .infinity }
        return Vector.distance(piece.centralAnchor.asVector(), target)
    }
}
```

- [ ] **Step 5: Write JigsawPuzzleView.swift**

The main view. Must implement:
- `ZStack` of all pieces rendered as `PieceView` (one per piece)
- `DragGesture` on each piece
- Ghost outline overlay when `ghostOutlineVisible`
- "Hints" toolbar with ghost toggle, highlight button, snap button
- Animations for piece dragging and snapping

```swift
public struct JigsawPuzzleView: View {
    @StateObject private var viewModel: JigsawPuzzleViewModel
    @StateObject private var hintEngine: HintEngine
    @State private var dragStartPos: Anchor?

    public let puzzle: Puzzle
    public let coverImage: Image?
    public let soundFX: JigsawSoundFX
    public let onSolved: (() -> Void)?

    public init(puzzle: Puzzle, coverImage: Image? = nil,
                soundFX: JigsawSoundFX = NoOpSoundFX(),
                onSolved: (() -> Void)? = nil)

    public var body: some View {
        ZStack {
            // Ghost outline
            if hintEngine.ghostOutlineVisible, let image = coverImage {
                image
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .opacity(0.15)
                    .allowsHitTesting(false)
            }

            // Pieces
            ForEach(Array(viewModel.pieces.enumerated()), id: \.element.id) { _, piece in
                PieceView(piece: piece, image: coverImage,
                          highlighted: hintEngine.highlightedPieceId == piece.id)
                    .position(x: piece.centralAnchor.x, y: piece.centralAnchor.y)
                    .gesture(
                        DragGesture()
                            .onChanged { value in
                                if dragStartPos == nil {
                                    dragStartPos = piece.centralAnchor
                                    viewModel.soundFX.play(.pieceLifted)
                                }
                                piece.drag(value.translation.width, value.translation.height, quiet: true)
                                viewModel.objectWillChange.send()
                            }
                            .onEnded { _ in
                                piece.drop()
                                puzzle.validate()
                                viewModel.objectWillChange.send()
                                dragStartPos = nil
                            }
                    )
                    .animation(.spring(), value: piece.centralAnchor.x)
            }
        }
        .toolbar {
            ToolbarItemGroup(placement: .bottomBar) {
                Button { hintEngine.toggleGhostOutline() } label: {
                    Image(systemName: hintEngine.ghostOutlineVisible ? "eye.fill" : "eye")
                }
                Button { hintEngine.highlightNearestPiece() } label: {
                    Image(systemName: "lightbulb")
                }
                Button { hintEngine.snapPieceToTarget() } label: {
                    Image(systemName: "wand.and.stars")
                }
            }
        }
        .onChange(of: viewModel.solved) { newValue in
            if newValue { onSolved?() }
        }
    }
}

struct PieceView: View {
    let piece: Piece
    let image: Image?
    let highlighted: Bool

    var body: some View {
        let shape = PieceShape(pieceSize: piece.size, structure: Structure(
            up: piece.up, down: piece.down,
            left: piece.left, right: piece.right))

        ZStack {
            // Image fill (clipped to piece shape) or gradient fallback
            if let image {
                image
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: piece.diameter.x, height: piece.diameter.y)
                    .clipShape(shape)
            } else {
                shape
                    .fill(LinearGradient(
                        colors: [Color("brand"), Color("brand").opacity(0.6)],
                        startPoint: .topLeading, endPoint: .bottomTrailing))
            }

            // Border stroke
            shape
                .stroke(highlighted ? Color.yellow : Color.black, lineWidth: highlighted ? 3 : 1.5)

            // Highlight pulse overlay
            if highlighted {
                shape
                    .stroke(Color.yellow, lineWidth: 3)
                    .opacity(highlighted ? 1 : 0)
                    .animation(.easeInOut(duration: 0.3).repeatCount(5), value: highlighted)
            }
        }
    }
}
```

- [ ] **Step 6: Build SPM package**

```bash
cd ios/LivtetJigsaw && swift build 2>&1
```

- [ ] **Step 7: Commit**

```bash
git add ios/LivtetJigsaw/Sources/LivtetJigsaw/SwiftUI/
git commit -m "feat(livtet-jigsaw): SwiftUI layer (SoundFX, PieceShape, ViewModel, HintEngine, PuzzleView)"
```

---

### Task 11: App integration

**Files:**
- Modify: `ios/project.yml` (add LivtetJigsaw package + dependency)
- Modify: `ios/Livtet/Views/RootTabView.swift` (add puzzle tab)
- Create: `ios/Livtet/Views/Puzzle/PuzzleTabView.swift`
- Create: `ios/Livtet/Views/Puzzle/PuzzleTabViewModel.swift`
- Create: `ios/Livtet/Views/Puzzle/NewPuzzleSheet.swift`
- Create: `ios/Livtet/Services/SystemSoundFX.swift`
- Modify: `ios/Livtet/LivtetApp.swift` (inject SystemSoundFX)

**Depends on:** Task 10 (needs LivtetJigsaw SPM compiled + public API).

- [ ] **Step 1: Modify project.yml**

Add to `packages:`:
```yaml
  LivtetJigsaw:
    path: ./LivtetJigsaw
```

Add to `Livtet` target's `dependencies:`:
```yaml
      - package: LivtetJigsaw
        product: LivtetJigsaw
```

- [ ] **Step 2: Write SystemSoundFX.swift**

```swift
import AVFoundation
import LivtetJigsaw

public final class SystemSoundFX: JigsawSoundFX {
    private var players: [JigsawSoundEvent: AVAudioPlayer?] = [:]

    public init() {
        // Load bundled sounds; nil if not found (graceful no-op)
        let events: [JigsawSoundEvent] = [.pieceLifted, .pieceSnapped, .pieceDropped, .hintUsed, .solved]
        for event in events {
            guard let url = Bundle.main.url(forResource: soundFileName(for: event),
                                            withExtension: "caf", subdirectory: "Sounds") else { continue }
            players[event] = try? AVAudioPlayer(contentsOf: url)
        }
    }

    private func soundFileName(for event: JigsawSoundEvent) -> String {
        switch event {
        case .pieceLifted: return "piece_lift"
        case .pieceSnapped: return "piece_snap"
        case .pieceDropped: return "piece_drop"
        case .hintUsed: return "hint"
        case .solved: return "solved"
        }
    }

    public func play(_ event: JigsawSoundEvent) {
        players[event]??.play()
    }
}
```

- [ ] **Step 3: Write PuzzleTabViewModel.swift**

```swift
import SwiftUI
import LivtetKit
import LivtetKitFFI
import LivtetJigsaw

public enum PuzzleDifficulty: String, CaseIterable {
    case easy = "2×2"
    case medium = "3×3"
    case hard = "4×4"

    var rows: Int {
        switch self {
        case .easy: return 2
        case .medium: return 3
        case .hard: return 4
        }
    }

    var cols: Int { rows }
}

@MainActor
public final class PuzzleTabViewModel: ObservableObject {
    @Published var books: [Book] = []
    @Published var selectedBook: Book?
    @Published var puzzle: Puzzle?
    @Published var coverImage: Image?
    @Published var difficulty: PuzzleDifficulty = .medium

    private let libraryBridge: LibraryBridge

    public init(libraryBridge: LibraryBridge = LivtetLibraryBridgeAdapter())

    public func loadBooks() async {
        do {
            books = try await libraryBridge.fetchBooks()
        } catch {
            books = []
        }
    }

    public var eligibleBooks: [Book] {
        books.filter { $0.editions.contains { $0.coverPath != nil } }
    }

    public func startPuzzle() {
        guard let book = selectedBook,
              let coverPath = book.editions.first(where: { $0.coverPath != nil })?.coverPath,
              let url = URL(string: "file://\(coverPath)"),
              let data = try? Data(contentsOf: url),
              let uiImage = UIImage(data: data) else {
            // Fallback: puzzle with no image
            puzzle = buildPuzzle()
            return
        }
        coverImage = Image(uiImage: uiImage)
        puzzle = buildPuzzle()
    }

    private func buildPuzzle() -> Puzzle {
        let m = Manufacturer()
        m.withDimensions(difficulty.cols, difficulty.rows)
        m.withInsertsGenerator(InsertSequence.twoAndTwo)
        return m.build()
    }
}
```

- [ ] **Step 4: Write NewPuzzleSheet.swift**

Sheet that picks book + difficulty, then starts puzzle:

```swift
public struct NewPuzzleSheet: View {
    @ObservedObject var viewModel: PuzzleTabViewModel
    @Binding var showPuzzle: Bool

    public var body: some View {
        NavigationStack {
            List {
                Section("Difficulty") {
                    Picker("Difficulty", selection: $viewModel.difficulty) {
                        ForEach(PuzzleDifficulty.allCases, id: \.self) { diff in
                            Text(diff.rawValue).tag(diff)
                        }
                    }
                    .pickerStyle(.segmented)
                }
                Section("Book") {
                    if viewModel.eligibleBooks.isEmpty {
                        Text("No books with covers. Add a book with a cover in the Library tab.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(viewModel.eligibleBooks, id: \.bookId) { book in
                            HStack {
                                Text(book.title)
                                Spacer()
                                if book.bookId == viewModel.selectedBook?.bookId {
                                    Image(systemName: "checkmark")
                                }
                            }
                            .contentShape(Rectangle())
                            .onTapGesture { viewModel.selectedBook = book }
                        }
                    }
                }
            }
            .navigationTitle("New Puzzle")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Start") {
                        viewModel.startPuzzle()
                        showPuzzle = true
                    }
                    .disabled(viewModel.selectedBook == nil)
                }
            }
        }
    }
}
```

- [ ] **Step 5: Write PuzzleTabView.swift**

```swift
import LivtetJigsaw

public struct PuzzleTabView: View {
    @StateObject private var viewModel = PuzzleTabViewModel()
    @State private var showNewPuzzle = false
    @State private var showActivePuzzle = false
    @EnvironmentObject var soundFX: SystemSoundFX

    public var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                if showActivePuzzle, let puzzle = viewModel.puzzle {
                    JigsawPuzzleView(
                        puzzle: puzzle,
                        coverImage: viewModel.coverImage,
                        soundFX: soundFX,
                        onSolved: { showActivePuzzle = false }
                    )
                } else {
                    VStack(spacing: 16) {
                        Image(systemName: "puzzlepiece.extension.fill")
                            .font(.system(size: 48))
                            .foregroundStyle(.brand)
                        Text("Solve a Puzzle")
                            .font(.livtetHeading(size: 24, weight: .semibold))
                        Button("New Puzzle") {
                            showNewPuzzle = true
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.brand)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .navigationTitle("Puzzle")
            .background(Color("surfaceDefault").ignoresSafeArea())
            .sheet(isPresented: $showNewPuzzle) {
                NewPuzzleSheet(viewModel: viewModel, showPuzzle: $showActivePuzzle)
            }
            .task { await viewModel.loadBooks() }
        }
    }
}
```

- [ ] **Step 6: Modify RootTabView.swift**

Add `case puzzle` to `AppTab`. In both `modernTabView` and `legacyTabView`,
insert:
```swift
Tab("Puzzle", systemImage: "puzzlepiece.extension.fill", value: AppTab.puzzle) {
    PuzzleTabView()
}
```
Place it between `.library` and `.feed`.

- [ ] **Step 7: Modify LivtetApp.swift**

In the app's body, add `.environmentObject(SystemSoundFX())` to the root
content view.

- [ ] **Step 8: Run xcodegen**

```bash
xcodegen generate --spec ios/project.yml --project ios 2>&1
```

Expected: succeeds, generates `Livtet.xcodeproj` with new LivtetJigsaw dependency.

- [ ] **Step 9: Commit**

```bash
git add ios/project.yml
git add ios/Livtet/LivtetApp.swift
git add ios/Livtet/Views/RootTabView.swift
git add ios/Livtet/Views/Puzzle/
git add ios/Livtet/Services/SystemSoundFX.swift
git commit -m "feat(livtet-jigsaw): app integration (Puzzle tab, book-cover jigsaws)"
```

---

### Task 12: Final verification

**Depends on:** All prior tasks.

**Runs on:** macOS worktree only (xcodebuild requires macOS). If on Linux, skip xcodebuild steps.

- [ ] **Step 1: Run SPM tests (model layer)**

```bash
cd ios/LivtetJigsaw && swift test 2>&1
```

Expected: all model tests pass.

- [ ] **Step 2: xcodegen regenerate**

```bash
xcodegen generate --spec ios/project.yml --project ios 2>&1
```

- [ ] **Step 3: xcodebuild**

Ask the user first (per guardrails skill). Then:
```bash
xcodebuild -workspace ios/Livtet.xcworkspace -scheme Livtet \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build 2>&1
```

Expected: BUILD SUCCEEDED.

- [ ] **Step 4: Run unit tests (including app-level tests)**

```bash
xcodebuild test -workspace ios/Livtet.xcworkspace -scheme Livtet \
  -destination 'platform=iOS Simulator,name=iPhone 15' 2>&1
```

Expected: all tests pass, 0 failures.

- [ ] **Step 5: Commit final state**

```bash
git add -A
git diff --cached --stat
git commit -m "chore(livtet-jigsaw): final integration verification"
```
