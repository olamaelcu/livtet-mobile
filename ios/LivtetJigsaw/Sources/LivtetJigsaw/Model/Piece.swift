import CoreFoundation

public typealias TranslationListener = (Piece, CGFloat, CGFloat) -> Void
public typealias ConnectionListener = (Piece, Piece) -> Void

public struct PieceConfig {
    public var centralAnchor: Anchor?
    public var metadata: [String: Any]?
    public var size: JigsawSize?

    public init(centralAnchor: Anchor? = nil, metadata: [String: Any]? = nil, size: JigsawSize? = nil) {
        self.centralAnchor = centralAnchor
        self.metadata = metadata
        self.size = size
    }
}

public final class Piece {
    public let up: Insert
    public let down: Insert
    public let left: Insert
    public let right: Insert

    public var metadata: [String: Any] = [:]
    public private(set) var centralAnchor: Anchor?
    public internal(set) weak var upConnection: Piece?
    public internal(set) weak var downConnection: Piece?
    public internal(set) weak var leftConnection: Piece?
    public internal(set) weak var rightConnection: Piece?
    public internal(set) weak var puzzle: Puzzle?

    private var _size: JigsawSize?
    private var _horizontalConnector: HorizontalConnector?
    private var _verticalConnector: VerticalConnector?

    private var translateListeners: [TranslationListener] = []
    private var connectListeners: [ConnectionListener] = []
    private var disconnectListeners: [ConnectionListener] = []

    public init(structure: Structure = Structure(), config: PieceConfig = PieceConfig()) {
        up = structure.up
        down = structure.down
        left = structure.left
        right = structure.right
        configure(config)
    }

    public func configure(_ config: PieceConfig) {
        if let anchor = config.centralAnchor { centerAround(anchor) }
        if let m = config.metadata { annotate(m) }
        if let s = config.size { resize(s) }
    }

    public var size: JigsawSize { _size ?? puzzle?.pieceSize ?? JigsawSize.diameter(100) }
    public var radius: Vector { size.radius }
    public var diameter: Vector { size.diameter }
    public var proximity: CGFloat { puzzle?.proximity ?? 1 }

    public var connected: Bool {
        upConnection != nil || downConnection != nil || leftConnection != nil || rightConnection != nil
    }

    public var connections: [Piece?] { [rightConnection, downConnection, leftConnection, upConnection] }
    public var presentConnections: [Piece] { connections.compactMap { $0 } }
    public var inserts: [Insert] { [right, down, left, up] }

    public var id: String? { metadata["id"] as? String }

    public var horizontalConnector: Connector {
        _horizontalConnector ?? (puzzle?.horizontalConnector ?? HorizontalConnector())
    }

    public var verticalConnector: Connector {
        _verticalConnector ?? (puzzle?.verticalConnector ?? VerticalConnector())
    }

    public var downAnchor: Anchor { centralAnchor!.translated(dx: 0, dy: radius.y) }
    public var rightAnchor: Anchor { centralAnchor!.translated(dx: radius.x, dy: 0) }
    public var upAnchor: Anchor { centralAnchor!.translated(dx: 0, dy: -radius.y) }
    public var leftAnchor: Anchor { centralAnchor!.translated(dx: -radius.x, dy: 0) }

    public func centerAround(_ anchor: Anchor) {
        if centralAnchor != nil { fatalError("Already centered. Use recenterAround.") }
        centralAnchor = anchor
    }

    public func locateAt(_ x: CGFloat, _ y: CGFloat) { centerAround(Anchor(x: x, y: y)) }
    public func isAt(_ x: CGFloat, _ y: CGFloat) -> Bool { centralAnchor?.isAt(x, y) ?? false }

    public func resize(_ s: JigsawSize) { _size = s }
    public func annotate(_ m: [String: Any]) { metadata.merge(m) { _, new in new } }
    public func belongTo(_ p: Puzzle) { puzzle = p }

    public func translate(_ dx: CGFloat, _ dy: CGFloat, quiet: Bool = false) {
        guard !Pair.isNull(dx, dy), centralAnchor != nil else { return }
        centralAnchor!.translate(dx, dy)
        if !quiet { fireTranslate(dx, dy) }
    }

    public func recenterAround(_ a: Anchor, quiet: Bool = false) {
        guard let anchor = centralAnchor else { return }
        let (dx, dy) = a.diff(anchor)
        translate(dx, dy, quiet: quiet)
    }

    public func relocateTo(_ x: CGFloat, _ y: CGFloat, quiet: Bool = false) {
        recenterAround(Anchor(x: x, y: y), quiet: quiet)
    }

    public func push(_ dx: CGFloat, _ dy: CGFloat, quiet: Bool = false, pushedPieces: inout [Piece]) {
        translate(dx, dy, quiet: quiet)
        let stationaries = presentConnections.filter { conn in
            !pushedPieces.contains(where: { $0 === conn })
        }
        pushedPieces.append(contentsOf: stationaries)
        for stationary in stationaries {
            stationary.push(dx, dy, quiet: false, pushedPieces: &pushedPieces)
        }
    }

