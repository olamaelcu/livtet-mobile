import CoreFoundation

public typealias ValidationListener = () -> Void

public protocol Validator: AnyObject {
    var valid: Bool { get set }
    func isValid(_ puzzle: Puzzle) -> Bool
    func validate(_ puzzle: Puzzle)
    func onValid(_ f: @escaping ValidationListener)
}

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

    public func reframe(_ min: Vector, _ max: Vector) {
        let minX = pieces.compactMap { $0.centralAnchor?.x }.min() ?? 0
        let minY = pieces.compactMap { $0.centralAnchor?.y }.min() ?? 0
        var dx: CGFloat = 0
        var dy: CGFloat = 0
        if minX < min.x { dx = min.x - minX }
        if minY < min.y { dy = min.y - minY }
        translate(dx, dy)
    }

    public func shuffleWith(_ shuffler: (inout [Piece]) -> Void) {
        disconnect()
        shuffler(&pieces)
        autoconnect()
    }
}

public final class NullValidator: Validator {
    public var valid: Bool = false
    private var listeners: [ValidationListener] = []

    public init() {}

    public func isValid(_ puzzle: Puzzle) -> Bool { false }

    public func validate(_ puzzle: Puzzle) {}

    public func onValid(_ f: @escaping ValidationListener) { listeners.append(f) }
}
