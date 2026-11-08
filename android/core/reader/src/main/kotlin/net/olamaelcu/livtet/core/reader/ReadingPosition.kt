package net.olamaelcu.livtet.core.reader

import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

/**
 * A reading position in the shape the store and the peer both carry.
 *
 * `reading_progress.last_location` is free text, and the sync change log ships it unchanged, so the
 * toolkit's own `Locator` JSON is what goes in it: the same position read here, written to the
 * store, and pulled by the desktop means the same thing on both sides without a translation table.
 */
data class ReadingPosition(
    // / The toolkit's `Locator` as JSON.
    val locatorJson: String,
    // / Progress through the publication, in `[0, 1]`.
    val progression: Double,
) {
    companion object {
        // / The position a navigator reports, in storable form.
        fun of(locator: Locator): ReadingPosition =
            ReadingPosition(
                locatorJson = locator.toJSON().toString(),
                progression = locator.locations.totalProgression ?: 0.0,
            )

        /**
         * Parse a stored position back into a `Locator`.
         *
         * Returns null for anything that no longer parses — a row written by an older build, or a
         * hand-edited one. The reading screen then starts at the beginning instead of failing to
         * open.
         */
        fun locator(storedJson: String?): Locator? {
            val json = storedJson?.takeIf { it.isNotBlank() } ?: return null
            return runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()
        }
    }
}
