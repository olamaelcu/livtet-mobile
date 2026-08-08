package net.olamaelcu.livtet

import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.olamaelcu.livtet.ffi.Book
import net.olamaelcu.livtet.ffi.BookSearchSortOrder
import net.olamaelcu.livtet.ffi.DashboardStats
import net.olamaelcu.livtet.ffi.EmptyMessage
import net.olamaelcu.livtet.ffi.Greeting
import net.olamaelcu.livtet.ffi.PluginHitMobile
import net.olamaelcu.livtet.ffi.RecentSearch
import net.olamaelcu.livtet.ffi.RecentlyReadBook
import net.olamaelcu.livtet.ffi.SeedResultMobile
import net.olamaelcu.livtet.ffi.SyncConfig
import net.olamaelcu.livtet.ffi.SyncState
import net.olamaelcu.livtet.ffi.seedDatabase as ffiSeedDatabase

object Bridge {
    @Volatile private var initialized = false

    private val initLock = Any()

    suspend fun init(databasePath: String) {
        if (initialized) return
        withContext(Dispatchers.IO) {
            synchronized(initLock) {
                if (initialized) return@withContext
                Log.i(TAG, "Bridge.init() ENTER (databasePath=$databasePath)")
                val dbFile = File(databasePath)
                val dbDir = dbFile.parentFile
                if (dbDir != null && !dbDir.exists()) {
                    Log.i(TAG, "Creating data directory: $dbDir")
                    dbDir.mkdirs()
                }
                net.olamaelcu.livtet.ffi.init(databasePath)
                initialized = true
                Log.i(TAG, "Bridge.init() COMPLETE")
            }
        }
    }

    fun isInitialized(): Boolean = initialized

    // ── Dashboard surfaces ────────────────────────────────────────
    //
    // The current FFI builds (`core/livtet-ffi`) don't expose
    // `getDashboardStats`, `getRecentlyReadBooks`, or
    // `getRecentSearches`. The Dashboard screen is the only
    // consumer; when those FFI calls land upstream we can wire
    // them back in here.

    suspend fun getGreeting(): Greeting =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.getGreeting() }

    suspend fun getEmptyStateQuotation(): EmptyMessage =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.getEmptyStateQuotation() }

    // ── Plugin search (for the Add Book wizard) ───────────────────

    suspend fun initPlugins() =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.initPlugins() }

    suspend fun lookupIdentifier(urn: String): PluginHitMobile? =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.lookupIdentifier(urn) }

    suspend fun searchProviders(query: String): List<PluginHitMobile> =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.searchProviders(query) }

    // ── Library (book list) ─────────────────────────────────────────

    suspend fun listBooks(
        limit: Int = 50,
        offset: Int = 0,
        order: BookSearchSortOrder = BookSearchSortOrder.DESCENDING,
    ): List<Book> =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.listBooks(
                limit = limit.toInt(),
                offset = offset.toInt(),
                order = order,
            )
        }

    // ── Sync (pair-with-desktop, sync-once, cancel) ────────────────

    suspend fun pairWithDesktop(config: SyncConfig) =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.pairWithDesktop(config) }

    suspend fun syncOnce(config: SyncConfig): SyncState =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.syncOnce(config) }

    suspend fun cancelSync(deviceId: String) =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.cancelSync(deviceId) }

    // ── Dashboard stub data ────────────────────────────────────────
    //
    // The full FFI surface for DashboardStats / RecentlyReadBook /
    // RecentSearch is not yet present in the core submodule. Until
    // those calls land, the Dashboard reads from these in-memory
    // stubs so the screen still renders something meaningful.
    // Replace each of these with the matching FFI wrapper once the
    // upstream FFI is restored.

    suspend fun getDashboardStats(): DashboardStats? = null

    suspend fun getRecentlyReadBooks(limit: Int): List<RecentlyReadBook> = emptyList()

    suspend fun getRecentSearches(limit: Int): List<RecentSearch> = emptyList()

    // ── Seed helper (used by smoke tests) ───────────────────────────

    suspend fun seedDatabase(works: Int): SeedResultMobile =
        withContext(Dispatchers.IO) { ffiSeedDatabase(works) }

    private const val TAG = "Bridge"
}
