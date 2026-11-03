package net.olamaelcu.livtet

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.olamaelcu.livtet.ffi.EmptyMessage
import net.olamaelcu.livtet.ffi.Greeting
import net.olamaelcu.livtet.ffi.LivtetStore
import net.olamaelcu.livtet.ffi.NewEdition
import net.olamaelcu.livtet.ffi.ReadingList
import net.olamaelcu.livtet.ffi.SeedStats
import net.olamaelcu.livtet.ffi.WorkSummary
import net.olamaelcu.livtet.types.SortDirection
import net.olamaelcu.livtet.types.PublishedDate
import net.olamaelcu.livtet.types.WorkSortBy
import net.olamaelcu.livtet.types.WorkStatus

/** A book (work) row for the Library screen (`Bridge.listBooks`). */
data class Book(
    val id: String,
    val title: String,
    val description: String?,
    /** Catalog cover URL, or a local cover file path; null when the edition has neither. */
    val coverUrl: String? = null,
    /** Reading status; null for a work that was never shelved. */
    val status: WorkStatus? = null,
    /** Edition a shelf entry files: the work's preferred edition, else its oldest. */
    val primaryEditionId: String? = null,
)

/** A contributor to attach while importing an edition. */
data class ContributorInput(val name: String, val role: String)

enum class BookSearchSortOrder {
    ASCENDING,
    DESCENDING,
}

/** Outcome of a seed/reset call (`SeedStats` is the FFI shape). */
typealias SeedResultMobile = SeedStats

/** Dashboard stats, projected from the FFI record. The FFI carries
 * `firstReadingAt` as an RFC 3339 string; the UI works in epoch millis,
 * so `Bridge.getDashboardStats` converts at the boundary.
 */
data class DashboardStats(
    val totalBooks: Long = 0,
    val booksInProgress: Long = 0,
    val finishedBooks: Long = 0,
    val totalReadingTimeSecs: Long = 0,
    val firstReadingAtMillis: Long? = null,
)

/** A recently-read book row for the dashboard. Placeholder data class. */
data class RecentlyReadBook(
    val workId: String,
    val editionId: String,
    val title: String,
    val authorName: String?,
    val progress: Double,
    val totalReadingTimeSecs: Long,
    val lastReadAt: String,
)

/** A search-history entry for the dashboard. Placeholder data class. */
data class RecentSearch(
    val query: String,
    val searchedAt: String,
)

/** A provider hit (from the Add Book wizard's online search). Placeholder. */
data class PluginHitMobile(
    val title: String,
    val authors: List<String>,
    val identifiers: List<String>,
    val publishedDate: String?,
    val publisher: String?,
    val source: String,
    val coverUrl: String?,
)

data class SyncConfig(val placeholder: String = "")

enum class SyncState {
    IDLE,
}

/**
 * Kotlin-facing library facade. Thin wrapper around the UniFFI
 * [`LivtetStore`]; owns initialization and presents the API surface the
 * screens consume.
 *
 * Plugin lookup and peer sync are intentionally stubbed: the upstream
 * plugin runtime and sync engine do not exist in core right now. The
 * call sites keep their exact API shape so only this object changes
 * when those subsystems return.
 */
object Bridge {
    @Volatile private var store: LivtetStore? = null
    private val initMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun init(databasePath: String) {
        if (store != null) return
        initMutex.withLock {
            if (store != null) return
            Log.i(TAG, "Bridge.init() ENTER (databasePath=$databasePath)")
            val dbFile = File(databasePath)
            val dbDir = dbFile.parentFile
            if (dbDir != null && !dbDir.exists()) {
                Log.i(TAG, "Creating data directory: $dbDir")
                dbDir.mkdirs()
            }
            val indexDir = File(dbDir, "search-index").absolutePath
            store = LivtetStore.open(databasePath, indexDir)
            Log.i(TAG, "Bridge.init() COMPLETE")
        }
    }

