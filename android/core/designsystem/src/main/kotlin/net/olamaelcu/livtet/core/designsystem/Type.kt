package net.olamaelcu.livtet.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.olamaelcu.livtet.branding.BodyFamily
import net.olamaelcu.livtet.branding.CodeFamily
import net.olamaelcu.livtet.branding.HeadingFamily

/**
 * Livtet typography. Tightens the letter spacing and bumps the line height for body / title styles
 * — readers spend a lot of time looking at these on mid-tier Android phones, so small readability
 * wins matter.
 *
 * Display / headline / title styles use [HeadingFamily] (Geist Variable) to give readers a distinct
 * typographic anchor on chapter titles and section headers; body and label styles use [BodyFamily]
 * (Work Sans) for screen-readable running text; the smallest label style uses [CodeFamily]
 * (JetBrains Mono) for inventory ids and similar monospace strings that appear in lists.
 *
 * Kept as a single Typography object (not a per-screen concern) so tweaking the type scale is one
 * PR, not fifteen.
 */
val LivtetTypography: Typography =
    Typography(
        displayLarge = TextStyle(fontFamily = HeadingFamily, fontWeight = FontWeight.Medium),
        displayMedium = TextStyle(fontFamily = HeadingFamily, fontWeight = FontWeight.Medium),
        displaySmall = TextStyle(fontFamily = HeadingFamily, fontWeight = FontWeight.Medium),
        headlineLarge = TextStyle(fontFamily = HeadingFamily, fontWeight = FontWeight.Medium),
        headlineMedium = TextStyle(fontFamily = HeadingFamily, fontWeight = FontWeight.Medium),
        headlineSmall = TextStyle(fontFamily = HeadingFamily, fontWeight = FontWeight.Medium),
        titleLarge = TextStyle(fontFamily = HeadingFamily, fontWeight = FontWeight.Medium),
        titleMedium =
            TextStyle(
                fontFamily = BodyFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = BodyFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        bodyLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal),
        bodyMedium =
            TextStyle(
                fontFamily = BodyFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.25.sp,
            ),
        bodySmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontFamily = CodeFamily, fontWeight = FontWeight.Normal),
    )
