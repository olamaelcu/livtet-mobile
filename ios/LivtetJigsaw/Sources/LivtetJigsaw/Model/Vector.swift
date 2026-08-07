import CoreFoundation

public struct Vector: Equatable, Codable, AdditiveArithmetic {
    public let x: CGFloat
    public let y: CGFloat

    public init(x: CGFloat, y: CGFloat) {
        self.x = x
        self.y = y
    }

    public static let zero = Vector(x: 0, y: 0)

    public static func + (l: Vector, r: Vector) -> Vector {
        Vector(x: l.x + r.x, y: l.y + r.y)
    }

    public static func - (l: Vector, r: Vector) -> Vector {
        Vector(x: l.x - r.x, y: l.y - r.y)
    }

    public static func * (l: Vector, r: CGFloat) -> Vector {
        Vector(x: l.x * r, y: l.y * r)
    }

    public static func / (l: Vector, r: CGFloat) -> Vector {
        Vector(x: l.x / r, y: l.y / r)
    }

    public static func cast(_ v: CGFloat) -> Vector {
        Vector(x: v, y: v)
    }

    public static func cast(_ v: Int) -> Vector {
        let c = CGFloat(v)
        return Vector(x: c, y: c)
    }

    public static func plus(_ a: Vector, _ b: Vector) -> Vector {
        a + b
    }

    public static func minus(_ a: Vector, _ b: Vector) -> Vector {
        a - b
    }

    public static func multiply(_ a: Vector, _ b: Vector) -> Vector {
        Vector(x: a.x * b.x, y: a.y * b.y)
    }

    public static func copy(_ v: Vector) -> Vector {
        v
    }

    public static func distance(_ a: Vector, _ b: Vector) -> CGFloat {
        let dx = a.x - b.x
        let dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    public static func isClose(_ a: Vector, _ b: Vector, tolerance: CGFloat) -> Bool {
        abs(a.x - b.x) <= tolerance && abs(a.y - b.y) <= tolerance
    }
}
