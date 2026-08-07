import CoreFoundation

public struct Anchor: Equatable, Codable {
    public var x: CGFloat
    public var y: CGFloat

    public init(x: CGFloat, y: CGFloat) {
        self.x = x
        self.y = y
    }

    public func translated(dx: CGFloat, dy: CGFloat) -> Anchor {
        Anchor(x: x + dx, y: y + dy)
    }

    public mutating func translate(_ dx: CGFloat, _ dy: CGFloat) {
        x += dx
        y += dy
    }

    public func diff(_ other: Anchor) -> (CGFloat, CGFloat) {
        (x - other.x, y - other.y)
    }

    public func isAt(_ x: CGFloat, _ y: CGFloat) -> Bool {
        abs(self.x - x) <= .ulpOfOne && abs(self.y - y) <= .ulpOfOne
    }

    public func asVector() -> Vector {
        Vector(x: x, y: y)
    }

    public func asPair() -> (CGFloat, CGFloat) {
        (x, y)
    }
}
