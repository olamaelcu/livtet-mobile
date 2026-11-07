package net.olamaelcu.livtet

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * A Livtet peer seen on the local network.
 *
 * The record is the desktop's own advertisement: `id` is the iroh node id a pairing dials,
 * `device_id` is the stable ULID it answers with. They are kept apart deliberately — one addresses
 * the connection, the other identifies the device a paired row is keyed on.
 */
data class NearbyPeer(
    val nodeId: String,
    val deviceId: String?,
    val name: String?,
    val deviceType: String?,
) {
    /** What to call it in a list, falling back to a short node id. */
    val label: String
        get() = name?.takeIf { it.isNotBlank() } ?: "Peer ${nodeId.take(6)}"
}

/** The TXT keys the desktop's `livtet-mdns` advertises. */
internal object MdnsKeys {
    const val SERVICE_TYPE = "_livtet-sync._tcp"

    const val NODE_ID = "id"
    const val DEVICE_ID = "device_id"
    const val NAME = "name"
    const val DEVICE_FLAVOR = "deviceFlavor"
    const val VERSION = "version"
}

/**
 * Browse for peers advertising `_livtet-sync._tcp`.
 *
 * Android resolves one service at a time, so lookups are queued: a second `resolveService` while
 * the first is in flight fails outright. [close] must be called — the platform keeps the browse
 * alive otherwise and drains battery for a screen nobody is looking at.
 */
class NearbyPeers(context: Context, private val ownNodeId: String? = null) : AutoCloseable {
    private val nsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val closed = AtomicBoolean(false)

    @Volatile private var listener: NsdManager.DiscoveryListener? = null

    /** Peers found while the browse runs, newest set last. */
    fun browse(): Flow<List<NearbyPeer>> = callbackFlow {
        val manager = nsdManager
        if (manager == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val found = LinkedHashMap<String, NearbyPeer>()
        val pending = ArrayDeque<NsdServiceInfo>()
        var resolving = false

        // One resolve at a time, drained in arrival order.
        fun drain() {
            if (resolving) return
            val next = pending.removeFirstOrNull() ?: return
            resolving = true
            manager.resolveService(
                next,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        resolving = false
                        drain()
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        resolving = false
                        val peer = peerFrom(serviceInfo.attributes)
                        if (peer != null && peer.nodeId != ownNodeId) {
                            found[peer.nodeId] = peer
                            trySend(found.values.toList())
                        }
                        drain()
                    }
                },
            )
        }

        val listener =
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (!serviceInfo.serviceType.startsWith(MdnsKeys.SERVICE_TYPE)) return
                    pending.addLast(serviceInfo)
                    drain()
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    // Lost records arrive by name, not by node id, so the
                    // next poll is what actually clears a departed peer.
                }

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    trySend(found.values.toList())
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            }

        this@NearbyPeers.listener = listener
        runCatching {
                manager.discoverServices(
                    MdnsKeys.SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener,
                )
            }
            .onFailure { trySend(emptyList()) }

        awaitClose {
            if (closed.compareAndSet(false, true)) {
                runCatching { manager.stopServiceDiscovery(listener) }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val manager = nsdManager ?: return
        listener?.let { runCatching { manager.stopServiceDiscovery(it) } }
    }
}

/**
 * Read one advertisement's TXT record, or null when it names no node.
 *
 * `NsdServiceInfo` hands the record over as bytes, and the daemon writes UTF-8, so that is what
 * they are decoded as.
 */
internal fun peerFrom(attributes: Map<String, ByteArray>): NearbyPeer? {
    fun text(key: String): String? =
        attributes[key]?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }

    val nodeId = text(MdnsKeys.NODE_ID) ?: return null
    return NearbyPeer(
        nodeId = nodeId,
        deviceId = text(MdnsKeys.DEVICE_ID),
        name = text(MdnsKeys.NAME),
        deviceType = text(MdnsKeys.DEVICE_FLAVOR),
    )
}
