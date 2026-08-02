package net.olamaelcu.livtet.core.auth

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.olamaelcu.livtet.core.auth.provider.AppleAuthProvider
import net.olamaelcu.livtet.core.auth.provider.AuthProvider
import net.olamaelcu.livtet.core.auth.provider.GoogleAuthProvider
import net.olamaelcu.livtet.core.auth.storage.SecureTokenStore
import timber.log.Timber

object AccountManager {
    private const val TAG = "AccountManager"

    private var tokenStore: SecureTokenStore? = null
    private val _accountState = MutableStateFlow(AccountState(emptyMap()))
    val accountState: Flow<AccountState> = _accountState.asStateFlow()

    fun init(context: Context) {
        if (tokenStore != null) return
        tokenStore = SecureTokenStore(context.applicationContext)
        Timber.tag(TAG).d("AccountManager initialized")
    }

    fun isInitialized(): Boolean = tokenStore != null

    suspend fun signIn(context: Context, provider: AuthProvider): ProviderAccount {
        val store = tokenStore ?: throw IllegalStateException("AccountManager not initialized")
        Timber.tag(TAG).d("signIn called for provider: $provider")
        val account =
            when (provider) {
                AuthProvider.Google -> GoogleAuthProvider.signIn(context)
                AuthProvider.Apple -> AppleAuthProvider.signIn(context)
                is AuthProvider.Atproto ->
                    throw UnsupportedOperationException(
                        "ATProto sign-in requires beginOAuth + handleCallback flow"
                    )
            }
        store.putToken("token:${providerKey(provider)}:signed_in_at", account.signedInAt.toString())
        if (account.email != null)
            store.putToken("token:${providerKey(provider)}:email", account.email)
        store.putToken("token:${providerKey(provider)}:display_name", account.displayName)
        val newProviders = _accountState.value.providers + (provider to account)
        _accountState.value = AccountState(newProviders)
        Timber.tag(TAG).d("signIn complete: provider=${providerKey(provider)}")
        return account
    }

    suspend fun signOut(context: Context, provider: AuthProvider) {
        val store = tokenStore ?: return
        Timber.tag(TAG).d("signOut: provider=${providerKey(provider)}")
        store.clearProvider("token:${providerKey(provider)}")
        val newProviders = _accountState.value.providers - provider
        _accountState.value = AccountState(newProviders)
        Timber.tag(TAG).d("signOut complete: provider=${providerKey(provider)}")
    }

    private fun providerKey(provider: AuthProvider): String =
        when (provider) {
            AuthProvider.Google -> "google"
            AuthProvider.Apple -> "apple"
            is AuthProvider.Atproto -> "atproto"
        }
}
