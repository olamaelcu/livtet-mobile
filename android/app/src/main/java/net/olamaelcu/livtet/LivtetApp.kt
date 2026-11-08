package net.olamaelcu.livtet

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
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

        // The catalog answers for the books a feed post names; the cache makes a reopened post
        // paint from what this phone already knows, before the network is asked again.
        CatalogClient.install(this)

        // Paired devices are polled on a schedule, not only when the offers screen opens.
        SyncPoll.schedule(this)

        // A peer pulls from this phone, so the endpoint has to answer while the app is open —
        // and only then. An unpaired install has nobody to serve and binds nothing.
        SyncAdvertisement.install(this)

        // The feed's covers are URLs, so the shared loader needs a network fetcher. Installed
        // once here rather than per screen: a screen-built loader would re-create the disk cache.
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory()) }
                .crossfade(true)
                .build()
        }
    }

    companion object {
        @Volatile private var instance: LivtetApp? = null

        fun getInstance(): LivtetApp =
            instance ?: throw IllegalStateException("LivtetApp not initialized")
    }
}
