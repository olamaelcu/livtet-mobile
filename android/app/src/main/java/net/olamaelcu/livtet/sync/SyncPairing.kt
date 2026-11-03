package net.olamaelcu.livtet.sync

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.olamaelcu.livtet.DiscoveredSyncDevice
import net.olamaelcu.livtet.ffi.SyncPairingResult
import net.olamaelcu.livtet.ffi.pairSyncDevice
import net.olamaelcu.livtet.ffi.syncPairingStatus
import ulid.ULID

/** A peer that approved this device's claim, and the token every later request carries. */
data class PairedPeer(
    val nodeId: String,
    val name: String,
    val deviceId: String,
    val sessionToken: String,
)

/**
 * Pairing a discovered peer over iroh, plus the small amount of identity this
 * device needs to file a claim.
 *
 * The peer is addressed by the node id from the mDNS TXT `id` record and nothing
 * else: iroh resolves that id (mDNS on the LAN, the relay off it), so no host or
 * port is involved and the phone never has to accept an inbound connection.
 *
 * Three things are persisted, all in the `sync` DataStore file:
 *
 * - `device_id` — a ULID generated once. The desktop stores it as this device's
 *   identity, so regenerating it would orphan the pairing record there.
 * - `device_name` — what the desktop shows while deciding. Defaults to the phone
 *   model; editable before a claim is filed.
 * - `peer:<nodeId>:{name,device_id,session_token}` — one entry per approved
 *   peer. The token is what later data routes authenticate with, so it is kept
 *   with the peer it belongs to rather than in memory.
 *
 * The iroh secret key lives beside the app's files, not in DataStore: it is a
 * credential the Rust endpoint reads directly, and its node id is the identity
 * the peer keyed the claim on.
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
    fun secretKeyPath(context: Context): String =
        File(context.applicationContext.filesDir, "sync/node.key").absolutePath

    /**
     * This device's ULID, created on first use.
     *
     * The read and the write share one `edit` block so two callers racing on
     * first launch still end up with one identity.
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

    suspend fun setDeviceName(context: Context, name: String) {
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
                            name = prefs[stringPreferencesKey("$PEER_PREFIX$nodeId$PEER_NAME_SUFFIX")]
                                .orEmpty(),
                            deviceId =
                                prefs[stringPreferencesKey("$PEER_PREFIX$nodeId$PEER_DEVICE_ID_SUFFIX")]
                                    .orEmpty(),
                            sessionToken = token,
                        )
                }
                .toMap()
        }

    /**
     * File a claim with [peer] and wait up to [waitSecs] for the reader's
     * decision, recording the session token when it is approved.
     */
    suspend fun pair(
        context: Context,
        peer: DiscoveredSyncDevice,
        name: String,
        waitSecs: Int = WAIT_SECS,
    ): SyncPairingResult {
        require(peer.nodeId.isNotBlank()) {
            "the advertised record for ${peer.name} carries no node id"
        }
        val claimName = name.trim().ifBlank { deviceName(context) }
        val result =
            pairSyncDevice(
                secretKeyPath = secretKeyPath(context),
                peerNodeId = peer.nodeId,
                deviceId = deviceId(context),
                name = claimName,
                deviceType = DEVICE_TYPE,
                waitSecs = waitSecs.toUInt(),
            )
        record(context, peer, claimName, result)
        return result
    }

    /** Keep waiting on a claim already filed by [pair]; same identity, same peer. */
    suspend fun status(
        context: Context,
        peer: DiscoveredSyncDevice,
        name: String,
        claim: String,
        waitSecs: Int = WAIT_SECS,
    ): SyncPairingResult {
        val result =
            syncPairingStatus(
                secretKeyPath = secretKeyPath(context),
                peerNodeId = peer.nodeId,
                claim = claim,
                waitSecs = waitSecs.toUInt(),
            )
        record(context, peer, name.trim(), result)
        return result
    }

    /**
     * Persist the approval. `Pending` has nothing to keep yet, and a refusal leaves any earlier
     * pairing alone: a peer declining a new claim has not revoked the token it already issued.
     */
    private suspend fun record(
        context: Context,
        peer: DiscoveredSyncDevice,
        name: String,
        result: SyncPairingResult,
    ) {
        if (result !is SyncPairingResult.Paired) return
        context.applicationContext.store.edit { prefs ->
            prefs[stringPreferencesKey("$PEER_PREFIX${peer.nodeId}$PEER_NAME_SUFFIX")] =
                name.ifBlank { peer.name }
            prefs[stringPreferencesKey("$PEER_PREFIX${peer.nodeId}$PEER_DEVICE_ID_SUFFIX")] =
                result.deviceId
            prefs[stringPreferencesKey("$PEER_PREFIX${peer.nodeId}$PEER_TOKEN_SUFFIX")] =
                result.sessionToken
        }
    }

    private fun defaultDeviceName(): String {
        val model = Build.MODEL.orEmpty().trim()
        return if (model.isEmpty()) "Livtet Android" else "Livtet on $model"
    }
}
