import CoreFoundation

public struct Pair {
    public static func isNull(_ x: CGFloat, _ y: CGFloat) -> Bool {
        abs(x) <= .ulpOfOne && abs(y) <= .ulpOfOne
    }

    public static func eq(_ a: (CGFloat, CGFloat), _ b: (CGFloat, CGFloat)) -> Bool {
        abs(a.0 - b.0) <= .ulpOfOne && abs(a.1 - b.1) <= .ulpOfOne
    }
}
