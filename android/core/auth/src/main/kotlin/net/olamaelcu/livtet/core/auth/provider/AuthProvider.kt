package net.olamaelcu.livtet.core.auth.provider

import kotlinx.serialization.Serializable

@Serializable
sealed interface AuthProvider {
    @Serializable data object Google : AuthProvider

    @Serializable data object Apple : AuthProvider

    @Serializable data class Atproto(val did: String, val handle: String) : AuthProvider
}
