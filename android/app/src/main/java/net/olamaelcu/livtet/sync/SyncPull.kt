package net.olamaelcu.livtet.sync

import android.content.Context
import android.os.Environment
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.olamaelcu.livtet.ffi.SyncOffer
import net.olamaelcu.livtet.ffi.TransferProgressListener
import net.olamaelcu.livtet.ffi.syncOfferAccept
import net.olamaelcu.livtet.ffi.syncOfferReject
import net.olamaelcu.livtet.ffi.syncPull
import timber.log.Timber
import java.io.File

/**
 * Where received books land.
 *
 * Deliberately the shared Documents folder and not app-private storage: KOReader browses it, so a
 * received book is openable without this app being involved. Writing there needs all-files access,
 * which the app asks for when the reader first enables book receiving.
 */
const val BOOKS_DIR = "/sdcard/Documents/Livtet"
const val COVERS_DIR = "$BOOKS_DIR/covers"

/** A book a paired peer is offering, waiting on the reader's decision. */
@Serializable
data class PendingBook(
    val offerId: String,
    val peerNodeId: String,
    val peerName: String,
    val inventoryId: String,
    val editionId: String,
    val workId: String,
    val fileName: String,
    val hasCover: Boolean,
    /** Held rows, echoed back to the core when the book is accepted. */
    val deferredChanges: String,
)

/** A book that landed on disk, and where. */
@Serializable data class ReceivedBook(val fileName: String, val filePath: String)

/**
 * How far a peer's bulk download has got.
 *
 * One book at a time, so this is a book counter rather than a byte counter: the download itself
 * reports no byte progress, and a row that names the file in flight is honest about what is
 * happening for however long it takes.
 */
data class TransferProgress(
    /** Which book of the peer's queue this is, one-based, and how many there are. */
    val done: Int,
    val total: Int,
    val fileName: String,
    /** Bytes written for this file so far, and its size; zero until the first chunk. */
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
)

/** How far through the file in flight, 0–100. Byte-based once the size is known. */
fun TransferProgress.percentOfCurrentFile(): Int {
    if (bytesTotal > 0) return ((bytesDone * 100) / bytesTotal).toInt().coerceIn(0, 100)
    return (done * 100) / total.coerceAtLeast(1)
}

/**
 * Pulling paired peers' libraries and receiving offered books.
 *
 * The protocol is pull-based: peers record offers in their own change logs, and this side asks for
 * them on an interval — the same shape as the desktop daemon, so a book appears without either app
 * being told about the other.
 *
 * Pending offers live in DataStore the same way the paired peer records do: the list is durable, so
 * an offer survives a restart while it waits for a decision.
 */
object SyncPull {
    /** How often each paired peer is asked for changes. */
    const val PULL_INTERVAL_MS = 60_000L

    /** Rows per page while catching up; the peer reports `hasMore` past this. */
    private const val PAGE_SIZE = 250L

    /** Bounded catch-up so one very stale peer cannot spin forever. */
    private const val MAX_PAGES = 20

    // Its own file: AndroidX refuses a second DataStore over the same file, and
    // `SyncPairing` already owns "sync". Offers and cursors are this file's business.
    private val Context.store by preferencesDataStore("sync_pull")
    private const val OFFER_PREFIX = "offer:"
    private const val RECEIVED_PREFIX = "received:"
    private const val CURSOR_PREFIX = "cursor:"
    private val AUTO_ACCEPT = booleanPreferencesKey("auto_accept_books")

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var started = false

    /** Whether the app can write into [BOOKS_DIR] at all. */
    fun hasStorageAccess(): Boolean = Environment.isExternalStorageManager()

    /**
     * Start asking paired peers for changes on an interval.
     *
     * Idempotent, and deliberately process-wide: a pull must not depend on the Settings screen
     * being open, or a book offered while the reader is reading would wait until they happened to
     * visit Settings.
     */
    fun startPolling(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch {
            while (true) {
                runCatching { pullAll(app) }.onFailure { Timber.tag(TAG).w(it, "pull failed") }
                delay(PULL_INTERVAL_MS)
            }
        }
    }

