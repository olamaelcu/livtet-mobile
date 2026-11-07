package net.olamaelcu.livtet.sync

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.olamaelcu.livtet.ffi.SyncPairingResult
import net.olamaelcu.livtet.ffi.advertiseSyncDevice
import net.olamaelcu.livtet.ffi.pairSyncDevice
import net.olamaelcu.livtet.ffi.peerSyncIdentity
import net.olamaelcu.livtet.ffi.stopAdvertisingSyncDevice
import net.olamaelcu.livtet.ffi.syncPairingStatus
import ulid.ULID
import java.io.File

/** A peer that approved this device's claim, and the token every later request carries. */
data class PairedPeer(
    val nodeId: String,
    val name: String,
    val deviceId: String,
    val sessionToken: String,
)

/**
 * Pairing a discovered peer over iroh, plus the small amount of identity this device needs to file
 * a claim.
 *
 * The peer is addressed by the node id from the mDNS TXT `id` record and nothing else: iroh
 * resolves that id (mDNS on the LAN, the relay off it), so no host or port is involved and the
 * phone never has to accept an inbound connection.
 *
 * Three things are persisted, all in the `sync` DataStore file:
 *
 * - `device_id` — a ULID generated once. The desktop stores it as this device's identity, so
 *   regenerating it would orphan the pairing record there.
 * - `device_name` — what the desktop shows while deciding. Defaults to the phone model; editable
 *   before a claim is filed.
 * - `peer:<nodeId>:{name,device_id,session_token}` — one entry per approved peer. The token is what
 *   later data routes authenticate with, so it is kept with the peer it belongs to rather than in
 *   memory.
 *
 * The iroh secret key lives beside the app's files, not in DataStore: it is a credential the Rust
 * endpoint reads directly, and its node id is the identity the peer keyed the claim on.
 */
object SyncPairing {
    /** Device type tag the desktop maps through `DeviceType::from_tag`. */
    const val DEVICE_TYPE = "android"

    /** How long a pairing call waits on the reader before reporting `Pending`. */
    const val WAIT_SECS = 30

    private const val NAME = "sync"
    private val Context.store by preferencesDataStore(NAME)

    private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
    private val DEVICE_NAME_KEY = stringPreferencesKey("device_name")

    private const val PEER_PREFIX = "peer:"
    private const val PEER_NAME_SUFFIX = ":name"
    private const val PEER_DEVICE_ID_SUFFIX = ":device_id"
    private const val PEER_TOKEN_SUFFIX = ":session_token"

    /** Absolute path of the iroh secret key file the FFI endpoint reads and creates. */
    fun secretKeyPath(context: Context): String = File(context.applicationContext.filesDir, "sync/node.key").absolutePath

    /**
     * Announce this device on `_livtet-sync._tcp` and return its node id.
     *
     * Desktops find peers by browsing that service, so without this the phone is only ever the side
     * that looks. The id comes back so the UI can show the reader what to type on the desktop if
     * they would rather pair from there.
     *
     * Idempotent in core: calling it again for the same key path returns the id already advertised
     * rather than binding a second endpoint.
     */
    suspend fun startAdvertising(
        context: Context,
        name: String,
    ): String =
        advertiseSyncDevice(
            secretKeyPath = secretKeyPath(context),
            deviceId = deviceId(context),
            name = name.trim().ifBlank { deviceName(context) },
            deviceType = DEVICE_TYPE,
        )

    /** Withdraw the advertisement. Safe when nothing is advertised. */
    fun stopAdvertising(context: Context) {
        stopAdvertisingSyncDevice(secretKeyPath(context))
    }

    /**
     * Fill in a paired peer's own device id by asking it.
     *
     * Pairing replies with the claiming device's id, so records written before that was understood
     * hold this phone's id under the peer's node id. Asking the peer is the only way to learn the
     * right value, and it costs one request on an already-paired peer.
     */
    suspend fun refreshPeerIdentity(
        context: Context,
        nodeId: String,
    ) {
        val identity =
            runCatching { peerSyncIdentity(secretKeyPath(context), nodeId) }.getOrNull() ?: return
        if (identity.deviceId.isBlank()) return
        context.applicationContext.store.edit { prefs ->
            prefs[stringPreferencesKey("$PEER_PREFIX$nodeId$PEER_DEVICE_ID_SUFFIX")] =
                identity.deviceId
        }
    }

