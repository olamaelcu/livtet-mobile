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
import net.olamaelcu.livtet.ffi.ReadingProgress
import net.olamaelcu.livtet.ffi.SeedStats
import net.olamaelcu.livtet.ffi.SyncOffer
import net.olamaelcu.livtet.ffi.SyncOfferSummary
import net.olamaelcu.livtet.ffi.SyncPairLink
import net.olamaelcu.livtet.ffi.SyncPairedDevice
import net.olamaelcu.livtet.ffi.SyncPairingResult
import net.olamaelcu.livtet.ffi.SyncPendingPairing
import net.olamaelcu.livtet.ffi.SyncPullReport
import net.olamaelcu.livtet.ffi.SyncReceivedBook
import net.olamaelcu.livtet.ffi.SyncSentBook
import net.olamaelcu.livtet.ffi.TransferProgressListener
import net.olamaelcu.livtet.ffi.WorkSummary
import net.olamaelcu.livtet.types.ProgressUnit
import net.olamaelcu.livtet.types.PublishedDate
import net.olamaelcu.livtet.types.SortDirection
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

/** One edition, as the book-detail sheet renders it. */
data class EditionDetailView(
    val title: String?,
    val authors: List<String>,
    val description: String?,
    val format: String?,
    val languageCode: String?,
    val publishedDate: String?,
    val publishers: List<String>,
    val identifiers: List<String>,
    val filePath: String?,
    val fileSizeBytes: Long?,
)

/** A contributor to attach while importing an edition. */
data class ContributorInput(val name: String, val role: String)

enum class BookSearchSortOrder {
    ASCENDING,
    DESCENDING,
}

/** Outcome of a seed/reset call (`SeedStats` is the FFI shape). */
typealias SeedResultMobile = SeedStats

/**
 * Dashboard stats, projected from the FFI record. The FFI carries `firstReadingAt` as an RFC 3339
 * string; the UI works in epoch millis, so `Bridge.getDashboardStats` converts at the boundary.
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
data class RecentSearch(val query: String, val searchedAt: String)

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

/**
 * Kotlin-facing library facade. Thin wrapper around the UniFFI [`LivtetStore`]; owns initialization
 * and presents the API surface the screens consume.
 *
 * Plugin lookup is still stubbed: the upstream plugin runtime does not exist in core yet.
 */
