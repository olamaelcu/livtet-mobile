package net.olamaelcu.livtet

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import net.olamaelcu.livtet.sync.SyncPull
import timber.log.Timber

@HiltAndroidApp
class LivtetApp : Application() {
    val koreaderPresence: KoreaderPresence by lazy { KoreaderPresence(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (BuildConfig.SENTRY_DSN.isNotEmpty()) {
            SentryAndroid.init(this) { options -> options.dsn = BuildConfig.SENTRY_DSN }
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Watch for KOReader install/uninstall so the "Open in KOReader"
        // CTA updates live without needing a full app restart.
        koreaderPresence.register()
        // Ask paired peers for changes on an interval. Receiving a book must not
        // depend on the reader having the Settings screen open.
        SyncPull.startPolling(this)
    }

    companion object {
        @Volatile private var instance: LivtetApp? = null

        fun getInstance(): LivtetApp = instance ?: throw IllegalStateException("LivtetApp not initialized")
    }
}
