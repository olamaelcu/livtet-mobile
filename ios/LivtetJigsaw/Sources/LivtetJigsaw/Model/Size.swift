import CoreFoundation

public struct JigsawSize: Equatable {
    public let radius: Vector

    public var diameter: Vector {
        Vector(x: radius.x * 2, y: radius.y * 2)
    }

    public init(radius: Vector) {
        self.radius = radius
    }

    public static func radius(_ value: CGFloat) -> JigsawSize {
        JigsawSize(radius: Vector.cast(value))
    }

    public static func radius(_ value: Vector) -> JigsawSize {
        JigsawSize(radius: value)
    }

    public static func diameter(_ value: CGFloat) -> JigsawSize {
        let v = Vector.cast(value)
        return JigsawSize(radius: v / 2)
    }

    public static func diameter(_ value: Vector) -> JigsawSize {
        JigsawSize(radius: value / 2)
    }
}
