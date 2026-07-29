package net.olamaelcu.livtet

import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.olamaelcu.livtet.ffi.AuthorInfo
import net.olamaelcu.livtet.ffi.Book
import net.olamaelcu.livtet.ffi.CrossWorkEditionDuplicateMobile
import net.olamaelcu.livtet.ffi.DashboardStats
import net.olamaelcu.livtet.ffi.DbId
import net.olamaelcu.livtet.ffi.DuplicateCandidateMobile
import net.olamaelcu.livtet.ffi.DuplicateMatchKindMobile
import net.olamaelcu.livtet.ffi.Edition
import net.olamaelcu.livtet.ffi.EditionDuplicateCandidateMobile
import net.olamaelcu.livtet.ffi.EditionMergeConflictResolutionMobile
import net.olamaelcu.livtet.ffi.FormatInfo
import net.olamaelcu.livtet.ffi.Greeting
import net.olamaelcu.livtet.ffi.LanguageInfo
import net.olamaelcu.livtet.ffi.MergeResultMobile
import net.olamaelcu.livtet.ffi.PluginHitMobile
import net.olamaelcu.livtet.ffi.PublisherInfo
import net.olamaelcu.livtet.ffi.RecentSearch
import net.olamaelcu.livtet.ffi.RecentlyReadBook
import net.olamaelcu.livtet.ffi.BookSearchSortOrder
import net.olamaelcu.livtet.ffi.WorkMergeConflictResolutionMobile
import net.olamaelcu.livtet.ffi.WorkStatusInfo
import net.olamaelcu.livtet.ffi.WorkSummary
import net.olamaelcu.livtet.ffi.ExistingWorkSummary
import net.olamaelcu.livtet.ffi.MobileException

object Bridge {
    @Volatile private var initialized = false

    private val initLock = Any()

    suspend fun init(databasePath: String) {
        if (initialized) return
        withContext(Dispatchers.IO) {
            synchronized(initLock) {
                if (initialized) return@withContext
                Log.i(TAG, "Bridge.init() ENTER (databasePath=$databasePath)")
                // TLS verification uses rustls's `WebPkiServerVerifier`
                // with Mozilla's bundled CA set, configured at the
                // reqwest client builder in `embedded_host.rs`. No
                // platform-verifier JNI init is needed — `reqwest` is
                // fed a prebuilt `rustls::ClientConfig` via
                // `tls_backend_preconfigured`.
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

    suspend fun getGreeting(): Greeting =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.getGreeting() }

    suspend fun getDashboardStats(): DashboardStats =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.getDashboardStats() }

    suspend fun getRecentlyReadBooks(limit: Int): List<RecentlyReadBook> =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.getRecentlyReadBooks(limit) }

