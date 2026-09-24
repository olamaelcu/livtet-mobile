package net.olamaelcu.livtet.core.auth.model

import kotlinx.serialization.Serializable

@Serializable
data class AtprotoProfile(
    val did: String,
    val handle: String,
    val displayName: String,
    val description: String,
    val avatarCid: String? = null,
)
