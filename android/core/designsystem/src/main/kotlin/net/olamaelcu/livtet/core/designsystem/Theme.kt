package net.olamaelcu.livtet.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
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
 */
@Composable
fun LivtetTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) livtetDarkColorScheme() else livtetLightColorScheme()

    MaterialTheme(colorScheme = colorScheme, typography = LivtetTypography, content = content)
}
