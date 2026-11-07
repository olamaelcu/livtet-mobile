package net.olamaelcu.livtet

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reader's display choices: what is stored, what is clamped, what is refused.
 *
 * These are the values that decide how a page looks on the next launch, so a decode that guessed
 * would change a reader's page without them asking. Anything unreadable falls back to the default,
 * and anything out of range is clamped to a step that exists.
 */
class ReaderDisplayTest {
    @Test
    fun a_stored_display_round_trips() {
        val stored = ReaderDisplay(ReaderTheme.SEPIA, textStep = 2, marginStep = 0)
        assertEquals(stored, ReaderDisplay.decode(stored.encode()))
        assertEquals("SEPIA|2|0", stored.encode())
    }

    @Test
    fun the_default_is_light_normal_type_normal_margins() {
        val default = ReaderDisplay.Default
        assertEquals(ReaderTheme.LIGHT, default.theme)
        assertEquals(1.0, default.textScale, 0.001)
        assertEquals(1.0, default.marginScale, 0.001)
    }

    @Test
    fun a_missing_or_unreadable_value_falls_back_to_the_default() {
        for (raw in listOf(null, "", "garbage", "SEPIA|2", "SEPIA|x|1", "SEPIA|1|y")) {
            assertEquals("raw=$raw", ReaderDisplay.Default, ReaderDisplay.decode(raw))
        }
    }

    @Test
    fun an_unknown_theme_falls_back_rather_than_failing() {
        // A theme this build does not have (a spectrum/nautical mode from elsewhere).
        assertEquals(ReaderDisplay.Default, ReaderDisplay.decode("NAUTICAL|1|1"))
    }

    @Test
    fun steps_out_of_range_are_clamped_to_the_nearest_that_exists() {
        val high = ReaderDisplay.decode("DARK|99|99")
        assertEquals(ReaderDisplay.TEXT_STEPS.lastIndex, high.textStep)
        assertEquals(ReaderDisplay.MARGIN_STEPS.lastIndex, high.marginStep)

        val low = ReaderDisplay.decode("DARK|-5|-5")
        assertEquals(0, low.textStep)
        assertEquals(0, low.marginStep)
    }

    @Test
    fun type_and_margins_stop_at_their_ends() {
        var display = ReaderDisplay.Default
        repeat(ReaderDisplay.TEXT_STEPS.size + 3) { display = display.largerText() }
        assertEquals(ReaderDisplay.TEXT_STEPS.lastIndex, display.textStep)

        repeat(ReaderDisplay.TEXT_STEPS.size + 3) { display = display.smallerText() }
        assertEquals(0, display.textStep)

        assertEquals(ReaderDisplay.MARGIN_STEPS.lastIndex, display.withMargins(42).marginStep)
        assertEquals(0, display.withMargins(-42).marginStep)
    }
}
