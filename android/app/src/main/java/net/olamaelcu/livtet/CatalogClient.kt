package net.olamaelcu.livtet

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/** One edition as Bibliograph knows it, which is more than the feed's record carries. */
data class CatalogBook(
    val uri: String,
    val title: String?,
    val authors: List<String>,
    val description: String?,
    val coverUrl: String?,
    val publishedDate: String?,
    val pages: Int?,
)

/**
 * Bibliograph's hydrated view of a catalog edition, for the books a feed post names.
 *
 * The feed carries a title and a cover and little else; the AppView answers anonymous reads from
 * the same host the platform's book pages use, so the phone asks it directly.
 *
 * Two calls, because each carries what the other drops: `getBooks` batches the views and their
 * contributor names, while the edition record itself carries the blurb and, for records the view
 * left bare, the contributor list. A book with neither a view nor a readable record stays absent
 * from the returned map — the AppView omits what it has never mirrored — so callers fall back to
 * what the post already knows.
 */
object CatalogClient {
    /** The AppView's public origin. */
    const val ORIGIN = "https://biblio.livtet.olamaelcu.net"

    private const val NSID = "net.olamaelcu.livtet.biblio"

    /** Most URIs the lexicon accepts in one call. */
    private const val BATCH = 100

    /** Roles the platform counts as authorship; anything else is not a name under the title. */
    private val AUTHOR_LIKE_ROLES: Set<String> =
        setOf("author", "intro_author", "foreword_author", "epilogue_author")

    /** Answers from earlier opens, on disk; null until [install] runs. */
    @Volatile private var cache: CatalogCache? = null

    /**
     * Point the client at its cache file. Called from the Application, once.
     *
     * The write goes through a temp file and a rename, so a process killed mid-save leaves the
     * previous cache intact rather than a truncated one.
     */
    fun install(context: Context) {
        if (cache != null) return
        val file = File(context.applicationContext.filesDir, "catalog-cache.json")
        cache =
            CatalogCache(
                read = { runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull() },
                write = { text ->
                    runCatching {
                        val temp = File(file.parentFile, "${file.name}.tmp")
                        temp.writeText(text)
                        if (!temp.renameTo(file)) {
                            file.writeText(text)
                            temp.delete()
                        }
                    }
                },
            )
    }

    /**
     * Hydrate [uris], answering from the cache first.
     *
     * [onUpdate] is called twice when something has to be fetched: once with whatever the cache
     * already knew, so a reopened post paints immediately, and once with the finished answers.
     * Books the catalog cannot answer for stay out of the map; the post's own fields fill in.
     */
    suspend fun books(
        uris: Collection<String>,
        onUpdate: (Map<String, CatalogBook>) -> Unit = {},
    ): Map<String, CatalogBook> =
        withContext(Dispatchers.IO) {
            val wanted = uris.filter { it.isNotBlank() }.distinct()
            val cache = cache
            val resolved = mutableMapOf<String, CatalogBook>()
            if (cache != null) {
                wanted.forEach { uri -> cache.book(uri)?.let { resolved[uri] = it } }
                onUpdate(resolved.toMap())
            }

            val stale = wanted.filter { cache == null || !cache.knows(it) }
            Timber.d(
                "catalog: %d of %d from cache, %d to fetch",
                wanted.size - stale.size,
                wanted.size,
                stale.size,
            )
            if (stale.isNotEmpty()) {
                coroutineScope {
                    stale.chunked(BATCH).forEach { batch ->
                        val views =
                            runCatching { views(batch) }
                                .getOrNull()
                                .orEmpty()
                                .associateBy { it.uri }
                        batch
                            .map { uri -> async { uri to book(uri, views[uri]) } }
                            .awaitAll()
                            .forEach { (uri, book) ->
                                if (book == null) {
                                    cache?.putUnreadable(uri)
                                } else {
                                    resolved[uri] = book
                                    cache?.put(book)
                                }
                            }
                    }
                }
                cache?.save()
            }
            onUpdate(resolved.toMap())
            resolved
        }

    /** The batched view, which is where the contributor names and the format come from. */
    private fun views(uris: List<String>): List<CatalogBook> {
        val query = uris.joinToString("&") { "uris=" + URLEncoder.encode(it, "UTF-8") }
        val json = get("$ORIGIN/xrpc/$NSID.getBooks?$query") ?: return emptyList()
        val books = json.optJSONArray("books") ?: return emptyList()
        return (0 until books.length()).mapNotNull { index ->
            val book = books.optJSONObject(index) ?: return@mapNotNull null
            val uri = book.optString("uri").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            CatalogBook(
                uri = uri,
                title = book.optString("title").takeIf { it.isNotEmpty() },
                authors = contributorNames(book.optJSONArray("contributors")),
                description = book.optString("description").takeIf { it.isNotEmpty() },
                coverUrl = book.optString("coverUrl").takeIf { it.isNotEmpty() },
                publishedDate = book.optString("publishDate").takeIf { it.isNotEmpty() },
                pages = book.optInt("pages").takeIf { it > 0 },
            )
        }
    }