object Bridge {
    @Volatile private var store: LivtetStore? = null
    private val initMutex = kotlinx.coroutines.sync.Mutex()
    /** The database path handed to [init], which peer sync reopens per call. */
    @Volatile private var databaseFile: String? = null

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
            databaseFile = databasePath
            Log.i(TAG, "Bridge.init() COMPLETE")
        }
    }

    fun isInitialized(): Boolean = store != null

    private fun requireStore(): LivtetStore =
        checkNotNull(store) { "Bridge.init() was not called before accessing the store" }

    // ── Dashboard surfaces ────────────────────────────────────────

    fun getGreeting(): Greeting = net.olamaelcu.livtet.ffi.getGreeting()

    fun getEmptyStateQuotation(): EmptyMessage = net.olamaelcu.livtet.ffi.getEmptyStateQuotation()

    // ── Plugin search (for the Add Book wizard) ───────────────────
    //
    // Plugins do not exist upstream yet. The wizard degrades to its
    // "no results / not available" state.

    suspend fun initPlugins() = Unit

    suspend fun lookupIdentifier(urn: String): PluginHitMobile? = null

    suspend fun searchProviders(query: String): List<PluginHitMobile> = emptyList()

    // ── Library (book list) ─────────────────────────────────────────

    /** The orders a library screen can offer. */
    enum class SortBy(val workSort: WorkSortBy) {
        /** Newest acquisition first, which is what "recently added" means here. */
        RECENT(WorkSortBy.CREATED_AT),
        TITLE(WorkSortBy.TITLE),
    }

    suspend fun listBooks(
        limit: Int = 50,
        offset: Int = 0,
        order: BookSearchSortOrder = BookSearchSortOrder.DESCENDING,
        query: String? = null,
        sortBy: SortBy = SortBy.RECENT,
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
                    sortBy = sortBy.workSort,
                    sortDirection = sortDirection,
                    query = query?.takeIf { it.isNotBlank() },
                )
                .map { w -> w.toBook() }
        }

    // ── Sync (pair-with-desktop, sync-once, cancel) ────────────────
    //
    // Peer sync is the FFI's own client and server: the phone pairs *outward*
    // by claiming a peer and *inward* by approving a claim, and it serves the
    // books it holds to whoever it approved. Every call below reopens the
    // library by path, so nothing here outlives [init].

    /** This device's iroh secret key. Its node id derives from it, so it must persist. */
    private fun secretKeyPath(): String =
        File(LivtetApp.getInstance().filesDir, "sync/node.key").absolutePath

    private fun requireDatabaseFile(): String =
        checkNotNull(databaseFile) { "Bridge.init() was not called before peer sync" }

    /**
     * This device's stable id, created on first use.
     *
     * A peer records this id when the pairing is approved, so it has to survive reinstall-free
     * restarts: it lives beside the sync key rather than in memory. The store accepts either of its
     * textual forms, so the 32 hex digits of a UUID serve without pulling in an id crate.
     */
    private fun deviceIdForPeer(): String {
        val file = File(LivtetApp.getInstance().filesDir, "sync/device.id")
        file.parentFile?.mkdirs()
        val existing = runCatching { file.readText().trim() }.getOrNull()
        if (!existing.isNullOrEmpty()) return existing
        val generated = java.util.UUID.randomUUID().toString().replace("-", "")
        file.writeText(generated)
        return generated
    }

    /** This device's stable id, shared by pairing and by the sync screen. */
    fun deviceId(): String = deviceIdForPeer()

    /**
     * Announce this device on `_livtet-sync._tcp` and start answering peers.
     *
     * Returns the node id peers dial. Idempotent: a second call returns the id already advertised
     * rather than binding another endpoint.
     */
    suspend fun advertiseForSync(deviceId: String, name: String): String =
        net.olamaelcu.livtet.ffi.advertiseSyncDevice(
            secretKeyPath = secretKeyPath(),
            dbPath = requireDatabaseFile(),
            deviceId = deviceId,
            name = name,
            deviceType = "android",
        )

    /** Stop advertising; a no-op when this device is not. */
    fun stopAdvertising() = net.olamaelcu.livtet.ffi.stopAdvertisingSyncDevice(secretKeyPath())

    /** The pairing link a QR code renders, for the peer to scan. */
    suspend fun pairingLink(name: String?): String =
        net.olamaelcu.livtet.ffi.pairLink(advertiseForSync(deviceIdForPeer(), name ?: ""), name)

    /** Read a scanned pairing link. Throws [LivtetException.InvalidInput] when it is not one. */
    fun parsePairingLink(link: String): SyncPairLink = net.olamaelcu.livtet.ffi.parsePairLink(link)

    /** Claim the peer at `peerNodeId`; [waitSeconds] 0 returns while it is still pending. */
    suspend fun pairWithPeer(
        peerNodeId: String,
        name: String,
        waitSeconds: UInt = 0u,
    ): SyncPairingResult =
        net.olamaelcu.livtet.ffi.pairSyncDevice(
            secretKeyPath = secretKeyPath(),
            dbPath = requireDatabaseFile(),
            peerNodeId = peerNodeId,
            deviceId = deviceIdForPeer(),
            name = name,
            deviceType = "android",
            waitSecs = waitSeconds,
        )

    /** Keep waiting on a claim [pairWithPeer] left pending. */
    suspend fun pairingStatus(
        peerNodeId: String,
        claim: String,
        name: String,
        waitSeconds: UInt = 30u,
    ): SyncPairingResult =
        net.olamaelcu.livtet.ffi.syncPairingStatus(
            secretKeyPath = secretKeyPath(),
            dbPath = requireDatabaseFile(),
            peerNodeId = peerNodeId,
            claim = claim,
            waitSecs = waitSeconds,
            name = name,
            deviceType = "android",
        )

    /**
     * The token a peer issued this device, read from its `paired_devices` row.
     *
     * `null` means no pairing completed with it — or completed without the peer identifying itself
     * — and a pull then needs the token handed in explicitly.
     */
    suspend fun peerToken(peerNodeId: String): String? =
        net.olamaelcu.livtet.ffi.syncPeerToken(requireDatabaseFile(), peerNodeId)

    /** Peers waiting on this device to approve them. */
    suspend fun pendingPairings(): List<SyncPendingPairing> =
        net.olamaelcu.livtet.ffi.syncPendingPairings(secretKeyPath())

    /**
     * Answer a claim. Approving returns the token the peer must present, which is worth recording
     * alongside the device; rejecting returns `null`.
     */
    suspend fun decidePairing(nodeId: String, accept: Boolean): String? =
        net.olamaelcu.livtet.ffi.syncDecidePairing(secretKeyPath(), nodeId, accept)

    /** The devices this one is paired with, in either direction. */
    suspend fun pairedDevices(): List<SyncPairedDevice> =
        net.olamaelcu.livtet.ffi.syncPairedDevices(requireDatabaseFile())

    /** Offer a book this device holds to a paired peer. */
    suspend fun sendBook(editionId: String, deviceId: String): SyncSentBook =
        net.olamaelcu.livtet.ffi.syncSendBook(secretKeyPath(), editionId, deviceId)

    /**
     * Pull a peer's changes into this library and record any books it offered.
     *
     * `sessionToken` is the one the peer issued this device when the pairing was approved;
     * `booksDir`/`coversDir` are where an accepted offer lands.
     */
    suspend fun syncOnce(
        peerNodeId: String,
        sessionToken: String? = null,
        deviceId: String,
        sinceVersion: Long = FROM_STORED_CURSOR,
        limit: Long = 0L,
    ): SyncPullReport {
        // A negative value means "wherever this peer left off", which is the
        // stored cursor; 0 asks for a fresh bootstrap on purpose.
        val since = if (sinceVersion == FROM_STORED_CURSOR) syncCursor(peerNodeId) else sinceVersion
        return net.olamaelcu.livtet.ffi.syncPull(
            secretKeyPath = secretKeyPath(),
            sessionToken =
                sessionToken
                    ?: peerToken(peerNodeId)
                    ?: error("no session token for $peerNodeId; pair with it first"),
            dbPath = requireDatabaseFile(),
            deviceId = deviceId,
            peerNodeId = peerNodeId,
            sinceVersion = since,
            limit = limit,
        )
    }

    /** Accept an offered book, streaming it to `booksDir`. */
    suspend fun acceptOffer(
        from: SyncOfferSummary,
        progress: TransferProgressListener? = null,
    ): SyncReceivedBook {
        val peer = peerFor(from.fromDeviceId)
        val nodeId = peer.nodeId ?: error("that device has no node id to dial")
        return net.olamaelcu.livtet.ffi.syncOfferAccept(
            secretKeyPath = secretKeyPath(),
            sessionToken = tokenFor(peer),
            dbPath = requireDatabaseFile(),
            deviceId = deviceIdForPeer(),
            peerNodeId = nodeId,
            offer =
                SyncOffer(
                    offerId = from.offerId,
                    inventoryId = from.inventoryId,
                    editionId = from.editionId,
                    workId = from.workId,
                    fileName = from.fileName ?: "",
                    hasCover = from.hasCover,
                    deferredChanges = from.deferredChanges,
                ),
            booksDir = booksDir(),
            coversDir = coversDir(),
            progress = progress,
        )
    }

    /**
     * The cover of a book a peer is offering, or null when it has none.
     *
     * Read straight from the peer, before anything is written locally: looking at a book should not
     * be the same act as accepting it.
     */
    suspend fun offerCover(from: SyncOfferSummary): ByteArray? {
        if (!from.hasCover) return null
        val peer = peerFor(from.fromDeviceId)
        val nodeId = peer.nodeId ?: return null
        return net.olamaelcu.livtet.ffi.syncOfferCover(
            secretKeyPath = secretKeyPath(),
            sessionToken = tokenFor(peer),
            peerNodeId = nodeId,
            inventoryId = from.inventoryId,
        )
    }

    /** Decline an offered book. */
    suspend fun rejectOffer(from: SyncOfferSummary) {
        val peer = peerFor(from.fromDeviceId)
        val nodeId = peer.nodeId ?: error("that device has no node id to dial")
        net.olamaelcu.livtet.ffi.syncOfferReject(
            secretKeyPath = secretKeyPath(),
            sessionToken = tokenFor(peer),
            dbPath = requireDatabaseFile(),
            peerNodeId = nodeId,
            offerId = from.offerId,
        )
    }

    /** Books a peer offered and nobody has decided yet, newest first. */
    suspend fun pendingOffers(): List<SyncOfferSummary> =
        net.olamaelcu.livtet.ffi.syncOffers(requireDatabaseFile())

    /** Where the next pull from `peerNodeId` resumes; 0 means never pulled. */
    suspend fun syncCursor(peerNodeId: String): Long =
        net.olamaelcu.livtet.ffi.syncCursor(requireDatabaseFile(), peerNodeId)

    /**
     * Pull every paired peer once, so the inbox is current when it opens.
     *
     * Each pull resumes from that peer's stored cursor, which is what keeps an open from
     * re-fetching the whole library every time.
     */
    suspend fun refreshOffers(): List<SyncOfferSummary> {
        pairedDevices()
            .filter { it.nodeId != null }
            .forEach { device ->
                val nodeId = device.nodeId ?: return@forEach
                runCatching { syncOnce(peerNodeId = nodeId, deviceId = deviceIdForPeer()) }
            }
        return pendingOffers()
    }

    /** Where received books and covers land, created on first use. */
    private fun booksDir(): String =
        File(LivtetApp.getInstance().filesDir, "books").apply { mkdirs() }.absolutePath

    private fun coversDir(): String =
        File(LivtetApp.getInstance().filesDir, "covers").apply { mkdirs() }.absolutePath

    /** The paired device that offered `deviceId`, or the only one if it is unknown. */
    private suspend fun peerFor(deviceId: String): SyncPairedDevice {
        val paired = pairedDevices().filter { it.nodeId != null }
        return paired.firstOrNull { it.deviceId == deviceId }
            ?: paired.singleOrNull()
            ?: error("no paired device to answer: $deviceId")
    }

    private suspend fun tokenFor(peer: SyncPairedDevice): String {
        val nodeId = peer.nodeId ?: error("that device has no node id to dial")
        return peerToken(nodeId) ?: error("no session token for $nodeId; pair with it again")
    }

    /** Pass as `sinceVersion` to resume from the peer's stored cursor. */
    const val FROM_STORED_CURSOR: Long = -1L
    /** Where the reader's display choices are kept. */
    private const val READER_DISPLAY_KEY: String = "reader.display"

    /** How this reader likes a page; the default until they say otherwise. */
    suspend fun readerDisplay(): ReaderDisplay =
        ReaderDisplay.decode(
            net.olamaelcu.livtet.ffi.settingsGet(requireDatabaseFile(), READER_DISPLAY_KEY)
        )

    /** Remember a display choice, so the next book opens the same way. */
    suspend fun saveReaderDisplay(display: ReaderDisplay) =
        net.olamaelcu.livtet.ffi.settingsSet(
            requireDatabaseFile(),
            READER_DISPLAY_KEY,
            display.encode(),
        )

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
     * Where cached covers live. Inside the app's own cache directory, so the system may evict it;
     * the loader treats a miss as "download again".
     */
    private fun coverCacheRoot(): String =
        File(LivtetApp.getInstance().cacheDir, "livtet-covers").absolutePath

    /**
     * A local file for the edition's cover, downloading it once if needed.
     *
     * `livtet-covers` owns the on-disk layout, so the FFI answers every hit. A miss returns null
     * from the lookup and the image is fetched here and handed back for storage; the next call is
     * then served from disk.
     */
    suspend fun resolveCoverPath(editionId: String, remoteUrl: String?): String? =
        withContext(Dispatchers.IO) {
            val store = requireStore()
            val root = coverCacheRoot()

            store
                .coverCachePath(editionId, root)
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    return@withContext it
                }

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

    /** The file on disk for an edition, or null when the library holds none. */
    suspend fun editionFilePath(editionId: String): String? =
        withContext(Dispatchers.IO) { requireStore().getEdition(editionId)?.file?.filePath }

    /**
     * The format row named [name] (`"epub"`, `"pdf"`).
     *
     * `reading_progress.format_id` is a foreign key, so the reader resolves the row once before it
     * can record anything; null means the library has no such format, and then there is nothing to
     * write against.
     */
    suspend fun formatId(name: String): String? =
        withContext(Dispatchers.IO) {
            requireStore().listFormats().firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
        }

    /**
     * Point an edition at a file on disk.
     *
     * This is what a received book leaves behind: `syncOfferAccept` writes the bytes and this
     * records where they landed, which is the row the reader screen resolves before it can open
     * anything.
     */
    suspend fun attachFile(editionId: String, path: String, format: String?, sizeBytes: Long?) =
        withContext(Dispatchers.IO) {
            requireStore()
                .setEditionFile(
                    editionId = editionId,
                    filePath = path,
                    fileFormat = format,
                    fileSizeBytes = sizeBytes,
                )
            Unit
        }

    suspend fun readingProgress(editionId: String): ReadingProgress? =
        withContext(Dispatchers.IO) { requireStore().getReadingProgress(editionId) }

    /**
     * Record where the reader is now.
     *
     * [lastLocation] is the toolkit's `Locator` JSON: the store keeps it verbatim and the sync
     * change log carries it to the reader's other devices, so the desktop resumes at the same
     * passage this phone stopped at. [additionalSeconds] is time spent reading since the previous
     * save.
     */
    suspend fun recordProgress(
        editionId: String,
        formatId: String,
        progression: Double,
        lastLocation: String?,
        additionalSeconds: Long,
    ) =
        withContext(Dispatchers.IO) {
            requireStore()
                .recordReadingProgress(
                    editionId = editionId,
                    formatId = formatId,
                    progress = progression,
                    unit = ProgressUnit.PERCENTAGE,
                    lastLocation = lastLocation,
                    additionalSeconds = additionalSeconds,
                )
            Unit
        }

    /** One edition for the detail sheet, or null when the id is unknown. */
    suspend fun editionDetail(editionId: String): EditionDetailView? =
        withContext(Dispatchers.IO) {
            val edition = requireStore().getEdition(editionId) ?: return@withContext null
            EditionDetailView(
                title = edition.title,
                authors = edition.authors,
                description = edition.description,
                format = edition.format,
                languageCode = edition.languageCode,
                publishedDate = edition.publishedDate?.let(::formatPublishedDate),
                publishers = edition.publishers,
                identifiers = edition.identifiers,
                filePath = edition.file?.filePath,
                fileSizeBytes = edition.file?.fileSizeBytes,
            )
        }

    /** `1989`, `1989-09`, or `1989-09-03`, matching the stored precision. */
    private fun formatPublishedDate(date: PublishedDate): String =
        when (date) {
            is PublishedDate.Year -> "${date.v1}"
            is PublishedDate.YearMonth -> "%04d-%02d".format(date.year, date.month.toInt())
            is PublishedDate.YearMonthDay ->
                "%04d-%02d-%02d".format(date.year, date.month.toInt(), date.day.toInt())
        }

    /** Record a catalog cover URL for an edition that was imported without one. */
    suspend fun setEditionCoverUrl(editionId: String, coverUrl: String) =
        withContext(Dispatchers.IO) { requireStore().setEditionCoverUrl(editionId, coverUrl) }

    private class DownloadedImage(val bytes: ByteArray, val ext: String)

    /**
     * Fetch an image and guess its extension from the response, falling back to the URL's suffix.
     * Returns null on any failure: a missing cover is a degraded row, not an error screen.
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
            "image/jpeg",
            "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/avif" -> "avif"
            "image/gif" -> "gif"
            else ->
                url.substringAfterLast('/')
                    .substringBefore('?')
                    .substringAfterLast('.', "jpg")
                    .lowercase()
                    .takeIf { it in setOf("jpg", "jpeg", "png", "webp", "avif", "gif") } ?: "jpg"
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

    // ── Annotations (notes pinned to a passage) ──────────────────

    /** A note as the reader screen renders it. */
    data class AnnotationView(
        val id: String,
        val content: String,
        val location: String?,
        val createdAt: String,
    )

    /**
     * Notes on one edition, oldest first.
     *
     * `location` is the toolkit's `Locator` JSON, the same shape reading progress stores, so a note
     * points at a passage the reader can return to.
     */
    suspend fun listAnnotations(editionId: String): List<AnnotationView> =
        withContext(Dispatchers.IO) {
            requireStore().listAnnotations(editionId).map {
                AnnotationView(
                    id = it.id.toString(),
                    content = it.content,
                    location = it.location,
                    createdAt = it.createdAt,
                )
            }
        }

    /** Add a note to an edition; returns the new annotation's id. */
    suspend fun addAnnotation(editionId: String, content: String, location: String?): String =
        withContext(Dispatchers.IO) {
            requireStore()
                .addAnnotation(editionId = editionId, content = content, location = location)
                .id
                .toString()
        }

    suspend fun deleteAnnotation(id: String): Boolean =
        withContext(Dispatchers.IO) { requireStore().deleteAnnotation(id) }

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
        withContext(Dispatchers.IO) { requireStore().removeEditionFromList(shelfId, editionId) }

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
     * Create a work with one edition and attach [identifiers] (URN strings), returning the new
     * edition id.
     *
     * Contributors are not persisted: the FFI has no author-creation method, so a catalog
     * contributor cannot become an `authors` row yet. The work title is what the Library list
     * renders.
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
            val work = store.createWork(title = title, sortTitle = null, description = description)
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
     * `authors` has no unique constraint, so importing the same person twice would otherwise leave
     * two rows that a rename could not keep in step.
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
 * Parse the FFI's RFC 3339 (UTC) timestamps into epoch millis for the UI. The Rust side renders
 * sub-second precision variably, so it is truncated before parsing; malformed input degrades to
 * `null`.
 */
private fun parseRfc3339Millis(value: String): Long? =
    try {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        formatter.parse(value.substringBefore('.').removeSuffix("Z"))?.time
    } catch (_: java.text.ParseException) {
        null
    }
