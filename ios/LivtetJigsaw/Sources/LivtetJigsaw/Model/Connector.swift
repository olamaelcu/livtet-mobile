import CoreFoundation

public typealias ConnectionRequirement = (Piece, Piece) -> Bool
public let noConnectionRequirements: ConnectionRequirement = { _, _ in true }

public protocol Connector: AnyObject {
    func closeTo(_ a: Piece, _ b: Piece, proximity: CGFloat) -> Bool
    func match(_ a: Piece, _ b: Piece) -> Bool
    func canConnectWith(_ a: Piece, _ b: Piece, proximity: CGFloat) -> Bool
    func connectWith(_ a: Piece, _ b: Piece, proximity: CGFloat, back: Bool)
    func attract(_ a: Piece, _ b: Piece, back: Bool)
}

public final class HorizontalConnector: Connector {
    private var requirement: ConnectionRequirement = noConnectionRequirements

    public init() {}

    public func closeTo(_ a: Piece, _ b: Piece, proximity: CGFloat) -> Bool {
        let forward = Vector.isClose(a.rightAnchor.asVector(), b.leftAnchor.asVector(), tolerance: proximity)
        let reverse = Vector.isClose(b.rightAnchor.asVector(), a.leftAnchor.asVector(), tolerance: proximity)
        return forward || reverse
    }

    public func match(_ a: Piece, _ b: Piece) -> Bool {
        (a.right.complement == b.left) || (a.left.complement == b.right)
    }

    public func canConnectWith(_ a: Piece, _ b: Piece, proximity: CGFloat) -> Bool {
        closeTo(a, b, proximity: proximity) && (match(a, b) || a.connected || b.connected) && requirement(a, b)
    }

    public func connectWith(_ a: Piece, _ b: Piece, proximity: CGFloat, back: Bool) {
        if a.right.complement == b.left {
            a.rightConnection = b
            b.leftConnection = a
            a.fireConnect(b)
            b.fireConnect(a)
        }
        if back && a.left.complement == b.right {
            a.leftConnection = b
            b.rightConnection = a
        }
    }

    public func attract(_ a: Piece, _ b: Piece, back: Bool) {
        if a.right.complement == b.left {
            b.recenterAround(a.rightAnchor)
            connectWith(a, b, proximity: 0, back: back)
        }
    }

    public func attachRequirement(_ req: @escaping ConnectionRequirement) {
        requirement = req
    }
}

public final class VerticalConnector: Connector {
    private var requirement: ConnectionRequirement = noConnectionRequirements

    public init() {}

    public func closeTo(_ a: Piece, _ b: Piece, proximity: CGFloat) -> Bool {
        let forward = Vector.isClose(a.downAnchor.asVector(), b.upAnchor.asVector(), tolerance: proximity)
        let reverse = Vector.isClose(b.downAnchor.asVector(), a.upAnchor.asVector(), tolerance: proximity)
        return forward || reverse
    }

    public func match(_ a: Piece, _ b: Piece) -> Bool {
        (a.down.complement == b.up) || (a.up.complement == b.down)
    }

    public func canConnectWith(_ a: Piece, _ b: Piece, proximity: CGFloat) -> Bool {
        closeTo(a, b, proximity: proximity) && (match(a, b) || a.connected || b.connected) && requirement(a, b)
    }

    public func connectWith(_ a: Piece, _ b: Piece, proximity: CGFloat, back: Bool) {
        if a.down.complement == b.up {
            a.downConnection = b
            b.upConnection = a
            a.fireConnect(b)
            b.fireConnect(a)
        }
        if back && a.up.complement == b.down {
            a.upConnection = b
            b.downConnection = a
        }
    }

    public func attract(_ a: Piece, _ b: Piece, back: Bool) {
        if a.down.complement == b.up {
            b.recenterAround(a.downAnchor)
            connectWith(a, b, proximity: 0, back: back)
        }
    }

    public func attachRequirement(_ req: @escaping ConnectionRequirement) {
        requirement = req
    }
}
