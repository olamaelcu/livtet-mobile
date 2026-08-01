package net.olamaelcu.livtet.core.auth.storage

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SecureTokenStoreTest {

    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)
    private val context: Context = mockk {
        every { getSharedPreferences("livtet_auth_tokens", Context.MODE_PRIVATE) } returns prefs
    }
    private val store = SecureTokenStore(context, testMode = true)

    @Test
    fun `store and retrieve token`() {
        every { prefs.getString("token:google:id_token", null) } returns "fake-id-token"
        store.putToken("token:google:id_token", "fake-id-token")
        val result = store.getToken("token:google:id_token")
        assertEquals("fake-id-token", result)
    }

    @Test
    fun `retrieve missing token returns null`() {
        every { prefs.getString("token:nonexistent", null) } returns null
        val result = store.getToken("token:nonexistent")
        assertNull(result)
    }

    @Test
    fun `clearProvider removes all tokens for a provider`() {
        every { prefs.edit() } returns editor
        every { editor.remove(any()) } returns editor
        every { prefs.all } returns mapOf("token:google:id_token" to "x", "token:google:refresh" to "y")
        store.clearProvider("token:google")
        verify { prefs.edit() }
    }

    @Test
    fun `clearAll removes all tokens`() {
        every { prefs.edit() } returns editor
        every { editor.clear() } returns editor
        store.clearAll()
        verify { prefs.edit() }
    }
}