    /**
     * Drop peer fields an earlier build filled with this device's own identity.
     *
     * The pairing reply carries the claiming device's id, so records written then hold this phone's
     * ids under the peer's node id. The read path already hides those values; removing them keeps
     * storage honest and lets [refreshPeerIdentity] fill in the real ones.
     */
    suspend fun repairPairedPeers(context: Context) {
        val ownName = deviceName(context)
        val ownDeviceId = deviceId(context)
        context.applicationContext.store.edit { prefs ->
            val peerNodes =
                prefs
                    .asMap()
                    .keys
                    .map { it.name }
                    .filter { it.startsWith(PEER_PREFIX) && it.endsWith(PEER_TOKEN_SUFFIX) }
                    .map { it.removePrefix(PEER_PREFIX).removeSuffix(PEER_TOKEN_SUFFIX) }
            peerNodes.forEach { nodeId ->
                val nameKey = stringPreferencesKey("$PEER_PREFIX$nodeId$PEER_NAME_SUFFIX")
                val deviceKey = stringPreferencesKey("$PEER_PREFIX$nodeId$PEER_DEVICE_ID_SUFFIX")
                if (prefs[nameKey] == ownName) prefs.remove(nameKey)
                if (prefs[deviceKey] == ownDeviceId) prefs.remove(deviceKey)
            }
        }
    }

    /**
     * Forget an approved pairing: its name, device id, and session token.
     *
     * The token is the only thing that authenticates this device to that peer, so deleting it
     * unpairs this side. The peer keeps its own record — nothing on the wire revokes a decided
     * pairing, and the desktop removes the row on its side — so the UI says so rather than implying
     * the desktop forgot.
     *
     * Removing the token is also what drops the row: [pairedPeers] enumerates peers by that key.
     */
    suspend fun forgetPeer(
        context: Context,
        nodeId: String,
    ) {
        context.applicationContext.store.edit { prefs ->
            prefs.remove(stringPreferencesKey("$PEER_PREFIX$nodeId$PEER_NAME_SUFFIX"))
            prefs.remove(stringPreferencesKey("$PEER_PREFIX$nodeId$PEER_DEVICE_ID_SUFFIX"))
            prefs.remove(stringPreferencesKey("$PEER_PREFIX$nodeId$PEER_TOKEN_SUFFIX"))
        }
    }

    /**
     * This device's ULID, created on first use.
     *
     * The read and the write share one `edit` block so two callers racing on first launch still end
     * up with one identity.
     */
    suspend fun deviceId(context: Context): String {
        val store = context.applicationContext.store
        store.edit { prefs ->
            if (prefs[DEVICE_ID_KEY] == null) prefs[DEVICE_ID_KEY] = ULID.randomULID()
        }
        return requireNotNull(store.data.first()[DEVICE_ID_KEY]) {
            "the device id was written above, so the read cannot miss it"
        }
    }

    /** The name the desktop shows for this device. */
    suspend fun deviceName(context: Context): String =
        context.applicationContext.store.data.first()[DEVICE_NAME_KEY].orEmpty().ifBlank {
            defaultDeviceName()
        }

