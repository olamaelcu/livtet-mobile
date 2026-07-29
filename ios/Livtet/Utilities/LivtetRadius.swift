import SwiftUI

/// Corner-radius tokens. Mirrors `radius.*` in `packages/livtet-design-tokens/tokens.json`.
///
/// Use [LivtetRadius.l] for cards and large surfaces, [LivtetRadius.m] for buttons and chips,
/// and [LivtetRadius.s] for small inline elements (badges, list-item tiles).
public enum LivtetRadius {
    // swiftlint:disable identifier_name
    public static let s: CGFloat = 4
    public static let m: CGFloat = 8
    public static let l: CGFloat = 16
    // swiftlint:enable identifier_name
}