    fun isInitialized(): Boolean = store != null

    private fun requireStore(): LivtetStore =
        checkNotNull(store) { "Bridge.init() was not called before accessing the store" }

    // ── Dashboard surfaces ────────────────────────────────────────

    fun getGreeting(): Greeting = net.olamaelcu.livtet.ffi.getGreeting()

    fun getEmptyStateQuotation(): EmptyMessage =
        net.olamaelcu.livtet.ffi.getEmptyStateQuotation()

    // ── Plugin search (for the Add Book wizard) ───────────────────
    //
    // Plugins do not exist upstream yet. The wizard degrades to its
    // "no results / not available" state.

    suspend fun initPlugins() = Unit

    suspend fun lookupIdentifier(urn: String): PluginHitMobile? = null

    suspend fun searchProviders(query: String): List<PluginHitMobile> = emptyList()

    // ── Library (book list) ─────────────────────────────────────────

    suspend fun listBooks(
        limit: Int = 50,
        offset: Int = 0,
        order: BookSearchSortOrder = BookSearchSortOrder.DESCENDING,
        query: String? = null,
    ): List<Book> =
        withContext(Dispatchers.IO) {
            val sortDirection =
                when (order) {
                    BookSearchSortOrder.ASCENDING -> SortDirection.ASC
                    BookSearchSortOrder.DESCENDING -> SortDirection.DESC
                }
            requireStore()
                .listWorks(
                    limit = limit.toUInt(),
                    offset = offset.toUInt(),
                    sortBy = WorkSortBy.CREATED_AT,
                    sortDirection = sortDirection,
                    query = query?.takeIf { it.isNotBlank() },
                )
                .map { w -> w.toBook() }
        }

    // ── Sync (pair-with-desktop, sync-once, cancel) ────────────────
    //
    // The sync engine does not exist upstream yet.

    suspend fun pairWithDesktop(config: SyncConfig) {
        throw UnsupportedOperationException("Pairing is unavailable in this build")
    }

    suspend fun syncOnce(config: SyncConfig): SyncState = SyncState.IDLE

    suspend fun cancelSync(deviceId: String) = Unit

    // ── Dashboard ────────────────────────────────────────

    suspend fun getDashboardStats(): DashboardStats? =
        withContext(Dispatchers.IO) {
            val s = requireStore().getDashboardStats()
            DashboardStats(
                totalBooks = s.totalBooks,
                booksInProgress = s.booksInProgress,
                finishedBooks = s.finishedBooks,
                totalReadingTimeSecs = s.totalReadingTimeSecs,
                firstReadingAtMillis = s.firstReadingAt?.let(::parseRfc3339Millis),
            )
        }

    suspend fun getRecentlyReadBooks(limit: Int): List<RecentlyReadBook> =
        withContext(Dispatchers.IO) {
            requireStore().getRecentlyReadBooks(limit.toUInt()).map { r ->
                RecentlyReadBook(
                    workId = r.workId.toString(),
                    editionId = r.editionId.toString(),
                    title = r.title,
                    authorName = r.authorName,
                    progress = r.progress,
                    totalReadingTimeSecs = r.totalReadingTimeSecs,
                    lastReadAt = r.lastReadAt,
                )
            }
        }

    suspend fun getRecentSearches(limit: Int): List<RecentSearch> =
        withContext(Dispatchers.IO) {
            requireStore().getRecentSearches(limit.toUInt()).map { s ->
                RecentSearch(query = s.query, searchedAt = s.searchedAt)
            }
        }

    // ── Seed helper (used by smoke tests) ───────────────────────────

    suspend fun seedDatabase(works: Int): SeedResultMobile =
        withContext(Dispatchers.IO) { requireStore().seedSampleData(works.toUInt()) }

    suspend fun resetAndSeed(numWorks: Int): SeedResultMobile =
        withContext(Dispatchers.IO) { requireStore().resetAndSeed(numWorks.toUInt()) }

