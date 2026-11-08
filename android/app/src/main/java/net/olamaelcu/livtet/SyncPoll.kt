package net.olamaelcu.livtet

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit
import net.olamaelcu.livtet.ffi.SyncOfferSummary
import timber.log.Timber

/**
 * Polls the paired devices while the reader is not looking at the offers screen.
 *
 * A book the desktop offers is only a row in the desktop's change log until this device pulls it,
 * and until now that pull happened only when the Offers screen opened — so an offer could sit
 * unseen for days. This worker pulls on a schedule, keeps the ids of what it has already announced,
 * and posts one notification for offers it has not seen before.
 *
 * Pulling is what also brings the ordinary library changes, so this is the same sync a reader would
 * trigger by hand; it is deliberately not an auto-accept: the offer still waits for a decision on
 * the Offers screen.
 */
class SyncPollWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // A worker can start before any activity: the store has to be opened here.
        if (!Bridge.isInitialized()) {
            Bridge.init(File(applicationContext.filesDir, "livtet.db").absolutePath)
        }
        val offers =
            runCatching { Bridge.refreshOffers() }
                .onFailure { Timber.w(it, "background sync failed") }
                .getOrNull() ?: return Result.retry()

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val announced = prefs.getStringSet(KEY_ANNOUNCED, emptySet()).orEmpty()
        val fresh = offers.filter { it.offerId !in announced }
        prefs
            .edit()
            .putStringSet(KEY_ANNOUNCED, offers.map { it.offerId }.toSet())
            .putLong(KEY_POLLED_AT, System.currentTimeMillis())
            .apply()

        Timber.d("background sync: %d waiting, %d new", offers.size, fresh.size)
        if (fresh.isNotEmpty()) {
            SyncNotifications.post(applicationContext, fresh)
        }
        return Result.success()
    }

    companion object {
        const val PREFS = "sync-poll"
        const val KEY_ANNOUNCED = "announced-offers"
        const val KEY_POLLED_AT = "last-polled-at"
    }
}

/** Schedules the poll and its channels; called from the Application so it survives restarts. */
internal object SyncPoll {
    private const val PERIODIC = "sync-poll"
    private const val IMMEDIATE = "sync-poll-now"

    fun schedule(context: Context) {
        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val manager = WorkManager.getInstance(context)
        manager.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncPollWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build(),
        )
        // Periodic work's first run is an interval away; a launch should not wait that long.
        manager.enqueueUniqueWork(
            IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncPollWorker>().setConstraints(constraints).build(),
        )
    }
}

/** The one notification this app posts: books a paired device is waiting for an answer on. */
internal object SyncNotifications {
    private const val CHANNEL = "incoming-books"
    private const val ID = 1

    fun post(context: Context, offers: List<SyncOfferSummary>) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            // The reader declined; the offers screen still lists them.
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Incoming books",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
        val title =
            if (offers.size == 1) "A book is waiting" else "${offers.size} books are waiting"
        val text =
            if (offers.size == 1) {
                offers.first().title
            } else {
                "${offers.first().title} and ${offers.size - 1} more"
            }
        val open =
            PendingIntent.getActivity(
                context,
                0,
                OffersActivity.intent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        manager.notify(
            ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}