    /**
     * One edition, the view's fields plus the record's.
     *
     * Null only when neither answered: a record that fails to read is not a book to lose, because
     * the view may still know its title and cover.
     */
    private fun book(uri: String, view: CatalogBook?): CatalogBook? {
        val record = editionRecord(uri)
        if (view == null && record == null) return null
        val authors =
            record
                ?.optJSONArray("contributors")
                ?.let { contributorNames(it) }
                ?.takeIf { it.isNotEmpty() } ?: view?.authors.orEmpty()
        return CatalogBook(
            uri = uri,
            title = record?.optString("title")?.takeIf { it.isNotEmpty() } ?: view?.title,
            authors = authors,
            description =
                record?.optString("description")?.takeIf { it.isNotEmpty() } ?: view?.description,
            coverUrl = record?.optString("coverUrl")?.takeIf { it.isNotEmpty() } ?: view?.coverUrl,
            publishedDate =
                record?.optString("publishDate")?.takeIf { it.isNotEmpty() } ?: view?.publishedDate,
            pages = record?.optInt("pages")?.takeIf { it > 0 } ?: view?.pages,
        )
    }

    /** The edition record itself; the AppView reads it across repos. */
    private fun editionRecord(uri: String): JSONObject? {
        val parts = atUriParts(uri) ?: return null
        val query =
            listOf("repo" to parts.did, "collection" to parts.collection, "rkey" to parts.rkey)
                .joinToString("&") { (key, value) -> "$key=" + URLEncoder.encode(value, "UTF-8") }
        return get("$ORIGIN/xrpc/com.atproto.repo.getRecord?$query")?.optJSONObject("value")
    }

    /**
     * Names from either shape a record uses: the view wraps each contributor with the role and
     * names it inline, while some records carry a reference to the contributor record instead.
     */
    private fun contributorNames(contributors: JSONArray): List<String> {
        val names = mutableListOf<String>()
        for (index in 0 until contributors.length()) {
            val entry = contributors.optJSONObject(index) ?: continue
            val role = entry.optString("role")
            if (role.isNotEmpty() && role !in AUTHOR_LIKE_ROLES) continue
            val inline =
                entry.optJSONObject("contributor")?.optString("name")?.takeIf { it.isNotEmpty() }
                    ?: entry.optString("name").takeIf { it.isNotEmpty() }
            val resolved =
                if (inline == null) {
                    entry.optString("ref").takeIf { it.isNotEmpty() }?.let(::contributorName)
                } else {
                    null
                }
            (inline ?: resolved)?.let { names += it }
        }
        if (names.isEmpty()) {
            for (index in 0 until contributors.length()) {
                contributors
                    .optJSONObject(index)
                    ?.optString("name")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { names += it }
            }
        }
        return names.distinct()
    }

    /** A contributor record's display name, read through the same host. */
    private fun contributorName(ref: String): String? {
        val parts = atUriParts(ref) ?: return null
        val query =
            listOf("repo" to parts.did, "collection" to parts.collection, "rkey" to parts.rkey)
                .joinToString("&") { (key, value) -> "$key=" + URLEncoder.encode(value, "UTF-8") }
        val value =
            get("$ORIGIN/xrpc/com.atproto.repo.getRecord?$query")?.optJSONObject("value")
                ?: return null
        return value.optString("name").takeIf { it.isNotEmpty() }
            ?: value.optString("displayName").takeIf { it.isNotEmpty() }
    }

    private class AtUri(val did: String, val collection: String, val rkey: String)

    private fun atUriParts(uri: String): AtUri? {
        if (!uri.startsWith("at://")) return null
        val (did, rest) =
            uri.substring(5).split("/", limit = 2).takeIf { it.size == 2 } ?: return null
        val (collection, rkey) = rest.split("/", limit = 2).takeIf { it.size == 2 } ?: return null
        if (did.isEmpty() || collection.isEmpty() || rkey.isEmpty()) return null
        return AtUri(did, collection, rkey)
    }

    private fun get(url: String): JSONObject? {
        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("accept", "application/json")
            }
        try {
            if (connection.responseCode !in 200..299) return null
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(text)
        } catch (error: Exception) {
            return null
        } finally {
            connection.disconnect()
        }
    }
}
