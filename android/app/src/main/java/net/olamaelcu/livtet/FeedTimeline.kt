package net.olamaelcu.livtet

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The feed as rows the screen draws.
 *
 * A page of the feed is mostly one reader shelving a series in a burst: in a live sample 25 of 29
 * items were `to-read` shelvings from four handles. Rows of one shelving each therefore drown the
 * reviews, so consecutive shelvings of the same status by one reader on one day collapse into a
 * single run card. Reviews never collapse — the text is the reason to show them.
 *
 * Pure on purpose: [now] and [zone] are parameters, so the labels are a function of the data and
 * the shaping is testable without a device.
 */
sealed interface FeedRow {
    /** Stable across pages, so a list can key on it. */
    val key: String
}

data class FeedHeader(val day: LocalDate) : FeedRow {
    override val key: String = "header:$day"
}

data class FeedEvent(val entry: FeedEntry, override val key: String) : FeedRow

data class FeedRun(
    val author: String,
    val status: String?,
    val entries: List<FeedEntry>,
    override val key: String,
) : FeedRow

/** How many covers a run card's strip shows before it says "+N". */
const val FEED_RUN_COVERS = 3

fun timeline(entries: List<FeedEntry>, zone: ZoneId = ZoneId.systemDefault()): List<FeedRow> {
    val rows = mutableListOf<FeedRow>()
    var headingDay: LocalDate? = null
    var index = 0
    while (index < entries.size) {
        val entry = entries[index]
        val day = dayOf(entry, zone)
        if (day != null && day != headingDay) {
            rows += FeedHeader(day)
            headingDay = day
        }
        val runnable = entry.kind == FeedEntry.Kind.SHELVING && entry.author != null
        if (!runnable) {
            rows += FeedEvent(entry, eventKey(entry, index))
            index++
            continue
        }
        var end = index + 1
        while (end < entries.size && joins(entry, entries[end], day, zone)) {
            end++
        }
        val run = entries.subList(index, end)
        rows +=
            if (run.size == 1) {
                FeedEvent(entry, eventKey(entry, index))
            } else {
                FeedRun(entry.author!!, entry.status, run.toList(), runKey(run.first(), index))
            }
        index = end
    }
    return rows
}

/**
 * Whether [next] belongs to the run that [first] starts.
 *
 * The day matters: "wants to read" yesterday and the same reader shelving again today are two runs,
 * because the day header between them would otherwise sit inside one card.
 */
private fun joins(first: FeedEntry, next: FeedEntry, day: LocalDate?, zone: ZoneId): Boolean {
    if (next.kind != FeedEntry.Kind.SHELVING) return false
    if (next.author == null || next.author != first.author) return false
    if (next.status != first.status) return false
    val nextDay = dayOf(next, zone)
    return day == null || nextDay == null || nextDay == day
}

private fun eventKey(entry: FeedEntry, index: Int): String = entry.uri ?: "event:$index"

private fun runKey(first: FeedEntry, index: Int): String = "run:${first.uri ?: index}"

private fun dayOf(entry: FeedEntry, zone: ZoneId): LocalDate? =
    instant(entry.createdAt)?.atZone(zone)?.toLocalDate()

private fun instant(createdAt: String?): Instant? = createdAt?.let {
    runCatching { Instant.parse(it) }.getOrNull()
}

/** The verb an entry adds to its book, from the shelf it was moved to. */
fun verb(entry: FeedEntry): String =
    when (entry.kind) {
        FeedEntry.Kind.REVIEW -> "reviewed"
        FeedEntry.Kind.RECOMMENDATION -> "recommended"
        FeedEntry.Kind.UNKNOWN -> "shared"
        FeedEntry.Kind.SHELVING -> shelfVerb(entry.status, entry.shelf) ?: "shelved"
    }

/** The verb a run of [count] shelvings adds, phrased for the plural. */
fun runVerb(status: String?): String =
    when (status) {
        "to-read" -> "added to want to read"
        "reading" -> "started reading"
        "read" -> "finished"
        else -> "shelved"
    }

private fun shelfVerb(status: String?, shelf: String?): String? =
    when (status) {
        "to-read" -> "wants to read"
        "reading" -> "is reading"
        "read" -> "finished"
        else -> shelf?.removePrefix("shelf-status-")?.takeIf { it.isNotEmpty() }?.replace('-', ' ')
    }

/**
 * A handle without the hosting domain: `nnachameu.bsky.social` reads as `nnachameu`, which is what
 * fits beside a time on a card, and the full handle is still the label's content description.
 */
fun displayHandle(handle: String?): String =
    handle?.substringBefore(".bsky.social")?.takeIf { it.isNotEmpty() } ?: "Someone"

/** "Today", "Yesterday", or a short date — in the reader's locale. */
fun dayLabel(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String =
    when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DateTimeFormatter.ofPattern("EEE d MMM", locale).format(day)
    }

/** How long ago an entry was created, or null when the route sent no timestamp. */
fun relativeTime(createdAt: String?, now: Instant): String? {
    val instant = instant(createdAt) ?: return null
    val seconds = Duration.between(instant, now).seconds
    return when {
        seconds < 60 -> "now"
        seconds < 3_600 -> "${seconds / 60} min"
        seconds < 86_400 -> "${seconds / 3_600} h"
        seconds < 7 * 86_400 -> "${seconds / 86_400} d"
        else ->
            DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
                .format(instant.atZone(ZoneId.systemDefault()))
    }
}

/** A rating as readers write it: `4` and `4.5`, never `4.0`. */
fun formatRating(rating: Double): String =
    if (rating % 1.0 == 0.0) rating.toInt().toString() else String.format(Locale.US, "%.1f", rating)
