package net.olamaelcu.livtet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactive KOReader install detector.
 *
 * Covers both F-Droid (`org.koreader.launcher.fdroid`) and the direct-APK
 * GitHub build (`org.koreader.launcher`). Upstream does not ship a Google
 * Play distribution, so no Play id is included.
 *
 * `isInstalled` is a [StateFlow] so Compose UI can collect it and re-render
 * "Open in KOReader" affordances live when the user installs or removes
 * KOReader while the app is foregrounded.
 *
 * Lifecycle: call [register] from `Application.onCreate` (or any
 * lifecycle-owning scope), [unregister] when the owning scope ends.
 * `register`/`unregister` are idempotent — duplicate calls are no-ops.
 */
class KoreaderPresence(private val context: Context) {
    private val pm = context.packageManager

    private val _isInstalled = MutableStateFlow(checkNow())
    val isInstalled: StateFlow<Boolean> = _isInstalled.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val pkg = intent?.data?.schemeSpecificPart ?: return
            if (pkg in KO_READER_PACKAGES) {
                val now = checkNow()
                Log.i(TAG, "$pkg package event → isInstalled=$now")
                _isInstalled.value = now
            }
        }
    }

    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        registered = true
        // Refresh once after registering in case something changed while
        // we weren't listening.
        _isInstalled.value = checkNow()
    }

    fun unregister() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    private fun checkNow(): Boolean =
        KO_READER_PACKAGES.any { p ->
            try {
                pm.getPackageInfo(p, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

    companion object {
        private const val TAG = "KoreaderPresence"

        /**
         * All known KOReader Android package ids, ordered from most to
         * least common. The first installed id wins when launching.
         */
        val KO_READER_PACKAGES = listOf(
            "org.koreader.launcher.fdroid",
            "org.koreader.launcher",
        )
    }
}
