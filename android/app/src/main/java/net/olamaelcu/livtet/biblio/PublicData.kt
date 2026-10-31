package net.olamaelcu.livtet.biblio

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Everything a card needs about the edition a review points at. */
data class BookSummary(
    val uri: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
)

/** Reading-status counts for a profile header. */
data class ProfileStats(
    val total: Int = 0,
    val reading: Int = 0,
    val finished: Int = 0,
    val toRead: Int = 0,
    val abandoned: Int = 0,
)

/** One row of the Shelves tab. */
data class ShelfSummary(val rkey: String, val name: String, val bookCount: Int)

/** One book on the selected shelf. */
data class ShelfBookRow(
    val shelfRkey: String,
    val bookUri: String?,
    val title: String,
    val status: String?,
)

/** The author line for a book view: the contributor roles the catalog treats as authorship. */
fun BiblioBook.authorLine(): String? {
    val names =
        contributors
            .orEmpty()
            .filter { it.role != null && AUTHOR_LIKE_ROLES.contains(it.role) }
            .mapNotNull { it.person()?.name?.takeIf { name -> name.isNotBlank() } }
    return names.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

/** First ISBN in the edition's identifiers, stripped of its `isbn:` prefix. */
fun BiblioBook.isbn(): String? {
    val resources = setOf("isbn", "isbn10", "isbn13")
    return identifiers
        .orEmpty()
        .firstOrNull { resources.contains(it.resource) }
        ?.uri
        ?.removePrefix("isbn:")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

/** Reading-status counts derived from an actor's shelving records. */
fun shelvingStats(shelving: List<BiblioShelving>): ProfileStats {
    var reading = 0
    var finished = 0
    var toRead = 0
    var abandoned = 0
    for (entry in shelving) {
        when (entry.value.metadata?.status) {
            "reading" -> reading += 1
            "read" -> finished += 1
            "to-read" -> toRead += 1
            "dnf" -> abandoned += 1
        }
    }
    return ProfileStats(
        total = reading + finished + toRead + abandoned,
        reading = reading,
        finished = finished,
        toRead = toRead,
        abandoned = abandoned,
    )
}

/** Shelf rkey → count of shelved books, keyed off the shelving record's shelf reference. */
fun shelfCounts(shelving: List<BiblioShelving>): Map<String, Int> {
    val out = HashMap<String, Int>()
    for (entry in shelving) {
        val rkey = parseAtUri(entry.value.shelf)?.rkey ?: continue
        out[rkey] = (out[rkey] ?: 0) + 1
    }
    return out
}

/** Status of the shelving record for one reviewed book, for the review tabs' filter. */
fun statusByBookUri(shelving: List<BiblioShelving>): Map<String, String> {
    val out = HashMap<String, String>()
    for (entry in shelving) {
        val bookUri = entry.value.book.atUri ?: continue
        val status = entry.value.metadata?.status ?: continue
        out[bookUri] = status
    }
    return out
}

/**
 * Looks up an actor's identity for each DID, at most [concurrency] requests in flight. Reviews only
 * carry a DID, so a card cannot name its author without this.
 */
suspend fun BiblioClient.actorMap(
    dids: Collection<String>,
    concurrency: Int = 6,
): Map<String, BiblioActor> {
    val unique = dids.filter { it.isNotEmpty() }.distinct()
    if (unique.isEmpty()) return emptyMap()
    val gate = Semaphore(concurrency)
    return coroutineScope {
        unique
            .map { did -> async { did to (gate.withPermit { getActor(did) }) } }
            .awaitAll()
            .mapNotNull { (did, actor) -> actor?.let { did to it } }
            .toMap()
    }
}

/**
 * Title, author, and cover for each edition URI, at most [concurrency] requests in flight.
 * Unresolvable editions are dropped.
 */
suspend fun BiblioClient.bookSummaries(
    bookUris: Collection<String>,
    concurrency: Int = 6,
): Map<String, BookSummary> {
    val unique = bookUris.filter { it.isNotEmpty() }.distinct()
    if (unique.isEmpty()) return emptyMap()
    val gate = Semaphore(concurrency)
    return coroutineScope {
        unique
            .map { uri -> async { uri to gate.withPermit { summarise(uri) } } }
            .awaitAll()
            .mapNotNull { (uri, summary) -> summary?.let { uri to it } }
            .toMap()
    }
}

/** One edition as a [BookSummary], or null when the record cannot be read. */
suspend fun BiblioClient.summarise(bookUri: String): BookSummary? {
    val atUri = parseAtUri(bookUri) ?: return null
    val book =
        try {
            getEdition(atUri.did, atUri.rkey)
        } catch (_: BiblioException) {
            null
        }
    if (book == null) return null
    val cover =
        book.coverImageUrl?.takeIf { it.isNotBlank() }
            ?: runCatching { getImageForBook(bookUri) }.getOrNull()
    return BookSummary(
        uri = bookUri,
        title = book.title.ifBlank { atUri.rkey },
        author = book.authorLine(),
        coverUrl = cover,
    )
}

private val TIMESTAMP_PATTERNS =
    listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd",
    )

/**
 * Formats an atproto `datetime` for display. The value is always RFC 3339, but the writer decides
 * how much precision and whether an offset is present, so a few shapes are accepted. Returns null
 * when nothing parses, which the screens treat as "print nothing".
 */
fun formatTimestamp(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    for (pattern in TIMESTAMP_PATTERNS) {
        val parser =
            SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
        val parsed =
            try {
                parser.parse(value)
            } catch (_: ParseException) {
                null
            } ?: continue
        return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(parsed)
    }
    return null
}

/** `9.5/10`, `9/10`, or null when the review carries no rating. */
fun ratingLabel(rating: Double?): String? {
    if (rating == null) return null
    val text = if (rating % 1.0 == 0.0) rating.toInt().toString() else rating.toString()
    return "$text/10"
}
