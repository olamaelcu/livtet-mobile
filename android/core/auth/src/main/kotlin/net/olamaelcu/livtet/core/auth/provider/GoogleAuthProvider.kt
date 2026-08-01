package net.olamaelcu.livtet.core.auth.provider

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import net.olamaelcu.livtet.core.auth.ProviderAccount
import timber.log.Timber

object GoogleAuthProvider {
    private const val TAG = "Auth/Google"

    private val SERVER_CLIENT_ID: String? = null

    private val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
        .apply { SERVER_CLIENT_ID?.let { setServerClientId(it) } }
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(false)
        .build()

    private val request: GetCredentialRequest = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    suspend fun signIn(context: Context): ProviderAccount {
        Timber.tag(TAG).d("starting Google sign-in")
        val credentialManager = CredentialManager.create(context)
        val result: GetCredentialResponse = try {
            credentialManager.getCredential(context, request)
        } catch (e: GetCredentialException) {
            Timber.tag(TAG).e(e, "credential retrieval failed")
            throw GoogleAuthException("Could not sign in with Google", e)
        }
        val credential = result.credential
        val googleId = GoogleIdTokenCredential.createFrom(credential.data)
        val account = ProviderAccount(
            provider = AuthProvider.Google,
            displayName = googleId.displayName ?: googleId.id,
            email = googleId.id,
            avatarUrl = googleId.profilePictureUri?.toString(),
            signedInAt = System.currentTimeMillis(),
        )
        Timber.tag(TAG).d("sign-in succeeded: displayName=${account.displayName}")
        return account
    }

    suspend fun trySilentSignIn(context: Context): ProviderAccount? {
        Timber.tag(TAG).d("attempting silent sign-in")
        val silentOption = GetGoogleIdOption.Builder()
            .apply { SERVER_CLIENT_ID?.let { setServerClientId(it) } }
            .setFilterByAuthorizedAccounts(true)
            .setAutoSelectEnabled(true)
            .build()
        val silentRequest = GetCredentialRequest.Builder()
            .addCredentialOption(silentOption)
            .build()
        return try {
            val credentialManager = CredentialManager.create(context)
            val result = credentialManager.getCredential(context, silentRequest)
            val googleId = GoogleIdTokenCredential.createFrom(result.credential.data)
            ProviderAccount(
                provider = AuthProvider.Google,
                displayName = googleId.displayName ?: googleId.id,
                email = googleId.id,
                avatarUrl = googleId.profilePictureUri?.toString(),
                signedInAt = System.currentTimeMillis(),
            )
        } catch (e: GetCredentialException) {
            Timber.tag(TAG).d("silent sign-in: no saved credentials")
            null
        }
    }

    class GoogleAuthException(message: String, cause: Throwable) : Exception(message, cause)
}
