package net.olamaelcu.livtet.core.auth

import net.olamaelcu.livtet.core.auth.model.AtprotoProfile
import net.olamaelcu.livtet.core.auth.provider.AuthProvider

data class ProviderAccount(
    val provider: AuthProvider,
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
    val signedInAt: Long,
    val atprotoProfile: AtprotoProfile? = null,
)

data class AccountState(val providers: Map<AuthProvider, ProviderAccount>) {
    val isAnySignedIn: Boolean
        get() = providers.isNotEmpty()
}