    public func drag(_ dx: CGFloat, _ dy: CGFloat, quiet: Bool = false) {
        guard !Pair.isNull(dx, dy) else { return }
        if dragShouldDisconnect(dx, dy) {
            disconnect()
            translate(dx, dy, quiet: quiet)
        } else {
            var pushed: [Piece] = [self]
            push(dx, dy, quiet: quiet, pushedPieces: &pushed)
        }
    }

    public func drop() { puzzle?.autoconnectWith(self) }

    public func dragAndDrop(_ dx: CGFloat, _ dy: CGFloat) { drag(dx, dy); drop() }

    public func dragShouldDisconnect(_ dx: CGFloat, _ dy: CGFloat) -> Bool {
        puzzle?.dragShouldDisconnect(self, dx, dy) ?? false
    }

    public func disconnect() {
        guard connected else { return }
        let conns = presentConnections

        if upConnection != nil { upConnection!.downConnection = nil; upConnection = nil }
        if downConnection != nil { downConnection!.upConnection = nil; downConnection = nil }
        if leftConnection != nil { leftConnection!.rightConnection = nil; leftConnection = nil }
        if rightConnection != nil { rightConnection!.leftConnection = nil; rightConnection = nil }

        fireDisconnect(conns)
    }

    public func tryConnectWith(_ other: Piece, back: Bool = false) {
        tryConnectHorizontallyWith(other, back: back)
        tryConnectVerticallyWith(other, back: back)
    }

    public func tryConnectHorizontallyWith(_ other: Piece, back: Bool = false) {
        if canConnectHorizontallyWith(other) { connectHorizontallyWith(other, back: back) }
    }

    public func tryConnectVerticallyWith(_ other: Piece, back: Bool = false) {
        if canConnectVerticallyWith(other) { connectVerticallyWith(other, back: back) }
    }

    public func canConnectHorizontallyWith(_ other: Piece) -> Bool {
        horizontalConnector.canConnectWith(self, other, proximity: proximity)
    }

    public func canConnectVerticallyWith(_ other: Piece) -> Bool {
        verticalConnector.canConnectWith(self, other, proximity: proximity)
    }

    public func connectHorizontallyWith(_ other: Piece, back: Bool = false) {
        horizontalConnector.connectWith(self, other, proximity: proximity, back: back)
    }

    public func connectVerticallyWith(_ other: Piece, back: Bool = false) {
        verticalConnector.connectWith(self, other, proximity: proximity, back: back)
    }

    public func horizontallyCloseTo(_ other: Piece) -> Bool {
        horizontalConnector.closeTo(self, other, proximity: proximity)
    }

    public func verticallyCloseTo(_ other: Piece) -> Bool {
        verticalConnector.closeTo(self, other, proximity: proximity)
    }

    public func horizontallyMatch(_ other: Piece) -> Bool {
        horizontalConnector.match(self, other)
    }

    public func verticallyMatch(_ other: Piece) -> Bool {
        verticalConnector.match(self, other)
    }

    public func onTranslate(_ f: @escaping TranslationListener) { translateListeners.append(f) }
    public func onConnect(_ f: @escaping ConnectionListener) { connectListeners.append(f) }
    public func onDisconnect(_ f: @escaping ConnectionListener) { disconnectListeners.append(f) }

    public func fireTranslate(_ dx: CGFloat, _ dy: CGFloat) {
        translateListeners.forEach { $0(self, dx, dy) }
    }

    public func fireConnect(_ other: Piece) {
        connectListeners.forEach { $0(self, other) }
    }

    public func fireDisconnect(_ others: [Piece]) {
        for other in others { disconnectListeners.forEach { $0(self, other) } }
    }

    public func export(compact: Bool = false) -> [String: Any] {
        var result: [String: Any] = [:]
        if let anchor = centralAnchor {
            result["centralAnchor"] = ["x": anchor.x, "y": anchor.y]
        }
        result["structure"] = Structure.serialize(Structure(up: up, down: down, left: left, right: right))
        result["metadata"] = metadata
        if let s = _size {
            result["size"] = ["radius": ["x": s.radius.x, "y": s.radius.y]]
        }
        if !compact {
            result["connections"] = connections.map { conn in
                conn.map { ["id": $0.id ?? ""] } as Any
            }
        }
        return result
    }

    public static func `import`(_ dump: [String: Any]) -> Piece {
        let structureStr = dump["structure"] as? String ?? ""
        let structure = Structure.deserialize(structureStr)
        var config = PieceConfig()
        if let anchorDict = dump["centralAnchor"] as? [String: Any],
           let x = anchorDict["x"] as? CGFloat,
           let y = anchorDict["y"] as? CGFloat {
            config.centralAnchor = Anchor(x: x, y: y)
        }
        config.metadata = dump["metadata"] as? [String: Any]
        if let sizeDict = dump["size"] as? [String: Any],
           let radiusDict = sizeDict["radius"] as? [String: Any],
           let x = radiusDict["x"] as? CGFloat,
           let y = radiusDict["y"] as? CGFloat {
            config.size = JigsawSize(radius: Vector(x: x, y: y))
        }
        return Piece(structure: structure, config: config)
    }
}
