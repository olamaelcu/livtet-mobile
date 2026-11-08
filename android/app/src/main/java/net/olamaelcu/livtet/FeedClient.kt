package net.olamaelcu.livtet

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * One entry in a reader's feed.
 *
 * The platform's feed is three record kinds behind one shape: a review, a shelving (a book moved to
 * a shelf) and a recommendation, which is a catalog edition rather than somebody's record and
 * therefore has no author. Keeping that distinction means the phone renders a line instead of
 * guessing at fields that are absent.
 */
data class FeedEntry(
    val uri: String?,
    val kind: Kind,
    val author: String?,
    val bookTitle: String?,
    val bookCoverUrl: String?,
    val bookUri: String?,
    val text: String?,
    val rating: Double?,
    val shelf: String?,
    val status: String?,
    val createdAt: String?,
) {
    enum class Kind {
        REVIEW,
        SHELVING,
        RECOMMENDATION,
        UNKNOWN,
    }

    /** One line for the home tile and the list. */
    val summary: String
        get() =
            when (kind) {
                Kind.REVIEW -> {
                    val who = author ?: "Someone"
                    val book = bookTitle?.let { " \u201c$it\u201d" } ?: ""
                    "$who reviewed$book"
                }
                Kind.SHELVING -> {
                    val who = author ?: "Someone"
                    val book = bookTitle?.let { " \u201c$it\u201d" } ?: ""
                    "$who shelved$book"
                }
                Kind.RECOMMENDATION -> bookTitle ?: "A book for you"
                Kind.UNKNOWN -> bookTitle ?: "Something new"
            }
}

/** A page of the feed, as the platform serves it. */
data class FeedPage(val entries: List<FeedEntry>, val cursor: String?)

/**
 * Reading the platform's feed as a signed-in reader.
 *
 * The route answers a cookie session to a browser and a bearer token to an app; this is the
 * application half. A refused token is not retried here — that is [PlatformAuth]'s job — but a 401
 * that survives it means the session is over, which is told apart from a transport failure so the
 * screen can say which.
 */
object FeedClient {
    /** Why a fetch produced nothing, so a screen can tell "sign in" from "offline". */
    class FeedError(val kind: Kind, override val message: String) : Exception(message) {
        enum class Kind {
            NOT_SIGNED_IN,
            SESSION_EXPIRED,
            TRANSPORT,
        }
    }

    /**
     * Fetch one page.
     *
     * [feed] names which generator: `following` is the reader's network, `public` the open one. A
     * signed-out reader can only read `public`, and the platform enforces that, so this never
     * pretends otherwise.
     */
    suspend fun page(
        context: Context,
        feed: String = "following",
        limit: Int = 20,
        cursor: String? = null,
    ): Result<FeedPage> =
        withContext(Dispatchers.IO) {
            val token = PlatformAuth.accessToken(context)
            if (token == null && feed != "public") {
                return@withContext Result.failure(
                    FeedError(FeedError.Kind.NOT_SIGNED_IN, NOT_SIGNED_IN)
                )
            }

            val url = buildString {
                append(PlatformAuth.PLATFORM_ORIGIN).append("/api/graph/feed")
                append("?feed=").append(java.net.URLEncoder.encode(feed, "UTF-8"))
                append("&limit=").append(limit)
                cursor
                    ?.takeIf { it.isNotBlank() }
                    ?.let { append("&cursor=").append(java.net.URLEncoder.encode(it, "UTF-8")) }
            }
            val connection =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    setRequestProperty("accept", "application/json")
                    token?.let { setRequestProperty("authorization", "Bearer $it") }
                }
            try {
                val status = connection.responseCode
                if (status == 401) {
                    return@withContext Result.failure(
                        FeedError(FeedError.Kind.SESSION_EXPIRED, SESSION_EXPIRED)
                    )
                }
                val text =
                    (if (status in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                if (status !in 200..299) {
                    return@withContext Result.failure(
                        FeedError(FeedError.Kind.TRANSPORT, "the feed answered $status")
                    )
                }
                Result.success(parse(JSONObject(text)))
            } catch (error: FeedError) {
                Result.failure(error)
            } catch (error: kotlinx.coroutines.CancellationException) {
                // The screen went away; that is not a feed failure.
                throw error
            } catch (error: Exception) {
                Result.failure(
                    FeedError(FeedError.Kind.TRANSPORT, error.message ?: error.toString())
                )
            } finally {
                connection.disconnect()
            }
        }

    const val NOT_SIGNED_IN = "Sign in to read your feed."
    const val SESSION_EXPIRED = "Your sign-in has expired. Sign in again."

    /** Read the JSON the route serves into entries, ignoring what cannot be read. */
    internal fun parse(json: JSONObject): FeedPage {
        val items = json.optJSONArray("items") ?: JSONArray()
        val entries =
            (0 until items.length()).mapNotNull { index ->
                items.optJSONObject(index)?.let(::entry)
            }
        return FeedPage(
            entries = entries,
            cursor = json.optString("cursor").takeIf { it.isNotEmpty() },
        )
    }

    private fun entry(json: JSONObject): FeedEntry {
        val kind =
            when (json.optString("kind")) {
                "review" -> FeedEntry.Kind.REVIEW
                "shelving" -> FeedEntry.Kind.SHELVING
                "recommendation" -> FeedEntry.Kind.RECOMMENDATION
                else -> FeedEntry.Kind.UNKNOWN
            }
        return FeedEntry(
            uri = json.optString("uri").takeIf { it.isNotEmpty() },
            kind = kind,
            author = json.optJSONObject("author")?.optString("handle")?.takeIf { it.isNotEmpty() },
            bookTitle = json.optString("bookTitle").takeIf { it.isNotEmpty() },
            bookCoverUrl = json.optString("bookCoverUrl").takeIf { it.isNotEmpty() },
            bookUri = json.optString("bookUri").takeIf { it.isNotEmpty() },
            text = json.optString("text").takeIf { it.isNotEmpty() },
            rating =
                if (json.has("rating")) json.optDouble("rating").takeIf { !it.isNaN() } else null,
            shelf = json.optString("shelf").takeIf { it.isNotEmpty() },
            status = json.optString("status").takeIf { it.isNotEmpty() },
            createdAt = json.optString("createdAt").takeIf { it.isNotEmpty() },
        )
    }
}