    /** Pull every paired peer once. Exposed for the UI's "check now" and tests. */
    suspend fun pullAll(context: Context) {
        if (!hasStorageAccess()) {
            Timber.tag(TAG).i("skipping pull: no all-files access")
            return
        }
        val peers = SyncPairing.pairedPeers(context).first().values
        peers.forEach { peer ->
            // A failure here is per-peer: one unreachable desktop must not stop the
            // others, but it must not vanish either.
            runCatching { pullPeer(context, peer) }
                .onFailure { Timber.tag(TAG).w(it, "pull from ${peer.nodeId.take(8)} failed") }
        }
    }

    private suspend fun pullPeer(
        context: Context,
        peer: PairedPeer,
    ) {
        val cursorKey = stringPreferencesKey("$CURSOR_PREFIX${peer.nodeId}")
        var cursor = context.store.data.first()[cursorKey]?.toLongOrNull() ?: 0L
        var pages = 0
        var more = false
        do {
            val report =
                syncPull(
                    secretKeyPath = SyncPairing.secretKeyPath(context),
                    sessionToken = peer.sessionToken,
                    dbPath = libraryPath(context),
                    deviceId = SyncPairing.deviceId(context),
                    peerNodeId = peer.nodeId,
                    sinceVersion = cursor,
                    limit = PAGE_SIZE,
                )
            report.offers.forEach { offer -> recordOffer(context, peer, offer) }
            cursor = report.cursor
            context.store.edit { prefs -> prefs[cursorKey] = cursor.toString() }
            pages += 1
            more = report.hasMore
        } while (more && pages < MAX_PAGES)
        Timber.tag(TAG)
            .i("pulled ${peer.name.ifBlank { peer.nodeId.take(8) }} up to version $cursor")
    }

    private suspend fun recordOffer(
        context: Context,
        peer: PairedPeer,
        offer: SyncOffer,
    ) {
        val book =
            PendingBook(
                offerId = offer.offerId,
                peerNodeId = peer.nodeId,
                peerName = peer.name,
                inventoryId = offer.inventoryId,
                editionId = offer.editionId,
                workId = offer.workId,
                fileName = offer.fileName,
                hasCover = offer.hasCover,
                deferredChanges = offer.deferredChanges,
            )
        context.store.edit { prefs ->
            prefs[stringPreferencesKey("$OFFER_PREFIX${book.offerId}")] = json.encodeToString(book)
        }
        Timber.tag(TAG).i("offer ${book.offerId} for ${book.fileName}")
        if (autoAccept(context).first()) {
            runCatching { accept(context, book) }
                .onFailure { Timber.tag(TAG).w(it, "auto-accept failed for ${book.offerId}") }
        }
    }

    /** Offers waiting for a decision, newest first by file name. */
    fun pendingOffers(context: Context): Flow<List<PendingBook>> =
        context.applicationContext.store.data.map { prefs ->
            prefs
                .asMap()
                .keys
                .map { it.name }
                .filter { it.startsWith(OFFER_PREFIX) }
                .mapNotNull { key -> prefs[stringPreferencesKey(key)] }
                .mapNotNull { raw ->
                    runCatching { json.decodeFromString<PendingBook>(raw) }.getOrNull()
                }
                .sortedBy { it.fileName.lowercase() }
        }

    /**
     * Download an offered book into [BOOKS_DIR] and register it in the library.
     *
     * The core applies the offer's held rows with the local path, so the edition arrives pointing
     * at a real file — that is what makes it openable.
     */
    suspend fun accept(
        context: Context,
        book: PendingBook,
        onBytes: (done: Long, total: Long) -> Unit = { _, _ -> },
    ) {
        val peer =
            SyncPairing.pairedPeers(context).first()[book.peerNodeId]
                ?: error("no session token for ${book.peerNodeId}")
        val received =
            syncOfferAccept(
                secretKeyPath = SyncPairing.secretKeyPath(context),
                sessionToken = peer.sessionToken,
                dbPath = libraryPath(context),
                deviceId = SyncPairing.deviceId(context),
                peerNodeId = book.peerNodeId,
                offer =
                    SyncOffer(
                        offerId = book.offerId,
                        inventoryId = book.inventoryId,
                        editionId = book.editionId,
                        workId = book.workId,
                        fileName = book.fileName,
                        hasCover = book.hasCover,
                        deferredChanges = book.deferredChanges,
                    ),
                booksDir = BOOKS_DIR,
                coversDir = COVERS_DIR,
                progress = ThrottledProgress(onBytes),
            )
        context.store.edit { prefs ->
            prefs[stringPreferencesKey("$RECEIVED_PREFIX${book.offerId}")] =
                json.encodeToString(ReceivedBook(book.fileName, received.filePath))
            prefs.remove(stringPreferencesKey("$OFFER_PREFIX${book.offerId}"))
        }
        Timber.tag(TAG).i("accepted ${book.fileName} -> ${received.filePath}")
    }

