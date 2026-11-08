package net.olamaelcu.livtet

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The feed's shaping is where a redesign goes wrong quietly: a run that merges two readers, or a
 * day header that lands in the middle of one card, looks like a design choice rather than a bug.
 */
class FeedTimelineTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 11, 7)

    private fun entry(
        uri: String,
        kind: FeedEntry.Kind = FeedEntry.Kind.SHELVING,
        author: String? = "nnachameu.bsky.social",
        status: String? = "to-read",
        createdAt: String? = "2026-11-07T14:00:00Z",
        title: String? = "A book",
    ) =
        FeedEntry(
            uri = uri,
            kind = kind,
            author = author,
            bookTitle = title,
            bookCoverUrl = null,
            bookUri = null,
            text = if (kind == FeedEntry.Kind.REVIEW) "a review" else null,
            rating = if (kind == FeedEntry.Kind.REVIEW) 4.0 else null,
            shelf = status?.let { "shelf-status-$it" },
            status = status,
            createdAt = createdAt,
        )

    @Test
    fun `consecutive shelvings of one status collapse into one run`() {
        val rows = timeline(listOf(entry("a"), entry("b"), entry("c")), zone)

        assertEquals(2, rows.size)
        assertTrue(rows[0] is FeedHeader)
        val run = rows[1] as FeedRun
        assertEquals(listOf("a", "b", "c"), run.entries.map { it.uri })
        assertEquals("to-read", run.status)
    }

    @Test
    fun `a different shelf ends the run`() {
        val rows = timeline(listOf(entry("a"), entry("b", status = "read")), zone)

        assertEquals(2, rows.count { it is FeedRun || it is FeedEvent })
        assertTrue((rows[1] as FeedEvent).entry.uri == "a")
        assertTrue((rows[2] as FeedEvent).entry.uri == "b")
    }

    @Test
    fun `a different reader ends the run`() {
        val rows = timeline(listOf(entry("a"), entry("b", author = "someone.bsky.social")), zone)

        assertEquals(2, rows.count { it is FeedEvent })
        assertEquals("nnachameu.bsky.social", (rows[1] as FeedEvent).entry.author)
    }

    @Test
    fun `a review never joins a run, even by the same reader`() {
        val rows =
            timeline(
                listOf(entry("a"), entry("rev", kind = FeedEntry.Kind.REVIEW), entry("b")),
                zone,
            )

        // "a" alone stays a single event: a run of one is not a run, so no run exists here at all.
        assertEquals(0, rows.count { it is FeedRun })
        assertTrue(rows[1] is FeedEvent)
        assertEquals("a", (rows[1] as FeedEvent).entry.uri)
        assertEquals("rev", (rows[2] as FeedEvent).entry.uri)
        assertEquals("b", (rows[3] as FeedEvent).entry.uri)
    }

    @Test
    fun `a new day heads the list and splits the run`() {
        val rows =
            timeline(
                listOf(entry("today"), entry("yesterday", createdAt = "2026-11-06T20:00:00Z")),
                zone,
            )

        assertEquals(2, rows.count { it is FeedHeader })
        assertEquals(day, (rows[0] as FeedHeader).day)
        assertEquals(day.minusDays(1), (rows[2] as FeedHeader).day)
        assertEquals(2, rows.count { it is FeedEvent })
    }

    @Test
    fun `an entry without a timestamp keeps the day it is listed under`() {
        val rows = timeline(listOf(entry("a"), entry("b", createdAt = null)), zone)

        assertEquals(1, rows.count { it is FeedHeader })
        // The two shelvings are one run, so the untimed entry cannot open a header of its own.
        assertEquals(2, rows.size)
        assertEquals(2, (rows[1] as FeedRun).entries.size)
    }

    @Test
    fun `relative times read as durations and then as dates`() {
        val now = Instant.parse("2026-11-07T14:00:00Z")

        assertEquals("now", relativeTime("2026-11-07T13:59:30Z", now))
        assertEquals("5 min", relativeTime("2026-11-07T13:55:00Z", now))
        assertEquals("3 h", relativeTime("2026-11-07T11:00:00Z", now))
        assertEquals("2 d", relativeTime("2026-11-05T14:00:00Z", now))
        // Older than a week: a date, not a running count of days.
        assertTrue(relativeTime("2026-10-01T14:00:00Z", now)!!.contains("Oct"))
        assertNull(relativeTime(null, now))
        assertNull(relativeTime("not a timestamp", now))
    }

    @Test
    fun `ratings lose the trailing zero and handles lose the host`() {
        assertEquals("4", formatRating(4.0))
        assertEquals("4.5", formatRating(4.5))
        assertEquals("nnachameu", displayHandle("nnachameu.bsky.social"))
        assertEquals("Someone", displayHandle(null))
    }

    @Test
    fun `verbs name the shelf the book went to`() {
        assertEquals("wants to read", verb(entry("a", status = "to-read")))
        assertEquals("is reading", verb(entry("a", status = "reading")))
        assertEquals("finished", verb(entry("a", status = "read")))
        assertEquals("reviewed", verb(entry("a", kind = FeedEntry.Kind.REVIEW)))
        assertEquals("recommended", verb(entry("a", kind = FeedEntry.Kind.RECOMMENDATION)))
        assertEquals("added to want to read", runVerb("to-read"))
        assertEquals("started reading", runVerb("reading"))
    }

    @Test
    fun `day labels name the last two days and date the rest`() {
        val locale = Locale.UK

        assertEquals("Today", dayLabel(day, day, locale))
        assertEquals("Yesterday", dayLabel(day.minusDays(1), day, locale))
        assertEquals("Sat 31 Oct", dayLabel(day.minusDays(7), day, locale))
    }
}