    suspend fun getRecentSearches(limit: Int): List<RecentSearch> =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.getRecentSearches(limit) }

    suspend fun listBooks(limit: Int, offset: Int, order: BookSearchSortOrder): List<Book> =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.listBooks(limit, offset, order) }

    suspend fun getDistinctFormats(): List<FormatInfo> =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.getDistinctFormats() }

    suspend fun getDistinctLanguages(): List<LanguageInfo> =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.getDistinctLanguages() }

    suspend fun getDistinctWorkStatuses(): List<WorkStatusInfo> =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.getDistinctWorkStatuses() }

    // ── Book CRUD ─────────────────────────────────────────────────

    suspend fun createBook(title: String, description: String?): Book =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.createBook(title, description) }

    suspend fun createBookComplete(
        title: String,
        description: String?,
        editionTitle: String?,
        isbn: String?,
        publishedDate: String?,
        languageId: DbId?,
        authorNames: List<String>,
        publisher: String?,
    ): Book =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.createBookComplete(
                title,
                description,
                editionTitle,
                isbn,
                publishedDate,
                languageId,
                authorNames,
                publisher,
            )
        }

    suspend fun createEdition(
        workId: DbId,
        title: String?,
        isbn: String?,
        publishedDate: String?,
        languageId: DbId?,
    ): Edition =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.createEdition(workId, title, isbn, publishedDate, languageId)
        }

    suspend fun findWorksByTitlePrefix(prefix: String, limit: Int): List<WorkSummary> =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.findWorksByTitlePrefix(prefix, limit)
        }

    suspend fun getWorkAuthors(workId: DbId): List<AuthorInfo> =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.getWorkAuthors(workId) }

    // ── Authors ──────────────────────────────────────────────────

    suspend fun findOrCreateAuthor(name: String): AuthorInfo =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.findOrCreateAuthor(name) }

    suspend fun linkWorkAuthor(workId: DbId, authorId: DbId, role: String) =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.linkWorkAuthor(workId, authorId, role)
        }

    // ── Publishers ───────────────────────────────────────────────

    suspend fun findOrCreatePublisher(name: String): PublisherInfo =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.findOrCreatePublisher(name) }

    suspend fun linkWorkPublisher(workId: DbId, publisherId: DbId) =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.linkWorkPublisher(workId, publisherId)
        }

    // ── Identifiers ──────────────────────────────────────────────

    suspend fun upsertIdentifier(editionId: DbId, urn: String, kind: String) =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.upsertIdentifier(editionId, urn, kind)
        }

    // ── Duplicate detection & recovery ─────────────────────────

    suspend fun findWorkByIsbn(isbn: String): ExistingWorkSummary? =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.findWorkByIsbn(isbn) }

    suspend fun createEditionForWork(
        workId: DbId,
        editionTitle: String?,
        isbn: String,
        publishedDate: String?,
        languageId: DbId?,
    ): DbId =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.createEditionForWork(
                workId,
                isbn,
                editionTitle,
                publishedDate,
                languageId,
            )
        }

    suspend fun linkIsbnToExistingEdition(editionId: DbId, isbn: String) =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.linkIsbnToExistingEdition(editionId, isbn)
        }

    suspend fun mergeReplaceWork(
        workId: DbId,
        newTitle: String,
        newDescription: String?,
        newIsbn: String,
        newEditionTitle: String?,
        publishedDate: String?,
    ): Book =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.mergeReplaceWork(
                workId,
                newTitle,
                newDescription,
                newIsbn,
                newEditionTitle,
                publishedDate,
            )
        }

    suspend fun findDuplicateWorks(
        matchKinds: List<DuplicateMatchKindMobile>,
        minConfidence: Float,
    ): List<DuplicateCandidateMobile> =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.findDuplicateWorks(matchKinds, minConfidence)
        }

    suspend fun findDuplicateEditionsInWork(
        workId: DbId,
        matchKinds: List<DuplicateMatchKindMobile>,
        minConfidence: Float,
    ): List<EditionDuplicateCandidateMobile> =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.findDuplicateEditionsInWork(
                workId,
                matchKinds,
                minConfidence,
            )
        }

    suspend fun findCrossWorkEditionDuplicates(
        matchKinds: List<DuplicateMatchKindMobile>,
        minConfidence: Float,
    ): List<CrossWorkEditionDuplicateMobile> =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.findCrossWorkEditionDuplicates(matchKinds, minConfidence)
        }

    suspend fun mergeWorks(
        primaryWorkId: DbId,
        duplicateWorkId: DbId,
        conflictResolution: WorkMergeConflictResolutionMobile,
    ): MergeResultMobile =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.mergeWorks(
                primaryWorkId,
                duplicateWorkId,
                conflictResolution,
            )
        }

    suspend fun mergeEditions(
        primaryEditionId: DbId,
        duplicateEditionId: DbId,
        conflictResolution: EditionMergeConflictResolutionMobile,
    ): MergeResultMobile =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.mergeEditions(
                primaryEditionId,
                duplicateEditionId,
                conflictResolution,
            )
        }

    suspend fun moveEditionToWork(editionId: DbId, targetWorkId: DbId) {
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.moveEditionToWork(editionId, targetWorkId)
        }
    }

    // ── Plugin lookup ────────────────────────────────────────────

    suspend fun initPlugins() =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.initPlugins() }

    suspend fun lookupIdentifier(urn: String): PluginHitMobile? =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.lookupIdentifier(urn) }

    suspend fun searchProviders(query: String): List<PluginHitMobile> =
        withContext(Dispatchers.IO) { net.olamaelcu.livtet.ffi.searchProviders(query) }

    // ── Pairing / paired devices ─────────────────────────────────

    /// All paired devices with their type names resolved for display.
    /// The `PairedDeviceMobile` record carries the canonical name
    /// (e.g. "E-Reader") in `device_type` so the UI doesn't have to
    /// look up the device_types table.
    suspend fun getPairedDevices(): List<net.olamaelcu.livtet.ffi.PairedDeviceMobile> =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.getPairedDevices()
        }

    /// Insert a new paired_devices row. The `device_id` is generated
    /// server-side; pass `null` to let the FFI pick. The desktop's
    /// Tauri command takes the same arguments.
    suspend fun pairDevice(
        name: String,
        address: String,
        port: Int,
        deviceType: String,
    ): net.olamaelcu.livtet.ffi.PairedDeviceMobile =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.pairDevice(
                name = name,
                address = address,
                port = port.toUShort(),
                deviceType = deviceType,
            )
        }

    /// Remove a paired device by its id. No-op if the row is gone.
    suspend fun unpairDevice(deviceId: DbId) =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.unpairDevice(deviceId)
        }

    // ── Installed plugins ─────────────────────────────────────────

    suspend fun listInstalledPlugins(): List<net.olamaelcu.livtet.ffi.InstalledPluginMobile> =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.listInstalledPlugins()
        }

    suspend fun setPluginEnabled(pluginId: String, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.setPluginEnabled(pluginId, enabled)
        }

    // ── Plugin settings (per-key on a per-plugin basis) ────────

    suspend fun pluginSaveSetting(
        pluginId: String,
        key: String,
        value: String,
    ) = withContext(Dispatchers.IO) {
        net.olamaelcu.livtet.ffi.pluginSaveSetting(pluginId, key, value)
    }

    suspend fun pluginGetSetting(
        pluginId: String,
        key: String,
    ): String? = withContext(Dispatchers.IO) {
        net.olamaelcu.livtet.ffi.pluginGetSetting(pluginId, key)
    }

    // ── Network addresses (for manual IP pairing) ─────────────

    /// Non-loopback IPv4/IPv6 addresses of the device. Used by the
    /// Settings → Paired Devices screen to render the local
    /// interfaces a user can pair against. May be empty on CI or
    /// inside a sandbox.
    suspend fun getNetworkAddresses(): net.olamaelcu.livtet.ffi.NetworkAddressesMobile =
        withContext(Dispatchers.IO) {
            net.olamaelcu.livtet.ffi.getNetworkAddresses()
        }

    fun isInitialized(): Boolean = initialized

    private const val TAG = "Bridge"
}
