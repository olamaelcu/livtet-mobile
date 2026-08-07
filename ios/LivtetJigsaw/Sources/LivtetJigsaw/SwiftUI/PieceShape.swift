import SwiftUI

public struct PieceShape: Shape {
    let pieceSize: JigsawSize
    let structure: Structure
    let outline: ClassicOutline
    let softness: CGFloat
    let borderFill: Vector

    public init(pieceSize: JigsawSize, structure: Structure,
                outline: ClassicOutline = ClassicOutline(),
                softness: CGFloat = 0.18, borderFill: Vector = Vector(x: 10, y: 10)) {
        self.pieceSize = pieceSize
        self.structure = structure
        self.outline = outline
        self.softness = softness
        self.borderFill = borderFill
    }

    public func path(in rect: CGRect) -> Path {
        outline.path(size: pieceSize, structure: structure, softness: softness, borderFill: borderFill)
    }
}
