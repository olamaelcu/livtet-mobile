package net.olamaelcu.livtet.biblio

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

/** A read against the public catalog or a repository failed. */
class BiblioException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Read-only client for the data the platform's book, review, and profile permalinks show.
 *
 * Catalog records come from Bibliograph (`biblio`), which answers `com.atproto.repo.getRecord` with
 * a hydrated edition view, and user-owned records are read from the owner's PDS — the same
 * authoritative paths the platform uses. Nothing here writes, and nothing is cached beyond the PDS
 * lookup for a DID, because every screen is a one-shot render of a permalink.
 */
@Singleton
class BiblioClient
    @Inject
    constructor(
        @PublicBiblioHttp private val http: HttpClient,
    ) {
        companion object {
            const val BIBLIO_BASE = "https://biblio.livtet.olamaelcu.net"

            const val BSKY_BASE = "https://public.api.bsky.app"

            const val CONSTELLATION_BASE = "https://constellation.microcosm.blue"

            const val EDITION_COLLECTION = "community.lexicon.book.edition"

            /** Catalog edition search backing the add-book flow. */
            const val SEARCH_EDITIONS = "community.lexicon.book.searchEditions"

            /** Search page size; the lexicon caps `limit` at 100. */
            const val SEARCH_EDITIONS_LIMIT = 25

            /**
             * Reviews lexicon. `net.olamaelcu.livtet.bookReview` is the legacy name: the platform
             * migrates records from it, and the OAuth client metadata does not register it.
             */
            const val REVIEW_COLLECTION = "net.olamaelcu.livtet.biblio.bookReview"

            const val SHELVING_COLLECTION = "net.olamaelcu.livtet.biblio.bookShelving"

            const val SHELF_COLLECTION = "net.olamaelcu.livtet.biblio.shelf"

            /** Reviews shown on a book page, matching the platform's page size. */
            const val BOOK_REVIEW_LIMIT = 25

            /** Reviews shown on a profile, matching the platform's page size. */
            const val PROFILE_REVIEW_LIMIT = 20

            /** `listRecords` is capped at 100 by the PDS. */
            private const val LIST_PAGE_SIZE = 100

            /** Ten pages is 1000 shelved books — far more than any real reader. */
            private const val MAX_SHELVING_PAGES = 10

            const val TIMEOUT_MS = 15_000L
        }

        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        /** DID → PDS endpoint. Cheap to memoize and noticeably speeds up review lists. */
        private val pdsByDid = HashMap<String, String>()

        private val pdsMutex = Mutex()

        /** One catalog edition, as Bibliograph hydrates it. */
        suspend fun getEdition(
            did: String,
            rkey: String,
        ): BiblioBook =
            fetch<BiblioBookResponse>(
                BIBLIO_BASE,
                "/xrpc/com.atproto.repo.getRecord",
                listOf("repo" to did, "collection" to EDITION_COLLECTION, "rkey" to rkey),
            )
                .value

        /**
         * Catalog editions matching [query], newest first.
         *
         * Answers `community.lexicon.book.searchEditions` with raw edition records, so callers get the
         * title, `coverImageUrl`, and inline-named `contributors` the add-book flow renders. A blank
         * query is allowed and returns the most recent editions.
         */
        suspend fun searchEditions(
            query: String,
            limit: Int = SEARCH_EDITIONS_LIMIT,
            cursor: String? = null,
        ): BiblioEditionSearch =
            fetch<BiblioEditionSearch>(
                BIBLIO_BASE,
                "/xrpc/$SEARCH_EDITIONS",
                buildList {
                    add("q" to query)
                    add("limit" to limit.toString())
                    cursor?.takeIf { it.isNotBlank() }?.let { add("cursor" to it) }
                },
            )

        /** Cover URL Bibliograph derives from the edition's identifiers. */
        suspend fun getImageForBook(atUri: String): String? =
            runCatchingBiblio {
                fetch<BiblioImageResponse>(
                    BIBLIO_BASE,
                    "/xrpc/net.olamaelcu.livtet.biblio.getImageForBook",
                    listOf("uri" to atUri),
                )
                    .url
            }
                ?.takeIf { it.isNotBlank() }

        /**
         * Reviews for one edition.
         *
         * The AppView's `getReviewsByBook` is asked first; when it fails or has nothing, the
         * Constellation backlink index is used and each hit is hydrated from its author's PDS. The
         * fallback is load-bearing: the AppView currently returns an empty list for real books.
         */
        suspend fun getBookReviews(atUri: String): List<BiblioReview> {
            val viaAppView =
                runCatchingBiblio {
                    fetch<BiblioReviewList>(
                        BIBLIO_BASE,
                        "/xrpc/net.olamaelcu.livtet.biblio.getReviewsByBook",
                        listOf("book" to atUri, "limit" to BOOK_REVIEW_LIMIT.toString()),
                    )
                        .reviews
                }
                    .orEmpty()
            if (viaAppView.isNotEmpty()) return viaAppView
            return reviewsViaBacklinks(atUri)
        }

        /**
         * One review, read from its author's PDS. Returns null when the DID, its PDS, or the record
         * cannot be resolved — the screen renders "not found" rather than an error.
         */
        suspend fun getReview(
            did: String,
            rkey: String,
        ): BiblioReview? {
            val pds = resolvePds(did) ?: return null
            val response =
                runCatchingBiblio {
                    fetch<BiblioRecordResponse>(
                        pds,
                        "/xrpc/com.atproto.repo.getRecord",
                        listOf("repo" to did, "collection" to REVIEW_COLLECTION, "rkey" to rkey),
                    )
                } ?: return null
            if (response.value.text.isBlank()) return null
            return BiblioReview(
                uri = response.uri.ifEmpty { "at://$did/$REVIEW_COLLECTION/$rkey" },
                did = did,
                record = response.value,
            )
        }

        /** An actor's own reviews, newest first, read straight from their PDS. */
        suspend fun listActorReviews(
            did: String,
            limit: Int = PROFILE_REVIEW_LIMIT,
        ): List<BiblioReview> {
            val pds = resolvePds(did) ?: return emptyList()
            val listing =
                runCatchingBiblio {
                    fetch<BiblioListing>(
                        pds,
                        "/xrpc/com.atproto.repo.listRecords",
                        listOf(
                            "repo" to did,
                            "collection" to REVIEW_COLLECTION,
                            "limit" to limit.toString(),
                        ),
                    )
                } ?: return emptyList()
            return listing.records.mapNotNull { envelope ->
                val record = decodeReview(envelope.value) ?: return@mapNotNull null
                BiblioReview(uri = envelope.uri, did = did, record = record)
            }
        }

        /** Every `bookShelving` record the actor has, following cursors. */
        suspend fun listActorShelving(did: String): List<BiblioShelving> {
            val pds = resolvePds(did) ?: return emptyList()
            val out = ArrayList<BiblioShelving>()
            var cursor: String? = null
            var pages = 0
            while (pages < MAX_SHELVING_PAGES) {
                val params =
                    buildList {
                        add("repo" to did)
                        add("collection" to SHELVING_COLLECTION)
                        add("limit" to LIST_PAGE_SIZE.toString())
                        cursor?.let { add("cursor" to it) }
                    }
                val page =
                    runCatchingBiblio {
                        fetch<BiblioListing>(pds, "/xrpc/com.atproto.repo.listRecords", params)
                    } ?: break
                out.addAll(page.records.mapNotNull { toShelving(it) })
                pages += 1
                val next = page.cursor
                if (next.isNullOrEmpty() || page.records.isEmpty()) break
                cursor = next
            }
            return out
        }

        /** Shelf rkey → display name for an actor's shelves. */
        suspend fun listActorShelves(did: String): Map<String, String> {
            val pds = resolvePds(did) ?: return emptyMap()
            val listing =
                runCatchingBiblio {
                    fetch<BiblioListing>(
                        pds,
                        "/xrpc/com.atproto.repo.listRecords",
                        listOf(
                            "repo" to did,
                            "collection" to SHELF_COLLECTION,
                            "limit" to LIST_PAGE_SIZE.toString(),
                        ),
                    )
                } ?: return emptyMap()
            val out = LinkedHashMap<String, String>()
            for (envelope in listing.records) {
                val value = envelope.value ?: continue
                val name =
                    try {
                        json.decodeFromJsonElement(BiblioShelfValue.serializer(), value).name
                    } catch (_: Exception) {
                        null
                    }
                        ?.takeIf { it.isNotBlank() } ?: continue
                val rkey = parseAtUri(envelope.uri)?.rkey ?: continue
                out[rkey] = name
            }
            return out
        }

        /** Identity (handle, display name, avatar) for a DID or handle. */
        suspend fun getActor(actor: String): BiblioActor? =
            runCatchingBiblio {
                fetch<BiblioActor>(BSKY_BASE, "/xrpc/app.bsky.actor.getProfile", listOf("actor" to actor))
            }

        /**
         * The actor's PDS endpoint from their DID document.
         *
         * `did:plc` documents live on the PLC directory; `did:web` documents live on the DID's own
         * host, at `/.well-known/did.json` unless the DID carries a path.
         */
        suspend fun resolvePds(did: String): String? {
            pdsMutex
                .withLock { pdsByDid[did] }
                ?.let {
                    return it
                }
            val documentUrl = didDocumentUrl(did) ?: return null
            val document = runCatchingBiblio { fetchRaw(documentUrl) } ?: return null
            val service =
                runCatchingBiblio {
                    document["service"]
                        ?.jsonArray
                        ?.mapNotNull { it as? JsonObject }
                        ?.firstOrNull { entry ->
                            entry["type"]?.jsonPrimitive?.content == "AtprotoPersonalDataServer" &&
                                entry["id"]?.jsonPrimitive?.content?.endsWith("#atproto_pds") == true
                        }
                } ?: return null
            val endpoint =
                service?.get("serviceEndpoint")?.jsonPrimitive?.content?.trim() ?: return null
            if (endpoint.isEmpty()) return null
            val normalized =
                endpoint.trimEnd('/').let {
                    if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
                }
            pdsMutex.withLock { pdsByDid[did] = normalized }
            return normalized
        }

        /** Location of a DID's document, or null for methods this app cannot read. */
        internal fun didDocumentUrl(did: String): String? {
            if (did.startsWith("did:plc:")) return "https://plc.directory/$did"
            val webPrefix = "did:web:"
            if (!did.startsWith(webPrefix)) return null
            val decoded =
                try {
                    URLDecoder.decode(did.removePrefix(webPrefix), "UTF-8")
                } catch (_: Exception) {
                    return null
                }
            // did:web:<host>[:<port>][:<path>:<segments>] → https://<host>[:<port>]/[path/]did.json
            val parts = decoded.split(':')
            val hasPort = parts.getOrNull(1)?.all { it.isDigit() } == true
            val authority = if (hasPort) "${parts[0]}:${parts[1]}" else parts[0]
            if (authority.isEmpty()) return null
            val tail = parts.drop(if (hasPort) 2 else 1).filter { it.isNotEmpty() }
            return if (tail.isEmpty()) {
                "https://$authority/.well-known/did.json"
            } else {
                "https://$authority/${tail.joinToString("/")}/did.json"
            }
        }

        // ── Review discovery fallback ───────────────────────────────────────────

        /**
         * Reviews found through Constellation's backlink index, hydrated one by one from their author's
         * PDS. Reverse references to a book are written against either `book.uri` (strongRef) or `book`
         * (string); the second source is only asked when the first has nothing.
         */
        private suspend fun reviewsViaBacklinks(bookUri: String): List<BiblioReview> {
            val primary = backlinks(bookUri, "$REVIEW_COLLECTION:book.uri")
            val references =
                if (primary.isEmpty()) {
                    backlinks(bookUri, "$REVIEW_COLLECTION:book")
                } else {
                    primary
                }
            return references.mapNotNull { reference ->
                if (reference.collection != REVIEW_COLLECTION) return@mapNotNull null
                getReview(reference.did, reference.rkey)
            }
        }

        private suspend fun backlinks(
            bookUri: String,
            source: String,
        ): List<BiblioBacklink> =
            runCatchingBiblio {
                fetch<BiblioBacklinks>(
                    CONSTELLATION_BASE,
                    "/xrpc/blue.microcosm.links.getBacklinks",
                    listOf(
                        "subject" to bookUri,
                        "source" to source,
                        "limit" to BOOK_REVIEW_LIMIT.toString(),
                    ),
                )
                    .records
            }
                .orEmpty()

        // ── Decoding ────────────────────────────────────────────────────────────

        private fun decodeReview(element: JsonElement?): BiblioReviewRecord? {
            val value = element as? JsonObject ?: return null
            val record =
                try {
                    json.decodeFromJsonElement(BiblioReviewRecord.serializer(), value)
                } catch (_: Exception) {
                    null
                } ?: return null
            return record.takeIf { it.text.isNotBlank() }
        }

        private fun toShelving(envelope: BiblioRecordEnvelope): BiblioShelving? {
            val value = envelope.value ?: return null
            val shelving =
                try {
                    json.decodeFromJsonElement(BiblioShelvingValue.serializer(), value)
                } catch (_: Exception) {
                    null
                } ?: return null
            return BiblioShelving(uri = envelope.uri, value = shelving)
        }

        // ── Transport ───────────────────────────────────────────────────────────

        private suspend inline fun <reified T> fetch(
            base: String,
            path: String,
            params: List<Pair<String, String>>,
        ): T {
            val response =
                try {
                    withTimeout(TIMEOUT_MS) {
                        http.get(base + path) {
                            params.forEach { (name, value) -> parameter(name, value) }
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    throw BiblioException("timeout: $path", e)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw BiblioException("request failed: $path", e)
                }
            if (!response.status.isSuccess()) {
                throw BiblioException("$path → ${response.status.value} ${response.status.description}")
            }
            return try {
                response.body<T>()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw BiblioException("invalid body from $path", e)
            }
        }

        private suspend fun fetchRaw(url: String): JsonObject {
            val response =
                try {
                    withTimeout(TIMEOUT_MS) { http.get(url) }
                } catch (e: TimeoutCancellationException) {
                    throw BiblioException("timeout: $url", e)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw BiblioException("request failed: $url", e)
                }
            if (response.status == HttpStatusCode.NotFound) throw BiblioException("not found: $url")
            if (!response.status.isSuccess()) {
                throw BiblioException("$url → ${response.status.value}")
            }
            return response.body<JsonObject>()
        }

        /** Runs [block], turning [BiblioException] into a null for optional reads. */
        private suspend inline fun <T> runCatchingBiblio(block: () -> T): T? =
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("BiblioClient").d(e, "optional read failed")
                null
            }
    }