    // ── Covers (disk cache) ──────────────────────────────────────────

    /**
     * Where cached covers live. Inside the app's own cache directory, so the
     * system may evict it; the loader treats a miss as "download again".
     */
    private fun coverCacheRoot(): String =
        File(LivtetApp.getInstance().cacheDir, "livtet-covers").absolutePath

    /**
     * A local file for the edition's cover, downloading it once if needed.
     *
     * `livtet-covers` owns the on-disk layout, so the FFI answers every hit.
     * A miss returns null from the lookup and the image is fetched here and
     * handed back for storage; the next call is then served from disk.
     */
    suspend fun resolveCoverPath(editionId: String, remoteUrl: String?): String? =
        withContext(Dispatchers.IO) {
            val store = requireStore()
            val root = coverCacheRoot()

            store
                .coverCachePath(editionId, root)
                ?.takeIf { it.isNotBlank() }
                ?.let { return@withContext it }

            val url = remoteUrl?.takeIf { it.isNotBlank() } ?: return@withContext null
            val download = downloadImage(url) ?: return@withContext null
            runCatching { store.storeCover(editionId, root, download.ext, download.bytes) }
                .getOrNull()
        }

    /** Remove a work, its editions, and its shelf/status rows. */
    suspend fun deleteBook(workId: String): Boolean =
        withContext(Dispatchers.IO) { requireStore().deleteWork(workId) }

    /** Identifier URNs stored against an edition, used when backfilling a cover. */
    suspend fun listEditionIdentifiers(editionId: String): List<String> =
        withContext(Dispatchers.IO) { requireStore().listEditionIdentifiers(editionId) }

    /** Record a catalog cover URL for an edition that was imported without one. */
    suspend fun setEditionCoverUrl(editionId: String, coverUrl: String) =
        withContext(Dispatchers.IO) { requireStore().setEditionCoverUrl(editionId, coverUrl) }

    private class DownloadedImage(val bytes: ByteArray, val ext: String)

