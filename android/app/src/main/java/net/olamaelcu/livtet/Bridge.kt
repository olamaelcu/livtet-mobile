package net.olamaelcu.livtet

import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.olamaelcu.livtet.ffi.EmptyMessage
import net.olamaelcu.livtet.ffi.Greeting
import net.olamaelcu.livtet.ffi.LivtetStore
import net.olamaelcu.livtet.ffi.SeedStats
import net.olamaelcu.livtet.ffi.WorkSummary
import net.olamaelcu.livtet.types.SortDirection
import net.olamaelcu.livtet.types.WorkSortBy

/** A book (work) row for the Library screen (`Bridge.listBooks`). */
data class Book(
    val id: String,
    val title: String,
    val description: String?,
)

enum class BookSearchSortOrder {
    ASCENDING,
    DESCENDING,
}

/** Outcome of a seed/reset call (`SeedStats` is the FFI shape). */
typealias SeedResultMobile = SeedStats

/** Dashboard stats placeholder — the FFI surface lands upstream later.
 * Mirrors the old FFI record's field names exactly.
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

    // ── Dashboard stub data ────────────────────────────────────────

    suspend fun getDashboardStats(): DashboardStats? = null

    suspend fun getRecentlyReadBooks(limit: Int): List<RecentlyReadBook> = emptyList()

    suspend fun getRecentSearches(limit: Int): List<RecentSearch> = emptyList()

    // ── Seed helper (used by smoke tests) ───────────────────────────

    suspend fun seedDatabase(works: Int): SeedResultMobile =
        withContext(Dispatchers.IO) { requireStore().seedSampleData(works.toUInt()) }

    suspend fun resetAndSeed(numWorks: Int): SeedResultMobile =
        withContext(Dispatchers.IO) { requireStore().resetAndSeed(numWorks.toUInt()) }

    private fun WorkSummary.toBook(): Book =
        Book(id = id.toString(), title = title, description = description)

    private const val TAG = "Bridge"
}
