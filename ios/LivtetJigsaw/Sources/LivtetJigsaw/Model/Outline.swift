import SwiftUI

public protocol Outline {
    func path(size: JigsawSize, structure: Structure, softness: CGFloat, borderFill: Vector) -> Path
}

public struct ClassicOutline: Outline {
    private let bezelize: Bool
    private let bezelDepth: CGFloat
    private let insertDepth: CGFloat
    private let borderLength: CGFloat

    public init(
        bezelize: Bool = false,
        bezelDepth: CGFloat = 2.0 / 5.0,
        insertDepth: CGFloat = 4.0 / 5.0,
        borderLength: CGFloat = 1.0 / 3.0
    ) {
        self.bezelize = bezelize
        self.bezelDepth = bezelDepth
        self.insertDepth = insertDepth
        self.borderLength = borderLength
    }

    public func path(
        size: JigsawSize,
        structure: Structure,
        softness: CGFloat = 0.18,
        borderFill: Vector = .zero
    ) -> Path {
        let diameter = size.diameter
        let fullSize = Vector(x: diameter.x + 2 * borderFill.x,
                              y: diameter.y + 2 * borderFill.y)

        let axisLength = min(fullSize.x, fullSize.y)
        let r = (axisLength * (1.0 - 2.0 * borderLength) * 100).rounded(.towardZero) / 100
        let s = Vector.minus(fullSize, Vector.cast(r)) / 2
        let o = r * insertDepth
        let b = min(s.x, s.y) * bezelDepth

        let b0 = bezelize && structure.left.isNone && structure.up.isNone
        let b1 = bezelize && structure.left.isNone && structure.down.isNone
        let b2 = bezelize && structure.right.isNone && structure.down.isNone
        let b3 = bezelize && structure.right.isNone && structure.up.isNone

        func nx(_ c: Bool) -> CGFloat { c ? b : 0 }
        func ny(_ c: Bool) -> CGFloat { c ? b : 0 }

        let pts = generatePoints(
            structure: structure,
            r: r, s: s, o: o,
            nx: nx, ny: ny,
            b0: b0, b1: b1, b2: b2, b3: b3
        )

        let halfX = fullSize.x / 2
        let halfY = fullSize.y / 2

        var path = Path()
        guard !pts.isEmpty else { return path }

        let centered = pts.map { ($0.0 - halfX, $0.1 - halfY) }

        path.move(to: CGPoint(x: centered[0].0, y: centered[0].1))
        var i = 1
        while i + 2 < centered.count {
            let cp1 = centered[i]
            let cp2 = centered[i + 1]
            let end = centered[i + 2]
            path.addCurve(to: CGPoint(x: end.0, y: end.1),
                          control1: CGPoint(x: cp1.0, y: cp1.1),
                          control2: CGPoint(x: cp2.0, y: cp2.1))
            i += 3
        }
        while i < centered.count {
            path.addLine(to: CGPoint(x: centered[i].0, y: centered[i].1))
            i += 1
        }
        path.closeSubpath()
        return path
    }

    // swiftlint:disable:next function_parameter_count
    private func generatePoints(
        structure: Structure,
        r: CGFloat, s: Vector, o: CGFloat,
        nx: (Bool) -> CGFloat, ny: (Bool) -> CGFloat,
        b0: Bool, b1: Bool, b2: Bool, b3: Bool
    ) -> [(CGFloat, CGFloat)] {
        let r2sx = r + 2 * s.x
        let r2sy = r + 2 * s.y
        let rsx = r + s.x
        let rsy = r + s.y

        var pts: [(CGFloat, CGFloat)] = []

        // Start: top-left corner
        pts.append((nx(b0), 0))

        // b0 bezel (top-left corner rounding)
        if b0 {
            pts.append((0, 0))
            pts.append((0, 0))
            pts.append((0, b))
        }

        // === LEFT EDGE (top to bottom) ===
        pts.append((0, ny(b0)))
        pts.append((0, s.y))
        pts.append((0, s.y))

        switch structure.left {
        case .tab:
            pts.append((-o, s.y))
            pts.append((-o, rsy))
        case .slot:
            pts.append((o, s.y))
            pts.append((o, rsy))
        case .none:
            pts.append((0, s.y))
            pts.append((0, rsy))
        }
        pts.append((0, rsy))
        pts.append((0, r2sy))
        pts.append((0, r2sy - ny(b1)))

        // b1 bezel (bottom-left corner rounding)
        if b1 {
            pts.append((0, r2sy))
            pts.append((0, r2sy))
            pts.append((b, r2sy))
        }

        // === BOTTOM EDGE (left to right) ===
        pts.append((nx(b1), r2sy))
        pts.append((s.x, r2sy))
        pts.append((s.x, r2sy))

        switch structure.down {
        case .tab:
            pts.append((s.x, r2sy + o))
            pts.append((rsx, r2sy + o))
        case .slot:
            pts.append((s.x, r2sy - o))
            pts.append((rsx, r2sy - o))
        case .none:
            pts.append((s.x, r2sy))
            pts.append((rsx, r2sy))
        }
        pts.append((rsx, r2sy))
        pts.append((r2sx, r2sy))
        pts.append((r2sx - nx(b2), r2sy))

        // b2 bezel (bottom-right corner rounding)
        if b2 {
            pts.append((r2sx, r2sy))
            pts.append((r2sx, r2sy))
            pts.append((r2sx, r2sy - b))
        }

        // === RIGHT EDGE (bottom to top) ===
        pts.append((r2sx, r2sy - ny(b2)))
        pts.append((r2sx, rsy))
        pts.append((r2sx, rsy))

        switch structure.right {
        case .tab:
            pts.append((r2sx + o, rsy))
            pts.append((r2sx + o, s.y))
        case .slot:
            pts.append((r2sx - o, rsy))
            pts.append((r2sx - o, s.y))
        case .none:
            pts.append((r2sx, rsy))
            pts.append((r2sx, s.y))
        }
        pts.append((r2sx, s.y))
        pts.append((r2sx, 0))
        pts.append((r2sx, ny(b3)))

        // b3 bezel (top-right corner rounding)
        if b3 {
            pts.append((r2sx, 0))
            pts.append((r2sx, 0))
            pts.append((r2sx - b, 0))
        }

        // === TOP EDGE (right to left) ===
        pts.append((r2sx - nx(b3), 0))
        pts.append((rsx, 0))
        pts.append((rsx, 0))

        switch structure.up {
        case .tab:
            pts.append((rsx, -o))
            pts.append((s.x, -o))
        case .slot:
            pts.append((rsx, o))
            pts.append((s.x, o))
        case .none:
            pts.append((rsx, 0))
            pts.append((s.x, 0))
        }
        pts.append((s.x, 0))
        pts.append((0, 0))
        pts.append((b0 ? b : 0, 0))

        return pts
    }
}