    suspend fun setDeviceName(
        context: Context,
        name: String,
    ) {
        context.applicationContext.store.edit { prefs ->
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                prefs.remove(DEVICE_NAME_KEY)
            } else {
                prefs[DEVICE_NAME_KEY] = trimmed
            }
        }
    }

    /** Approved peers, keyed by node id. */
    fun pairedPeers(context: Context): Flow<Map<String, PairedPeer>> =
        context.applicationContext.store.data.map { prefs ->
            // An earlier build stored this device's own claim name as the peer's name, so a row
            // read "Livtet on …" for the desktop. Treat that value as absent rather than showing
            // the reader's own device under someone else's node id.
            val ownName = prefs[DEVICE_NAME_KEY].orEmpty()
            val ownDeviceId = prefs[DEVICE_ID_KEY].orEmpty()
            prefs
                .asMap()
                .entries
                .mapNotNull { (key, value) ->
                    val keyName = key.name
                    if (!keyName.startsWith(PEER_PREFIX) || !keyName.endsWith(PEER_TOKEN_SUFFIX)) {
                        return@mapNotNull null
                    }
                    val nodeId = keyName.removePrefix(PEER_PREFIX).removeSuffix(PEER_TOKEN_SUFFIX)
                    val token = value as? String ?: return@mapNotNull null
                    nodeId to
                        PairedPeer(
                            nodeId = nodeId,
                            name =
                                prefs[stringPreferencesKey("$PEER_PREFIX$nodeId$PEER_NAME_SUFFIX")]
                                    .orEmpty()
                                    .takeUnless { it == ownName }
                                    .orEmpty(),
                            deviceId =
                                prefs[
                                    stringPreferencesKey(
                                        "$PEER_PREFIX$nodeId$PEER_DEVICE_ID_SUFFIX",
                                    ),
                                ]
                                    .orEmpty()
                                    .takeUnless { it == ownDeviceId }
                                    .orEmpty(),
                            sessionToken = token,
                        )
                }
                .toMap()
        }

    /**
     * File a claim with [peer] and wait up to [waitSecs] for the reader's decision, recording the
     * session token when it is approved.
     */
    suspend fun pair(
        context: Context,
        nodeId: String,
        label: String,
        name: String,
        waitSecs: Int = WAIT_SECS,
    ): SyncPairingResult {
        val target = nodeId.trim()
        require(target.isNotEmpty()) { "a pairing target needs a node id" }
        val claimName = name.trim().ifBlank { deviceName(context) }
        val result =
            pairSyncDevice(
                secretKeyPath = secretKeyPath(context),
                peerNodeId = target,
                deviceId = deviceId(context),
                name = claimName,
                deviceType = DEVICE_TYPE,
                waitSecs = waitSecs.toUInt(),
            )
        record(context, target, label, result)
        return result
    }

    /** Keep waiting on a claim already filed by [pair]; same identity, same peer. */
    suspend fun status(
        context: Context,
        nodeId: String,
        label: String,
        claim: String,
        waitSecs: Int = WAIT_SECS,
    ): SyncPairingResult {
        val target = nodeId.trim()
        val result =
            syncPairingStatus(
                secretKeyPath = secretKeyPath(context),
                peerNodeId = target,
                claim = claim,
                waitSecs = waitSecs.toUInt(),
            )
        record(context, target, label, result)
        return result
    }

    /**
     * Persist the approval. `Pending` has nothing to keep yet, and a refusal leaves any earlier
     * pairing alone: a peer declining a new claim has not revoked the token it already issued.
     */
    private suspend fun record(
        context: Context,
        nodeId: String,
        label: String,
        result: SyncPairingResult,
    ) {
        if (result !is SyncPairingResult.Paired) return
        // The pairing reply carries the *claiming* device's id (ours), so the
        // peer's own id has to be asked for separately. A failure here only costs
        // the row its device id; the token is still worth keeping.
        val peerDeviceId =
            runCatching { peerSyncIdentity(secretKeyPath(context), nodeId) }
                .getOrNull()
                ?.deviceId
                .orEmpty()
        context.applicationContext.store.edit { prefs ->
            prefs[stringPreferencesKey("$PEER_PREFIX$nodeId$PEER_NAME_SUFFIX")] = label.trim()
            prefs[stringPreferencesKey("$PEER_PREFIX$nodeId$PEER_DEVICE_ID_SUFFIX")] = peerDeviceId
            prefs[stringPreferencesKey("$PEER_PREFIX$nodeId$PEER_TOKEN_SUFFIX")] =
                result.sessionToken
        }
    }

    private fun defaultDeviceName(): String {
        val model = Build.MODEL.orEmpty().trim()
        return if (model.isEmpty()) "Livtet Android" else "Livtet on $model"
    }
}
