package net.olamaelcu.livtet

import android.app.Application
import io.sentry.android.core.SentryAndroid
import net.olamaelcu.livtet.ffi.setSystemSecrets
import timber.log.Timber

class LivtetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        val systemSecrets =
            mutableMapOf<String, String>().apply {
                put("google_books_api_key", BuildConfig.GOOGLE_API_KEY)
            }
        setSystemSecrets(systemSecrets)

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
