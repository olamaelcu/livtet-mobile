package net.olamaelcu.livtet

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import timber.log.Timber

@HiltAndroidApp
class LivtetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        if (BuildConfig.SENTRY_DSN.isNotEmpty()) {
            SentryAndroid.init(this) { options -> options.dsn = BuildConfig.SENTRY_DSN }
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    companion object {
        @Volatile private var instance: LivtetApp? = null

        fun getInstance(): LivtetApp =
            instance ?: throw IllegalStateException("LivtetApp not initialized")
    }
}
