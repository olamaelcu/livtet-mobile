package net.olamaelcu.livtet.core.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.olamaelcu.livtet.core.auth.provider.AuthProvider

class AccountStateTest {

    @Test
    fun `empty state has no providers`() {
        val state = AccountState(emptyMap())
        assertFalse(state.isAnySignedIn)
        assertEquals(0, state.providers.size)
    }

    @Test
    fun `state with one provider is signed in`() {
        val account = ProviderAccount(
            provider = AuthProvider.Google,
            displayName = "Test User",
            email = "test@example.com",
            avatarUrl = null,
            signedInAt = 0L,
        )
        val state = AccountState(mapOf(AuthProvider.Google to account))
        assertTrue(state.isAnySignedIn)
    }

    @Test
    fun `sign out removes provider from state`() {
        val account = ProviderAccount(
            provider = AuthProvider.Google,
            displayName = "Test User",
            email = "test@example.com",
            avatarUrl = null,
            signedInAt = 0L,
        )
        val state = AccountState(mapOf(AuthProvider.Google to account))
        val afterSignOut = state.copy(providers = state.providers - AuthProvider.Google)
        assertEquals(0, afterSignOut.providers.size)
        assertFalse(afterSignOut.isAnySignedIn)
    }

    @Test
    fun `multiple providers can coexist`() {
        val google = ProviderAccount(
            provider = AuthProvider.Google,
            displayName = "A",
            email = "a@x.com",
            avatarUrl = null,
            signedInAt = 0L,
        )
        val atproto = ProviderAccount(
            provider = AuthProvider.Atproto(did = "did:plc:abc", handle = "test.bsky.social"),
            displayName = "B",
            email = null,
            avatarUrl = null,
            signedInAt = 0L,
        )
        val state = AccountState(mapOf(AuthProvider.Google to google, AuthProvider.Atproto("did:plc:abc", "test.bsky.social") to atproto))
        assertTrue(state.isAnySignedIn)
        assertEquals(2, state.providers.size)
    }
}