    /**
     * Receive every book one peer is currently offering.
     *
     * Offers are the only way file bytes move, so this is "sync the files from that peer": each
     * pending offer is downloaded in turn, one after another rather than in parallel, because every
     * download holds the peer's stream open and writes into the shared books folder.
     *
     * One book failing does not stop the rest: its offer stays pending for a later attempt and the
     * reason is returned to the caller.
     */
    suspend fun acceptAllFrom(
        context: Context,
        peerNodeId: String,
        onProgress: (TransferProgress) -> Unit = {},
    ): List<String> {
        val offered = pendingOffers(context).first().filter { it.peerNodeId == peerNodeId }
        val failures = mutableListOf<String>()
        offered.forEachIndexed { index, book ->
            // Announced before the bytes move, so the reader sees which file is in
            // flight for however long the download takes, not just that it started.
            onProgress(TransferProgress(index + 1, offered.size, book.fileName))
            var transferred = TransferProgress(index + 1, offered.size, book.fileName)
            runCatching {
                accept(context, book) { done, total ->
                    transferred = transferred.copy(bytesDone = done, bytesTotal = total)
                    onProgress(transferred)
                }
            }
                .onFailure { error ->
                    failures += "${book.fileName}: ${error.message ?: "could not receive"}"
                }
        }
        Timber.tag(TAG).i("received ${offered.size - failures.size}/${offered.size} from peer")
        return failures
    }

    /** Decline an offer; the peer keeps the book. */
    suspend fun reject(
        context: Context,
        book: PendingBook,
    ) {
        val peer =
            SyncPairing.pairedPeers(context).first()[book.peerNodeId]
                ?: error("no session token for ${book.peerNodeId}")
        syncOfferReject(
            secretKeyPath = SyncPairing.secretKeyPath(context),
            sessionToken = peer.sessionToken,
            peerNodeId = book.peerNodeId,
            offerId = book.offerId,
        )
        forget(context, book.offerId)
    }

    private suspend fun forget(
        context: Context,
        offerId: String,
    ) {
        context.store.edit { prefs -> prefs.remove(stringPreferencesKey("$OFFER_PREFIX$offerId")) }
    }

    /** Whether offers are accepted without asking. */
    fun autoAccept(context: Context): Flow<Boolean> = context.applicationContext.store.data.map { prefs -> prefs[AUTO_ACCEPT] ?: false }

    suspend fun setAutoAccept(
        context: Context,
        enabled: Boolean,
    ) {
        context.applicationContext.store.edit { prefs -> prefs[AUTO_ACCEPT] = enabled }
    }

    /** The library database the received rows are applied to. */
    private fun libraryPath(context: Context): String = File(context.applicationContext.filesDir, "livtet.db").absolutePath

    private const val TAG = "SyncPull"
}

/**
 * Coalesces the per-chunk callbacks into something a Compose state can hold.
 *
 * A large book arrives in hundreds of chunks, and recomposing on every one would cost more than the
 * transfer. Emits at most once per percent or per 200 ms, and always the final value so the bar
 * lands on 100%.
 */
private class ThrottledProgress(private val report: (done: Long, total: Long) -> Unit) :
    TransferProgressListener {
    private var lastDone = 0L
    private var lastAt = 0L

    override fun onProgress(
        done: ULong,
        total: ULong,
    ) {
        val written = done.toLong()
        val size = total.toLong()
        val now = System.nanoTime()
        val finished = size > 0 && written >= size
        val step = (size / 100).coerceAtLeast(1L)
        if (finished || written - lastDone >= step || now - lastAt >= EMIT_NANOS) {
            lastDone = written
            lastAt = now
            report(written, size)
        }
    }

    private companion object {
        const val EMIT_NANOS = 200_000_000L
    }
}
