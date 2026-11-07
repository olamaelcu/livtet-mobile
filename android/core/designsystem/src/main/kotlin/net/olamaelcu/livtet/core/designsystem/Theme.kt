package net.olamaelcu.livtet.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.olamaelcu.livtet.branding.livtetDarkColorScheme
import net.olamaelcu.livtet.branding.livtetLightColorScheme

/**
 * Livtet theme. Wires the design-token color schemes ([livtetLightColorScheme] /
 * [livtetDarkColorScheme], generated from `tokens.json` via `packages/livtet-design-tokens`) and
 * the brand typography ([LivtetTypography]) into Material 3.
 *
 * Dark mode flips on `isSystemInDarkTheme()`. Screens should consume colors through
 * `MaterialTheme.colorScheme.*` rather than referencing [LivtetColors] directly so that role-based
 * theming (M3 expressiveness) remains intact.
 *
 * [colorScheme] lets a screen substitute its own roles — the reader does this for its sepia page,
 * which is a reading surface rather than a brand surface. Everything not given stays the brand's.
 */
@Composable
fun LivtetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorScheme: ColorScheme? = null,
    content: @Composable () -> Unit,
) {
    val resolved =
        colorScheme ?: if (darkTheme) livtetDarkColorScheme() else livtetLightColorScheme()

    MaterialTheme(colorScheme = resolved, typography = LivtetTypography, content = content)
}
