import SwiftUI
import UIKit

// Design tokens for the iOS app.
//
// This file replaces a checked-in absolute symlink that pointed at
// `/home/vrgl/Code/olamaelcu/livtet/packages/livtet-design-tokens/generated/ios/Livtet/Utilities/LucideIcons.swift`,
// a path that only exists on the original author's machine. The token package
// is not present in any local checkout, so the generated output cannot be
// reproduced here. The tokens below are restored from the authoritative
// sources that are on disk:
//
// - `Color.brand` uses the sRGB components the token generator writes into its
//   generated `brand.colorset` (light `#0F271A`, dark `#64C491`, from
//   `tokens.json` -> `color.brand.{light,dark}.foreground`). The values are
//   inlined rather than looked up as `Color("brand")` because the app's
//   `Resources/Assets.xcassets` ships no `brand` colorset.
// - `Image.lucide*` maps the tokens the app actually references onto the
//   template-rendered `lucide-*.imageset` assets in
//   `Resources/Assets.xcassets` (`tokens.json` -> `icon.set.ios`).
// - `Font.livtet*` uses the brand families the app registers at launch
//   (`LivtetApp.registerBrandFonts()` and `UIAppFonts`): Work Sans for body,
//   Geist for headings, JetBrains Mono for code.
//
// If `packages/livtet-design-tokens` becomes available again, re-run its iOS
// generator and replace this file with its output.

// MARK: - Color tokens

public extension ShapeStyle where Self == Color {
    /// Brand accent ("Library Green"): dark ink on light surfaces, the mint
    /// ramp step on dark surfaces.
    static var brand: Color {
        Color(
            uiColor: UIColor { traits in
                traits.userInterfaceStyle == .dark
                    ? UIColor(
                        red: 0.39215686274509803,
                        green: 0.7725490196078432,
                        blue: 0.5686274509803921,
                        alpha: 1
                    )
                    : UIColor(
                        red: 0.058823529411764705,
                        green: 0.15294117647058825,
                        blue: 0.10196078431372549,
                        alpha: 1
                    )
            }
        )
    }
}

// MARK: - Typography tokens

public extension Font {
    /// Body copy. Work Sans (`work_sans.ttf`).
    static func livtetBody(size: CGFloat = 16, weight: Font.Weight = .regular) -> Font {
        .custom("Work Sans", size: size, relativeTo: .body).weight(weight)
    }

    /// Headings and titles. Geist (`geist.ttf`).
    static func livtetHeading(size: CGFloat = 24, weight: Font.Weight = .semibold) -> Font {
        .custom("Geist", size: size, relativeTo: .title).weight(weight)
    }

    /// Monospaced text and numbers. JetBrains Mono (`jetbrains_mono.ttf`).
    static func livtetCode(size: CGFloat = 14) -> Font {
        .custom("JetBrains Mono", size: size, relativeTo: .body)
    }
}

// MARK: - Icon tokens

public extension Image {
    static let lucideBookOpen = Image("lucide-book-open")
    static let lucideBookPlus = Image("lucide-book-plus")
    static let lucideCircleCheck = Image("lucide-circle-check")
    static let lucideInfo = Image("lucide-info")
}
