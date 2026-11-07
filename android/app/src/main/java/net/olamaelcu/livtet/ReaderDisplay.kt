package net.olamaelcu.livtet

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import net.olamaelcu.livtet.branding.livtetDarkColorScheme
import net.olamaelcu.livtet.branding.livtetLightColorScheme
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme

/** The page a book is read on. */
enum class ReaderTheme {
    LIGHT,
    SEPIA,
    DARK;

    /** The Compose scheme this theme paints the screen with. */
    fun colorScheme(): ColorScheme =
        when (this) {
            LIGHT -> livtetLightColorScheme()
            DARK -> livtetDarkColorScheme()
            // Sepia is the reader's own: warm paper with brown ink, which the brand
            // palette has no role for. Only the roles a page uses are replaced, so
            // anything else on screen stays the brand's.
            SEPIA ->
                livtetLightColorScheme()
                    .copy(
                        background = SepiaPaper,
                        surface = SepiaPaper,
                        surfaceVariant = SepiaPaperDeep,
                        onBackground = SepiaInk,
                        onSurface = SepiaInk,
                        onSurfaceVariant = SepiaInkMuted,
                    )
        }

    /** The same choice, as the toolkit spells it. */
    fun toNavigatorTheme(): Theme =
        when (this) {
            LIGHT -> Theme.LIGHT
            DARK -> Theme.DARK
            SEPIA -> Theme.SEPIA
        }
}

/**
 * How a reader likes a page: which paper, how large the type, how wide the margins.
 *
 * Steps rather than free values, because a reader adjusts towards a preference and never asks for
 * 1.13×: discrete choices are what can be remembered, shown and returned to. The encoding is what
 * lands in `client_settings` — `THEME|text|margin`.
 */
data class ReaderDisplay(
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val textStep: Int = 1,
    val marginStep: Int = 1,
) {
    /** Type scale this step asks for. */
    val textScale: Double
        get() = TEXT_STEPS[textStep.coerceIn(0, TEXT_STEPS.lastIndex)]

    /** Page margin multiplier this step asks for. */
    val marginScale: Double
        get() = MARGIN_STEPS[marginStep.coerceIn(0, MARGIN_STEPS.lastIndex)]

    fun withTheme(value: ReaderTheme): ReaderDisplay = copy(theme = value)

    /** Larger type; the last step is the ceiling. */
    fun largerText(): ReaderDisplay =
        copy(textStep = (textStep + 1).coerceAtMost(TEXT_STEPS.lastIndex))

    /** Smaller type; the first step is the floor. */
    fun smallerText(): ReaderDisplay = copy(textStep = (textStep - 1).coerceAtLeast(0))

    fun withMargins(step: Int): ReaderDisplay =
        copy(marginStep = step.coerceIn(0, MARGIN_STEPS.lastIndex))

    /** The toolkit's preferences for this display. */
    fun toPreferences(): EpubPreferences =
        EpubPreferences(
            theme = theme.toNavigatorTheme(),
            fontSize = BASE_FONT_SIZE * textScale,
            pageMargins = marginScale,
        )

    fun encode(): String = "${theme.name}|$textStep|$marginStep"

    companion object {
        /** Type scale per step, from smallest to largest. */
        val TEXT_STEPS: List<Double> = listOf(0.85, 1.0, 1.15, 1.3)

        /** Margin multiplier per step: tight, normal, wide. */
        val MARGIN_STEPS: List<Double> = listOf(0.5, 1.0, 1.6)

        /** Base type size before the step's scale, in the toolkit's units. */
        const val BASE_FONT_SIZE: Double = 18.0

        /** What a reader gets before touching anything. */
        val Default: ReaderDisplay = ReaderDisplay()

        /**
         * Read a stored display, tolerating anything.
         *
         * A row written by an older build, or edited by hand, must not stop a book from opening:
         * unknown themes fall back to light and out-of-range steps are clamped to the nearest one
         * this build offers.
         */
        fun decode(raw: String?): ReaderDisplay {
            val parts = raw?.split('|')?.map { it.trim() } ?: return Default
            if (parts.size != 3) return Default
            val theme = ReaderTheme.entries.firstOrNull { it.name.equals(parts[0], true) }
            val text = parts[1].toIntOrNull()
            val margin = parts[2].toIntOrNull()
            if (theme == null || text == null || margin == null) return Default
            return ReaderDisplay(
                theme = theme,
                textStep = text.coerceIn(0, TEXT_STEPS.lastIndex),
                marginStep = margin.coerceIn(0, MARGIN_STEPS.lastIndex),
            )
        }
    }
}

private val SepiaPaper = Color(0xFFF4ECD8)
private val SepiaPaperDeep = Color(0xFFE8DFC6)
private val SepiaInk = Color(0xFF5B4636)
private val SepiaInkMuted = Color(0xFF7A6450)
