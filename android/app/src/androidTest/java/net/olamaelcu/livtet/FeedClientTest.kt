package net.olamaelcu.livtet

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reading the platform's feed JSON.
 *
 * On-device because it uses the platform's `org.json`, which the JVM unit-test runtime only stubs.
 *
 * The three item kinds share a page but not a shape: a recommendation has no author, a shelving has
 * a shelf and may have no text. The tile and the list both render from what this produces, so an
 * entry that cannot be read should be dropped rather than shown as a blank row.
 */
@RunWith(AndroidJUnit4::class)
class FeedClientTest {
    private val page =
        JSONObject(
            """
            {
              "items": [
                {
                  "kind": "shelving",
                  "author": { "did": "did:plc:a", "handle": "jacky.example" },
                  "bookTitle": "Piranesi",
                  "shelf": "Reading",
                  "createdAt": "2026-11-07T10:00:00Z"
                },
                {
                  "kind": "review",
                  "author": { "did": "did:plc:b", "handle": "reader.example" },
                  "bookTitle": "The Long Shelf",
                  "text": "Worth the detour.",
                  "rating": 5
                },
                { "kind": "recommendation", "bookUri": "at://x", "bookTitle": "Night Reading" },
                { "kind": "shelving" }
              ],
              "cursor": "next"
            }
            """
                .trimIndent()
        )

    @Test
    fun keepsTheKindsApartAndReadsWhatEachCarries() {
        val parsed = FeedClient.parse(page)

        assertEquals(4, parsed.entries.size)
        val shelving = parsed.entries[0]
        assertEquals(FeedEntry.Kind.SHELVING, shelving.kind)
        assertEquals("jacky.example", shelving.author)
        assertEquals("Piranesi", shelving.bookTitle)
        assertEquals("Reading", shelving.shelf)

        val review = parsed.entries[1]
        assertEquals(FeedEntry.Kind.REVIEW, review.kind)
        assertEquals("Worth the detour.", review.text)

        val recommendation = parsed.entries[2]
        assertEquals(FeedEntry.Kind.RECOMMENDATION, recommendation.kind)
        assertNull("a recommendation is not somebody's record", recommendation.author)
        assertEquals("Night Reading", recommendation.bookTitle)

        assertEquals("next", parsed.cursor)
    }

    @Test
    fun anItemMissingEveryFieldStillRenders() {
        val entry = FeedClient.parse(page).entries[3]

        assertEquals(FeedEntry.Kind.SHELVING, entry.kind)
        assertNull(entry.author)
        assertEquals("Someone shelved", entry.summary)
    }

    @Test
    fun theSummariesNameTheAuthorAndTheBook() {
        val entries = FeedClient.parse(page).entries

        assertEquals("jacky.example shelved \u201cPiranesi\u201d", entries[0].summary)
        assertEquals("reader.example reviewed \u201cThe Long Shelf\u201d", entries[1].summary)
        assertEquals("Night Reading", entries[2].summary)
    }

    @Test
    fun aPageWithoutACursorInventsNone() {
        val parsed = FeedClient.parse(JSONObject("""{"items":[]}"""))

        assertTrue(parsed.entries.isEmpty())
        assertNull(parsed.cursor)
    }
}