    /**
     * Fetch an image and guess its extension from the response, falling back
     * to the URL's suffix. Returns null on any failure: a missing cover is a
     * degraded row, not an error screen.
     */
    private fun downloadImage(url: String): DownloadedImage? =
        try {
            val connection =
                (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 20_000
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "image/*")
                }
            try {
                if (connection.responseCode !in 200..299) return null
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.isEmpty()) return null
                DownloadedImage(bytes, extensionFor(connection.contentType, url))
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }

    private fun extensionFor(contentType: String?, url: String): String =
        when (contentType?.substringBefore(';')?.trim()?.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/avif" -> "avif"
            "image/gif" -> "gif"
            else ->
                url.substringAfterLast('/').substringBefore('?').substringAfterLast('.', "jpg")
                    .lowercase()
                    .takeIf { it in setOf("jpg", "jpeg", "png", "webp", "avif", "gif") }
                    ?: "jpg"
        }

    // ── Shelves (named reading lists) ────────────────────────────────

    /** Named shelf. Membership is edition-scoped: `editionIds` in shelf order. */
    data class Shelf(
        val id: String,
        val name: String,
        val description: String?,
        val editionIds: List<String>,
    )

    private fun ReadingList.toShelf(): Shelf =
        Shelf(
            id = id.toString(),
            name = name,
            description = description,
            editionIds = editionIds.map { it.toString() },
        )

    suspend fun listShelves(): List<Shelf> =
        withContext(Dispatchers.IO) { requireStore().listReadingLists().map { it.toShelf() } }

    suspend fun createShelf(name: String): Shelf =
        withContext(Dispatchers.IO) {
            requireStore().createReadingList(name = name, description = null).toShelf()
        }

    suspend fun renameShelf(shelfId: String, name: String): Shelf =
        withContext(Dispatchers.IO) {
            requireStore().updateReadingList(shelfId, name, null).toShelf()
        }

    /** File a book on a shelf. No-op when it is already there. */
    suspend fun addToShelf(shelfId: String, editionId: String) =
        withContext(Dispatchers.IO) { requireStore().addEditionToList(shelfId, editionId) }

    /** Move a book to [position] (0-based) on its shelf; returns the new order. */
    suspend fun moveInShelf(shelfId: String, editionId: String, position: Int): List<String> =
        withContext(Dispatchers.IO) {
            requireStore()
                .moveEditionInList(shelfId, editionId, position.coerceAtLeast(0).toUInt())
                .map { it.toString() }
        }

    suspend fun removeFromShelf(shelfId: String, editionId: String): Boolean =
        withContext(Dispatchers.IO) {
            requireStore().removeEditionFromList(shelfId, editionId)
        }

    suspend fun deleteShelf(shelfId: String): Boolean =
        withContext(Dispatchers.IO) { requireStore().deleteReadingList(shelfId) }

    private fun WorkSummary.toBook(): Book =
        Book(
            id = id.toString(),
            title = title,
            description = description,
            coverUrl = coverUrl,
            status = status,
            primaryEditionId = primaryEditionId?.toString(),
        )

    // ── Import (catalog edition → library) ────────────────────────

    /**
     * Create a work with one edition and attach [identifiers] (URN
     * strings), returning the new edition id.
     *
     * Contributors are not persisted: the FFI has no author-creation
     * method, so a catalog contributor cannot become an `authors` row
     * yet. The work title is what the Library list renders.
     */
    suspend fun importEdition(
        title: String,
        description: String?,
        publishedYear: Int? = null,
        coverImageUrl: String? = null,
        contributors: List<ContributorInput> = emptyList(),
        identifiers: List<String> = emptyList(),
    ): String =
        withContext(Dispatchers.IO) {
            val store = requireStore()
            val work =
                store.createWork(
                    title = title,
                    sortTitle = null,
                    description = description,
                )
            val edition =
                store.createEdition(
                    NewEdition(
                        workId = work.id,
                        title = title,
                        description = description,
                        publishedDate = publishedYear?.let { PublishedDate.Year(it) },
                        coverImageUrl = coverImageUrl,
                        formatId = null,
                        languageId = null,
                        notes = null,
                    )
                )
            identifiers.distinct().forEach { urn ->
                store.addEditionIdentifier(editionId = edition.id, identifier = urn)
            }
            contributors
                .distinctBy { it.name.lowercase() to it.role.lowercase() }
                .forEach { contribution ->
                    store.addEditionContributor(
                        editionId = edition.id,
                        contributorId = contributorId(store, contribution.name),
                        role = contribution.role,
                    )
                }
            edition.id.toString()
        }

    /**
     * The contributor with exactly this name, or a new row.
     *
     * `authors` has no unique constraint, so importing the same person twice
     * would otherwise leave two rows that a rename could not keep in step.
     */
    private suspend fun contributorId(
        store: net.olamaelcu.livtet.ffi.LivtetStore,
        name: String,
    ): String {
        val existing =
            store.findContributors(name).firstOrNull { it.name.equals(name, ignoreCase = true) }
        return existing?.id ?: store.createContributor(name.trim()).id
    }

    /** True when any of [urns] already sits on an edition in the library. */
    suspend fun isInLibrary(urns: List<String>): Boolean =
        withContext(Dispatchers.IO) {
            val store = requireStore()
            urns.any { urn -> store.findEditionsByIdentifier(urn).isNotEmpty() }
        }

    private const val TAG = "Bridge"
}

/**
 * Parse the FFI's RFC 3339 (UTC) timestamps into epoch millis for the UI.
 * The Rust side renders sub-second precision variably, so it is truncated
 * before parsing; malformed input degrades to `null`.
 */
private fun parseRfc3339Millis(value: String): Long? =
    try {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        formatter.parse(value.substringBefore('.').removeSuffix("Z"))?.time
    } catch (_: java.text.ParseException) {
        null
    }
