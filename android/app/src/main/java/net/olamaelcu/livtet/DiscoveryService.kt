package net.olamaelcu.livtet

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A discovered livtet/koreader sync peer on the local network.
 *
 * The TXT record carries two distinct identifiers (`core/livtet-sync-server/src/mdns.rs`):
 * `id` is the iroh node id that pairing dials, and `device_id` is the stable device ULID. They are
 * kept apart deliberately — `nodeId` is what a future pairing flow hands to the core, `deviceId` is
 * what identifies the device to the user and to stored state.
 *
 * `name` is the peer's friendly display name from the TXT `name` record, falling back to the mDNS
 * instance label. `host` and `port` identify the endpoint; `appVersion` and `deviceFlavor` come from
 * the other TXT fields.
 */
data class DiscoveredSyncDevice(
    val nodeId: String,
    val deviceId: String,
    val name: String,
    val host: String,
    val port: Int,
    val appVersion: String?,
    val deviceFlavor: String?,
)

/**
 * mDNS discovery for `_livtet-sync._tcp` services on the local network. Uses the Android NSD API to
 * browse for the same service type that the desktop's `livtet_mdns::advertise_sync` publishes and
 * that the iOS `DiscoveryService.swift` (via `NWBrowser`) also looks for.
 *
 * The `start()` call is idempotent — calling it on an already-running service is a no-op. `stop()`
 * is the matching teardown.
 */
class DiscoveryService(private val context: Context) {
    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val _devices = MutableStateFlow<List<DiscoveredSyncDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredSyncDevice>> = _devices.asStateFlow()

    private val byName: MutableMap<String, DiscoveredSyncDevice> = mutableMapOf()

    // Cached for `NsdManager.resolveService` — the discovery
    // listener hands us a `serviceInfo` we then resolve to
    // populate `host` / `port`.
    private var pendingResolve: NsdManager.ResolveListener? = null

    private val discoveryListener =
        object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.i(TAG, "discovering $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                // Drop the trailing `.<service-type>` to compare by
                // instance name. We only care about `_livtet-sync._tcp`
                // services, but the listener is registered for that
                // type so anything we see here is on-list.
                val instance = service.serviceName.substringBefore(".")
                Log.i(TAG, "found $instance on ${service.host}:${service.port}")
                val pending =
                    object : NsdManager.ResolveListener {
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val attrs = info.attributes
                            val discovered =
                                DiscoveredSyncDevice(
                                    nodeId = attrs["id"]?.toString(Charsets.UTF_8).orEmpty(),
                                    deviceId =
                                        attrs["device_id"]?.toString(Charsets.UTF_8).orEmpty(),
                                    name =
                                        attrs["name"]
                                            ?.toString(Charsets.UTF_8)
                                            ?.takeIf { it.isNotBlank() }
                                            ?: instance,
                                    host = info.host?.hostAddress.orEmpty(),
                                    port = info.port,
                                    appVersion = attrs["version"]?.toString(Charsets.UTF_8),
                                    deviceFlavor = attrs["deviceFlavor"]?.toString(Charsets.UTF_8),
                                )
                            byName[instance] = discovered
                            emit()
                        }

                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            Log.w(TAG, "resolve failed for $instance: $errorCode")
                        }
                    }
                // Cache and start. `resolveService` may reject if
                // another resolve is in flight; the catch keeps us
                // alive on a busy network.
                try {
                    pendingResolve = pending
                    nsdManager?.resolveService(service, pending)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "resolve already in flight, skipping $instance")
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val instance = service.serviceName.substringBefore(".")
                if (byName.remove(instance) != null) emit()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "start discovery failed for $serviceType: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "stop discovery failed for $serviceType: $errorCode")
            }
        }

    fun start() {
        val mgr = nsdManager ?: return
        // Idempotent — Android's `discoverServices` rejects
        // duplicate registrations with `IllegalArgumentException`.
        try {
            mgr.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: IllegalArgumentException) {
            Log.i(TAG, "discovery already running")
        }
    }

    fun stop() {
        val mgr = nsdManager ?: return
        try {
            mgr.stopServiceDiscovery(discoveryListener)
        } catch (e: IllegalArgumentException) {
            // Not currently running; safe to ignore.
        }
        byName.clear()
        emit()
    }

    private fun emit() {
        _devices.value = byName.values.toList()
    }

    companion object {
        private const val TAG = "DiscoveryService"

        // Match `livmet_mdns::SERVICE_TYPE` (`_livtet-sync._tcp.local.`)
        // with the trailing `.local.` suffix NSD expects.
        const val SERVICE_TYPE = "_livtet-sync._tcp."
    }
}
