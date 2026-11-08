package net.olamaelcu.livtet

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Pulls every paired device on a short interval while the app is visible.
 *
 * The background worker runs at WorkManager's 15-minute floor, which is too coarse for a reader
 * who moved a position on the desktop and expects the phone to notice. The visible app polls
 * every [INTERVAL_MS], so a change lands well inside a minute of both apps being open.
 *
 * Only a visible pass polls: the pull is a network round trip, and a phone in the background has
 * the periodic worker for that. A pass that finds nothing new cancels its notification; one that
 * brings changes briefly reports what arrived.
 */
internal object SyncPoller {
    /** Comfortably inside a minute even with a slow peer. */
    private const val INTERVAL_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicInteger(0)

    @Volatile private var foreground = false
    private var job: Job? = null
    private lateinit var application: Application

    fun install(application: Application) {
        this.application = application
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: android.app.Activity) {
                    if (started.incrementAndGet() == 1) resume()
                }

                override fun onActivityStopped(activity: android.app.Activity) {
                    if (started.decrementAndGet() == 0 && !activity.isChangingConfigurations)
                        pause()
                }

                override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) = Unit

                override fun onActivityResumed(activity: android.app.Activity) = Unit

                override fun onActivityPaused(activity: android.app.Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: android.app.Activity,
                    state: android.os.Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: android.app.Activity) = Unit
            }
        )
    }

    private fun resume() {
        foreground = true
        if (job?.isActive == true) return
        job =
            scope.launch {
                while (isActive && foreground) {
                    runCatching { pollOnce() }.onFailure { Timber.w(it, "visible sync failed") }
                    delay(INTERVAL_MS)
                }
            }
    }

    private fun pause() {
        foreground = false
        job?.cancel()
        job = null
        if (::application.isInitialized) SyncNotifications.cancelProgress(application)
    }

    private suspend fun pollOnce() {
        if (!Bridge.isInitialized()) {
            Bridge.init(File(application.filesDir, "livtet.db").absolutePath)
        }
        val peers = Bridge.pairedDevices().filter { it.nodeId != null }
        if (peers.isEmpty()) return

        SyncNotifications.progressStart(application, peers.size)
        var pulled = 0
        var applied = 0
        var conflicts = 0
        var offers = 0
        Bridge.pullAllPeers { report ->
            pulled += 1
            applied += report.applied.toInt()
            conflicts += report.conflicts.toInt()
            offers += report.offers.size
            SyncNotifications.progressStep(application, pulled, peers.size)
        }
        SyncNotifications.progressDone(application, applied, conflicts, offers)
        Timber.d(
            "visible sync: %d/%d peers, %d applied, %d conflicts, %d offers",
            pulled,
            peers.size,
            applied,
            conflicts,
            offers,
        )
        // Let the result line breathe before it disappears; the next pass starts after the
        // interval, not after this pause.
        if (applied + conflicts + offers > 0) delay(2_500)
    }
}
