package net.olamaelcu.livtet

import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * The catalog answers this phone has already read, on disk.
 *
 * Opening a post is the expensive read — one `getBooks` batch and one record per book — and the
 * same editions come back every time a reader reopens the same post, or the app. So an answer is
 * kept for a week, and "the catalog has never heard of this edition" is kept for a day: a miss is
 * an answer too, and retrying it on every open is what made a reopened post slow.
 *
 * The clock and the two store calls are injected, so expiry and round-trips are testable without a
 * device or a filesystem.
 */
class CatalogCache(
    private val read: () -> String?,
    private val write: (String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val hitTtlMillis: Long = HIT_TTL_MILLIS,
    private val missTtlMillis: Long = MISS_TTL_MILLIS,
    private val maxEntries: Int = MAX_ENTRIES,
) {
    private class Entry(val book: CatalogBook?, val fetchedAt: Long)

    private val entries = ConcurrentHashMap<String, Entry>()
    private var restored = false
    private var changed = false

    /** The book, when the cache holds a fresh copy of it. */
    fun book(uri: String): CatalogBook? = fresh(uri)?.book

    /**
     * Whether the cache can answer for [uri] without a request: either a fresh book or a fresh "the
     * catalog does not have it".
     */
    fun knows(uri: String): Boolean = fresh(uri) != null

    fun put(book: CatalogBook) {
        restore()
        entries[book.uri] = Entry(book, now())
        changed = true
    }

    /** Record that the catalog answered nothing for [uri], so it is not re-asked today. */
    fun putUnreadable(uri: String) {
        restore()
        entries[uri] = Entry(null, now())
        changed = true
    }

    /** Persist what changed, trimming the oldest entries first so the file stays bounded. */
    fun save() {
        if (!changed) return
        val snapshot =
            synchronized(this) {
                trim()
                encode()
            }
        write(snapshot)
        changed = false
    }

    private fun fresh(uri: String): Entry? {
        restore()
        val entry = entries[uri] ?: return null
        val ttl = if (entry.book == null) missTtlMillis else hitTtlMillis
        return entry.takeIf { now() - it.fetchedAt < ttl }
    }

    private fun restore() {
        if (restored) return
        synchronized(this) {
            if (restored) return
            runCatching { decode(read()) }.getOrNull()?.let { decoded -> entries.putAll(decoded) }
            restored = true
        }
    }

    /**
     * Drop the oldest entries once over [maxEntries].
     *
     * Eviction is by fetch time, not by read time: an entry that has not been refreshed is the one
     * nearest expiry anyway, and tracking reads would write the file far more often.
     */
    private fun trim() {
        if (entries.size <= maxEntries) return
        entries.entries
            .sortedBy { it.value.fetchedAt }
            .take(entries.size - maxEntries)
            .forEach { (uri, _) -> entries.remove(uri) }
    }

    private fun encode(): String {
        val encoded = JSONObject()
        entries.forEach { (uri, entry) ->
            val value = JSONObject()
            value.put("fetchedAt", entry.fetchedAt)
            entry.book?.let { value.put("book", encode(it)) }
            encoded.put(uri, value)
        }
        return JSONObject().put("entries", encoded).toString()
    }

    private fun decode(json: String?): Map<String, Entry> {
        if (json.isNullOrBlank()) return emptyMap()
        val entries = JSONObject(json).optJSONObject("entries") ?: return emptyMap()
        val decoded = HashMap<String, Entry>(entries.length())
        entries.keys().forEach { uri ->
            val entry = entries.optJSONObject(uri) ?: return@forEach
            decoded[uri] =
                Entry(
                    book = entry.optJSONObject("book")?.let { decodeBook(uri, it) },
                    fetchedAt = entry.optLong("fetchedAt"),
                )
        }
        return decoded
    }

    private fun encode(book: CatalogBook): JSONObject =
        JSONObject().apply {
            put("title", book.title)
            put("authors", JSONArray(book.authors))
            put("description", book.description)
            put("coverUrl", book.coverUrl)
            put("publishedDate", book.publishedDate)
            book.pages?.let { put("pages", it) }
        }

    private fun decodeBook(uri: String, json: JSONObject): CatalogBook {
        val authors = mutableListOf<String>()
        json.optJSONArray("authors")?.let { array ->
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotEmpty() }?.let { authors += it }
            }
        }
        return CatalogBook(
            uri = uri,
            title = json.optString("title").takeIf { it.isNotEmpty() },
            authors = authors,
            description = json.optString("description").takeIf { it.isNotEmpty() },
            coverUrl = json.optString("coverUrl").takeIf { it.isNotEmpty() },
            publishedDate = json.optString("publishedDate").takeIf { it.isNotEmpty() },
            pages = json.optInt("pages").takeIf { it > 0 },
        )
    }

    companion object {
        /** Catalog metadata changes when an edition is edited, which is rare; a week is safe. */
        const val HIT_TTL_MILLIS: Long = 7L * 24 * 60 * 60 * 1000

        /** A missed edition may be mirrored later, so a miss is believed for a day. */
        const val MISS_TTL_MILLIS: Long = 24L * 60 * 60 * 1000

        /** Roughly a year of feed reading; keeps the file in the low hundreds of kB. */
        const val MAX_ENTRIES: Int = 2_000
    }
}
