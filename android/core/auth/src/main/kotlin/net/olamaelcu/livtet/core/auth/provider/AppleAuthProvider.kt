package net.olamaelcu.livtet.core.auth.provider

import android.content.Context
import net.olamaelcu.livtet.core.auth.ProviderAccount
import timber.log.Timber

/**
 * Apple Sign-In provider for Android.
 *
 * Currently a stub — will be fully implemented once the team has an active Apple Developer account
 * with the bundle ID registered for SIWA.
 */
object AppleAuthProvider {
    private const val TAG = "Auth/Apple"

    suspend fun signIn(context: Context): ProviderAccount {
        Timber.tag(TAG).d("starting Apple sign-in")
        throw AppleAuthException(
            "Sign in with Apple is not yet configured for this app. " +
                "The Apple Developer account must register the bundle ID " +
                "for the Sign in with Apple service."
        )
    }

    class AppleAuthException(message: String) : Exception(message)
}
