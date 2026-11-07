package net.olamaelcu.livtet

/**
 * What an "Accept all" run produced, in the words the screen shows.
 *
 * The run is the one place a reader can lose a book without noticing: it works through a list, and
 * some entries can fail while others succeed. Keeping the counting and the wording here — rather
 * than inline in an activity — is what makes the partial case testable, and the partial case is the
 * one that matters.
 */
data class AcceptAllResult(val added: Int, val failed: Int) {
    /** The line shown after the run. */
    val summary: String
        get() =
            when {
                added == 0 && failed == 0 -> "Nothing to add"
                failed == 0 -> "Added $added"
                added == 0 -> "Nothing could be added"
                else -> "Added $added \u00b7 $failed could not be added"
            }
}
