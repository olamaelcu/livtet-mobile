package net.olamaelcu.livtet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cache is what a reopened post stands on, so its expiry and its bounds are behaviour worth
 * pinning: a week-old blurb is fine to keep, but a permanent "the catalog has never heard of this
 * book" would quietly hide editions the mirror added later.
 */
class CatalogCacheTest {
    private var stored: String? = null
    private var writes = 0
    private var clock = 1_000L

    private fun cache(maxEntries: Int = 100) =
        CatalogCache(
            read = { stored },
            write = {
                writes += 1
                stored = it
            },
            now = { clock },
            maxEntries = maxEntries,
        )

    private fun book(uri: String, title: String = uri) =
        CatalogBook(
            uri = uri,
            title = title,
            authors = listOf("An Author"),
            description = "A blurb.",
            coverUrl = null,
            publishedDate = null,
            pages = null,
        )

    @Test
    fun `a saved book comes back in a later process`() {
        val first = cache()
        first.put(book("at://a"))
        first.save()

        val reopened = cache()
        assertEquals("at://a", reopened.book("at://a")?.title)
        assertTrue(reopened.knows("at://a"))
    }

    @Test
    fun `a book expires after a week`() {
        val first = cache()
        first.put(book("at://a"))
        first.save()

        clock += CatalogCache.HIT_TTL_MILLIS + 1
        val reopened = cache()
        assertNull(reopened.book("at://a"))
        assertFalse(reopened.knows("at://a"))
    }

    @Test
    fun `a miss is believed for a day and then asked again`() {
        val first = cache()
        first.putUnreadable("at://ghost")
        first.save()

        val reopened = cache()
        assertTrue(reopened.knows("at://ghost"))
        assertNull(reopened.book("at://ghost"))

        clock += CatalogCache.MISS_TTL_MILLIS + 1
        assertFalse(reopened.knows("at://ghost"))
    }

    @Test
    fun `the oldest entries are dropped once the cache is full`() {
        val first = cache(maxEntries = 2)
        first.put(book("at://oldest"))
        clock += 10
        first.put(book("at://middle"))
        clock += 10
        first.put(book("at://newest"))
        first.save()

        val reopened = cache()
        assertNull(reopened.book("at://oldest"))
        assertTrue(reopened.knows("at://middle"))
        assertTrue(reopened.knows("at://newest"))
    }

    @Test
    fun `a corrupt file is ignored rather than fatal`() {
        stored = "{ this is not the cache"

        val cache = cache()
        assertNull(cache.book("at://a"))
        cache.put(book("at://a"))
        cache.save()
        assertEquals("at://a", cache().book("at://a")?.title)
    }

    @Test
    fun `an unchanged cache is not rewritten`() {
        val cache = cache()

        cache.save()
        assertEquals(0, writes)

        cache.put(book("at://a"))
        cache.save()
        assertEquals(1, writes)

        cache.save()
        assertEquals(1, writes)
    }
}
