package net.olamaelcu.livtet

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.olamaelcu.livtet.ffi.SyncPairedDevice
import timber.log.Timber

/**
 * Keeps this device pullable while the app is in the foreground.
 *
 * Sync is pull-only: a peer brings this phone's books and reading positions over by asking for
 * them, so a phone that is not answering has nothing to give. Pairing is what makes answering worth
 * the endpoint — an unpaired install has nobody to serve — so [advertise] asks for a paired device
 * first and binds nothing when there is none.
 *
 * The endpoint follows the visible app rather than one screen: stopped when the last activity goes
 * away, started again when the app comes back. [SyncActivity] used to own the advertisement for the
 * length of its screen; the app owns it now, and the screen's call is just the idempotent one on
 * the same secret key path.
 */
internal object SyncAdvertisement {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Serializes start and stop, so a stop cannot land between a check and the advertisement. */
    private val gate = Mutex()

    /** How many activities are started; only 1 → 0 is the app leaving the foreground. */
    private val started = AtomicInteger(0)

    @Volatile private var foreground = false

    /** Follow the app's visible lifetime. Call once, from [LivtetApp]. */
    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    // Counted, not a boolean: the next activity starts before this one stops, so
                    // only the app's own first start is a transition into the foreground.
                    if (started.incrementAndGet() == 1) resume()
                }

                override fun onActivityStopped(activity: Activity) {
                    // A rotation stops the old activity before the new one is started; the
                    // advertisement would be torn down and bound again for a new window layout.
                    if (started.decrementAndGet() == 0 && !activity.isChangingConfigurations)
                        pause()
                }

                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }

    private fun resume() {
        foreground = true
        scope.launch { gate.withLock { if (foreground) advertise() } }
    }

    private fun pause() {
        foreground = false
        scope.launch {
            gate.withLock {
                // A start that won the race is the current state; do not tear it down.
                if (foreground) return@withLock
                Bridge.stopAdvertising()
                Timber.d("sync: stopped advertising; the app is in the background")
            }
        }
    }

    /**
     * Announce this device when it has a peer, and answer there.
     *
     * Returns the node id peers dial, or null when there is nobody to serve. The store is opened
     * here because an app start has no screen that has done it yet, and pairing rows are read from
     * it.
     */
    suspend fun advertise(
        paired: suspend () -> List<SyncPairedDevice> = { Bridge.pairedDevices() }
    ): String? {
        if (!Bridge.isInitialized()) {
            Bridge.init(File(LivtetApp.getInstance().filesDir, "livtet.db").absolutePath)
        }
        if (paired().isEmpty()) {
            Timber.d("sync: no paired device, so this device is not advertised")
            return null
        }
        val nodeId = Bridge.advertiseForSync(Bridge.deviceId(), Bridge.deviceName())
        Timber.i("sync: advertising while the app is open as %s", nodeId.take(8))
        return nodeId
    }
}
