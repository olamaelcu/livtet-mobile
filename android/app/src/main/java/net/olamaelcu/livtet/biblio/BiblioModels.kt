package net.olamaelcu.livtet.biblio

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Wire models for the public catalog and PDS reads behind the book, review, and profile screens.
 *
 * Every field is nullable or defaulted: the same shapes are served by Bibliograph's hydrated
 * AppView views and by raw repository records, and a record written by an older client is missing
 * whatever the lexicon added since.
 */

/** `com.atproto.repo.getRecord` envelope for an edition. */
@Serializable
data class BiblioBookResponse(
    val uri: String = "",
    val cid: String? = null,
    val value: BiblioBook = BiblioBook(),
)

/** A catalog edition, as the AppView hydrates it. */
@Serializable
data class BiblioBook(
    val uri: String = "",
    val title: String = "",
    val description: String? = null,
    val coverImageUrl: String? = null,
    val publishedYear: Int? = null,
    val publishDate: String? = null,
    val language: String? = null,
    val pages: Int? = null,
    val format: BiblioFormat? = null,
    val genres: List<BiblioGenre>? = null,
    val contributors: List<BiblioContributor>? = null,
    val identifiers: List<BiblioIdentifier>? = null,
)

@Serializable
data class BiblioContributor(
    val bookUri: String? = null,
    val contributor: BiblioContributorRef? = null,
    val role: String? = null,
    /** Raw-record spelling of a contribution; the AppView uses `contributor`. */
    val subject: BiblioContributorRef? = null,
    /**
     * Name written inline on the contribution. `community.lexicon.book.searchEditions` returns
     * `{ subject, role, name }`, whereas `getRecord` hydration puts the name on the ref itself.
     */
    val name: String? = null,
)

@Serializable
data class BiblioContributorRef(
    val uri: String? = null,
    val cid: String? = null,
    val name: String? = null,
)

@Serializable
data class BiblioGenre(
    val uri: String? = null,
    val name: String = "",
    val emoji: String? = null,
    val description: String? = null,
)

@Serializable
data class BiblioFormat(
    val uri: String? = null,
    val description: String? = null,
    val emoji: String? = null,
    val unit: String? = null,
)

@Serializable data class BiblioIdentifier(val resource: String = "", val uri: String = "")

/** One review as either the AppView list or [getReview] returns it. */
@Serializable
data class BiblioReview(
    val uri: String = "",
    val did: String = "",
    val record: BiblioReviewRecord = BiblioReviewRecord(),
)

@Serializable
data class BiblioReviewRecord(
    val text: String = "",
    /**
     * Bibliograph writes a bare at-uri string; livtet-service writes a strongRef. Decoded as JSON
     * so both shapes survive.
     */
    val book: JsonElement? = null,
    val rating: Double? = null,
    val createdAt: String? = null,
)

/** `com.atproto.repo.getRecord` envelope for a review. */
@Serializable
data class BiblioRecordResponse(
    val uri: String = "",
    val cid: String? = null,
    val value: BiblioReviewRecord = BiblioReviewRecord(),
)

/** `com.atproto.repo.listRecords` envelope. */
@Serializable
data class BiblioListing(
    val records: List<BiblioRecordEnvelope> = emptyList(),
    val cursor: String? = null,
)

@Serializable data class BiblioRecordEnvelope(val uri: String = "", val value: JsonElement? = null)

/** `net.olamaelcu.livtet.biblio.bookShelving` record. */
@Serializable
data class BiblioShelving(
    val uri: String = "",
    val value: BiblioShelvingValue = BiblioShelvingValue(),
)

@Serializable
data class BiblioShelvingValue(
    val book: BiblioShelvingBook = BiblioShelvingBook(),
    val shelf: String = "",
    val metadata: BiblioShelvingMetadata? = null,
)

@Serializable
data class BiblioShelvingBook(
    /** Strong-ref spelling, written by livtet-service. */
    val uri: String? = null,
    val cid: String? = null,
    /** Inline spelling, written by reader apps: the edition URI lives here. */
    val ref: String? = null,
    /** Only present on the inline spelling. */
    val title: String? = null,
) {
    /** The catalog edition URI, whichever spelling the record used. */
    val atUri: String?
        get() = uri ?: ref
}

@Serializable
data class BiblioShelvingMetadata(
    /** `reading`, `read`, `to-read`, or `dnf`. */
    val status: String? = null,
    val pages: Int? = null,
)

/** `net.olamaelcu.livtet.biblio.shelf` record. */
@Serializable
data class BiblioShelf(val uri: String = "", val value: BiblioShelfValue = BiblioShelfValue())

@Serializable data class BiblioShelfValue(val name: String = "")

/** `app.bsky.actor.getProfile` view. */
@Serializable
data class BiblioActor(
    val did: String = "",
    val handle: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val avatar: String? = null,
)

/** `blue.microcosm.links.getBacklinks` response. */
@Serializable
data class BiblioBacklinks(val records: List<BiblioBacklink>? = null, val cursor: String? = null)

@Serializable
data class BiblioBacklink(val did: String = "", val collection: String = "", val rkey: String = "")

/** `net.olamaelcu.livtet.biblio.getImageForBook` response. */
@Serializable data class BiblioImageResponse(val url: String? = null)

/** `community.lexicon.book.searchEditions` response: raw edition records, newest first. */
@Serializable
data class BiblioEditionSearch(
    val items: List<BiblioBook> = emptyList(),
    val cursor: String? = null,
)

/** `{biblio}.getReviewsByBook` response. */
@Serializable
data class BiblioReviewList(
    val reviews: List<BiblioReview> = emptyList(),
    val cursor: String? = null,
)

/** The three parts of an `at://` URI this app needs. */
data class AtUri(val did: String, val collection: String, val rkey: String) {
    override fun toString(): String = "at://$did/$collection/$rkey"
}

/** Splits `at://<did>/<collection>/<rkey>`; null for anything else. */
fun parseAtUri(value: String?): AtUri? {
    val raw = value?.trim().orEmpty()
    if (!raw.startsWith("at://")) return null
    val parts = raw.removePrefix("at://").split('/')
    if (parts.size != 3) return null
    val (did, collection, rkey) = parts
    if (did.isEmpty() || collection.isEmpty() || rkey.isEmpty()) return null
    return AtUri(did, collection, rkey)
}

/** The edition URI a review points at, accepting both the string and the strongRef spelling. */
fun BiblioReviewRecord.bookUri(): String? =
    when (val element = book) {
        is JsonPrimitive -> element.stringOrNull()
        is JsonObject -> (element["uri"] as? JsonPrimitive)?.stringOrNull()
        else -> null
    }

private fun JsonPrimitive.stringOrNull(): String? =
    if (isString) content.takeIf { it.isNotEmpty() } else null

/** Role names the book screen presents as the author line. */
val AUTHOR_LIKE_ROLES: Set<String> =
    setOf("author", "intro_author", "foreword_author", "epilogue_author")

/** The contributor a book view hangs a name on, whichever spelling it used. */
fun BiblioContributor.person(): BiblioContributorRef? = contributor ?: subject
