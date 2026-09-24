package net.olamaelcu.livtet.core.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.core.auth.model.AtprotoProfile
import net.olamaelcu.livtet.core.auth.provider.AppleAuthProvider
import net.olamaelcu.livtet.core.auth.provider.AtprotoAuthService
import net.olamaelcu.livtet.core.auth.provider.AuthProvider
import net.olamaelcu.livtet.core.auth.provider.GoogleAuthProvider
import net.olamaelcu.livtet.core.auth.storage.SecureTokenStore
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val atprotoAuthService: AtprotoAuthService,
    private val tokenStore: SecureTokenStore,
) {
    private val _accountState = MutableStateFlow(AccountState(emptyMap()))
    val accountState: Flow<AccountState> = _accountState.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        Timber.tag(TAG).d("AccountManager created via Hilt")
        scope.launch { restoreSession() }
    }

    private suspend fun restoreSession() {
        val restored = mutableMapOf<AuthProvider, ProviderAccount>()

        // Restore ATProto session from encrypted store
        if (atprotoAuthService.hasSession()) {
            val did = atprotoAuthService.getDid() ?: ""
            val handle = atprotoAuthService.getHandle() ?: ""
            if (did.isNotEmpty()) {
                val profile = try {
                    atprotoAuthService.fetchProfile(did)
                } catch (_: Exception) { null }
                val atprotoProvider = AuthProvider.Atproto(did = did, handle = handle)
                restored[atprotoProvider] = ProviderAccount(
                    provider = atprotoProvider,
                    displayName = profile?.displayName?.ifEmpty { handle } ?: handle,
                    email = null,
                    avatarUrl = profile?.avatarCid?.let {
                        "https://biblio.livtet.olamaelcu.net/xrpc/com.atproto.sync.getBlob?did=$did&cid=$it"
                    },
                    signedInAt = System.currentTimeMillis(),
                    atprotoProfile = profile,
                )
            }
        }

        // Restore Google/Apple sessions from SecureTokenStore
        for (provider in listOf("google", "apple")) {
            val signedInAt = tokenStore.getToken("token:$provider:signed_in_at")?.toLongOrNull()
            if (signedInAt != null) {
                val email = tokenStore.getToken("token:$provider:email")
                val displayName = tokenStore.getToken("token:$provider:display_name") ?: provider
                val authProvider =
                    when (provider) {
                        "google" -> AuthProvider.Google
                        "apple" -> AuthProvider.Apple
                        else -> continue
                    }
                restored[authProvider] =
                    ProviderAccount(
                        provider = authProvider,
                        displayName = displayName,
                        email = email,
                        avatarUrl = null,
                        signedInAt = signedInAt,
                    )
            }
        }
        if (restored.isNotEmpty()) {
            _accountState.value = AccountState(restored)
        }
    }

    suspend fun signIn(provider: AuthProvider): String? {
        Timber.tag(TAG).d("signIn called for provider: $provider")
        return when (provider) {
            AuthProvider.Google -> {
                val account = GoogleAuthProvider.signIn(context)
                persistGoogleAccount(account)
                null
            }
            AuthProvider.Apple -> {
                val account = AppleAuthProvider.signIn(context)
                null
            }
            is AuthProvider.Atproto -> {
                // Begin OAuth flow — returns auth URL for Custom Tab
                atprotoAuthService.beginLogin(provider.handle)
            }
        }
    }

    private fun persistGoogleAccount(account: ProviderAccount) {
        val store = tokenStore
        store.putToken("token:google:signed_in_at", account.signedInAt.toString())
        if (account.email != null) store.putToken("token:google:email", account.email)
        store.putToken("token:google:display_name", account.displayName)
        val newProviders = _accountState.value.providers + (AuthProvider.Google to account)
        _accountState.value = AccountState(newProviders)
    }

    suspend fun completeAtprotoLogin(redirectUri: String) {
        Timber.tag(TAG).d("completeAtprotoLogin")
        atprotoAuthService.completeLogin(redirectUri)

        val did = atprotoAuthService.getDid() ?: ""
        val handle = atprotoAuthService.getHandle() ?: ""

        val profile = if (did.isNotEmpty()) {
            atprotoAuthService.fetchProfile(did)
        } else {
            null
        }

        val account = ProviderAccount(
            provider = AuthProvider.Atproto(did = did, handle = handle),
            displayName = profile?.displayName?.ifEmpty { handle } ?: handle,
            email = null,
            avatarUrl = profile?.avatarCid?.let { "https://biblio.livtet.olamaelcu.net/xrpc/com.atproto.sync.getBlob?did=$did&cid=$it" },
            signedInAt = System.currentTimeMillis(),
            atprotoProfile = profile,
        )
        val newProviders = _accountState.value.providers + (account.provider to account)
        _accountState.value = AccountState(newProviders)
        Timber.tag(TAG).d("completeAtprotoLogin: success did=$did handle=$handle")
    }

    suspend fun fetchAtprotoProfile(did: String) {
        Timber.tag(TAG).d("fetchAtprotoProfile: did=$did")
        try {
            val profile = atprotoAuthService.fetchProfile(did)
            updateAtprotoProfile(profile)
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch ATProto profile")
        }
    }

    private fun updateAtprotoProfile(profile: AtprotoProfile) {
        val currentAtproto =
            _accountState.value.providers.keys.filterIsInstance<AuthProvider.Atproto>().firstOrNull()
                ?: return
        val existing = _accountState.value.providers[currentAtproto] ?: return
        val updated = existing.copy(atprotoProfile = profile)
        val newProviders = _accountState.value.providers + (currentAtproto to updated)
        _accountState.value = AccountState(newProviders)
    }

    suspend fun signOut(provider: AuthProvider) {
        Timber.tag(TAG).d("signOut: provider=${providerKey(provider)}")
        when (provider) {
            is AuthProvider.Atproto -> atprotoAuthService.signOut()
            else -> tokenStore.clearProvider("token:${providerKey(provider)}")
        }
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

    companion object {
        private const val TAG = "AccountManager"
    }
}
