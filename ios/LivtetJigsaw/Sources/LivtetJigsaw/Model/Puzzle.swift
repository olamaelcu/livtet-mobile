import CoreFoundation

public struct ExportOptions { public var compact: Bool = false; public init() {} }

public enum DragMode { case forceConnection, forceDisconnection, tryDisconnection }

public final class Puzzle {
    public let pieceSize: JigsawSize
    public let proximity: CGFloat
    public let horizontalConnector: HorizontalConnector
    public let verticalConnector: VerticalConnector
    public var validator: Validator
    public var dragMode: DragMode = .tryDisconnection
    public private(set) var pieces: [Piece] = []

    public init(pieceRadius: CGFloat = 50, proximity: CGFloat = 1) {
        pieceSize = JigsawSize.radius(pieceRadius)
        self.proximity = proximity
        horizontalConnector = HorizontalConnector()
        verticalConnector = VerticalConnector()
        validator = NullValidator()
    }

    public func newPiece(structure: Structure = Structure(), config: PieceConfig = PieceConfig()) -> Piece {
        let piece = Piece(structure: structure, config: config)
        pieces.append(piece)
        piece.belongTo(self)
        return piece
    }

    public func addPiece(_ piece: Piece) { pieces.append(piece); piece.belongTo(self) }
    public func addPieces(_ newPieces: [Piece]) { newPieces.forEach { addPiece($0) } }

    public func relocateTo(_ points: [(CGFloat, CGFloat)]) {
        for (i, point) in points.enumerated() where i < pieces.count {
            pieces[i].relocateTo(point.0, point.1)
        }
    }

    public func annotate(_ metadataArray: [[String: Any]]) {
        for (i, meta) in metadataArray.enumerated() where i < pieces.count {
            pieces[i].annotate(meta)
        }
    }

    public func autoconnectWith(_ piece: Piece) {
        for other in pieces where other !== piece {
            piece.tryConnectWith(other)
            other.tryConnectWith(piece, back: true)
        }
    }

    public func autoconnect() { pieces.forEach { autoconnectWith($0) } }

    public func translate(_ dx: CGFloat, _ dy: CGFloat) { pieces.forEach { $0.translate(dx, dy) } }

    public func dragShouldDisconnect(_ piece: Piece, _ dx: CGFloat, _ dy: CGFloat) -> Bool {
        switch dragMode {
        case .forceConnection: return false
        case .forceDisconnection: return true
        case .tryDisconnection: return !piece.connected
        }
    }

    public func attachValidator(_ v: Validator) { validator = v }
    public func validate() { validator.validate(self) }
    public func isValid() -> Bool { validator.isValid(self) }
    public func updateValidity() { validator.validate(self) }
    public var valid: Bool { validator.valid }

    public var head: Piece? { pieces.first }
    public var headAnchor: Anchor? { head?.centralAnchor }
    public var pieceDiameter: Vector { pieceSize.diameter }
    public var pieceRadius: Vector { pieceSize.radius }
    public var connected: Bool { pieces.allSatisfy { $0.connected } }
    public var points: [(CGFloat, CGFloat)] {
        pieces.compactMap { p in p.centralAnchor.map { ($0.x, $0.y) } }
    }
    public var metadata: [[String: Any]] { pieces.map { $0.metadata } }

    public var refs: [[CGFloat]] {
        pieces.compactMap { piece in
            guard let anchor = piece.centralAnchor else { return nil }
            return [anchor.x / piece.diameter.x, anchor.y / piece.diameter.y]
        }
    }

    public func disconnect() { pieces.forEach { $0.disconnect() } }

    public func onTranslate(_ f: @escaping (Piece, CGFloat, CGFloat) -> Void) {
        pieces.forEach { $0.onTranslate(f) }
    }

    public func onConnect(_ f: @escaping (Piece, Piece) -> Void) {
        pieces.forEach { $0.onConnect(f) }
    }

    public func onDisconnect(_ f: @escaping (Piece, Piece) -> Void) {
        pieces.forEach { $0.onDisconnect(f) }
    }

    public func onValid(_ f: @escaping ValidationListener) { validator.onValid(f) }

    public func shuffle(_ maxX: CGFloat, _ maxY: CGFloat) {
        disconnect()
        for piece in pieces {
            let x = CGFloat.random(in: 0...maxX)
            let y = CGFloat.random(in: 0...maxY)
            piece.relocateTo(x, y)
        }
        autoconnect()
    }

    public func shuffleWith(_ shuffler: Shuffler) {
        disconnect()
        shuffler(pieces)
        autoconnect()
    }

    public func reframe(_ min: Vector, _ max: Vector) {
        let leftOffstage = min.x - (pieces.compactMap { $0.centralAnchor }.map { $0.x }.min() ?? 0)
        let dx: CGFloat
        if leftOffstage > 0 {
            dx = leftOffstage
        } else {
            let rightOffstage = max.x - (pieces.compactMap { $0.centralAnchor }.map { $0.x }.max() ?? 0)
            dx = rightOffstage < 0 ? rightOffstage : 0
        }

        let upOffstage = min.y - (pieces.compactMap { $0.centralAnchor }.map { $0.y }.min() ?? 0)
        let dy: CGFloat
        if upOffstage > 0 {
            dy = upOffstage
        } else {
            let downOffstage = max.y - (pieces.compactMap { $0.centralAnchor }.map { $0.y }.max() ?? 0)
            dy = downOffstage < 0 ? downOffstage : 0
        }

        translate(dx, dy)
    }

    public func attachHorizontalConnectionRequirement(_ req: @escaping ConnectionRequirement) {
        horizontalConnector.attachRequirement(req)
    }

    public func attachVerticalConnectionRequirement(_ req: @escaping ConnectionRequirement) {
        verticalConnector.attachRequirement(req)
    }

    public func attachConnectionRequirement(_ req: @escaping ConnectionRequirement) {
        attachHorizontalConnectionRequirement(req)
        attachVerticalConnectionRequirement(req)
    }

    public func clearConnectionRequirements() {
        horizontalConnector.attachRequirement(noConnectionRequirements)
        verticalConnector.attachRequirement(noConnectionRequirements)
    }

    public func forceConnectionWhileDragging() { dragMode = .forceConnection }
    public func forceDisconnectionWhileDragging() { dragMode = .forceDisconnection }
    public func tryDisconnectionWhileDragging() { dragMode = .tryDisconnection }

    public func export(options: ExportOptions = ExportOptions()) -> [String: Any] {
        return [
            "pieceRadius": [
                "x": pieceRadius.x,
                "y": pieceRadius.y
            ],
            "proximity": proximity,
            "pieces": pieces.map { $0.export(compact: options.compact) }
        ]
    }

    public static func `import`(_ dump: [String: Any]) -> Puzzle {
        let pieceRadius = dump["pieceRadius"] as? [String: CGFloat]
        let proximity = dump["proximity"] as? CGFloat ?? 1
        let radius = pieceRadius.map { Vector(x: $0["x"] ?? 50, y: $0["y"] ?? 50) } ?? Vector(x: 50, y: 50)
        let puzzle = Puzzle(pieceRadius: radius.x, proximity: proximity)
        if let pieceDumps = dump["pieces"] as? [[String: Any]] {
            puzzle.addPieces(pieceDumps.map { Piece.import($0) })
        }
        puzzle.autoconnect()
        return puzzle
    }
}


