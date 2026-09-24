package net.olamaelcu.livtet.core.auth.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.kikin81.atproto.oauth.OAuthSession
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

class AndroidOAuthSessionStore(context: Context) : OAuthSessionStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs: SharedPreferences = run {
        val masterKey =
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "livtet_atproto_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun load(): OAuthSession? {
        val raw = prefs.getString(KEY, null) ?: return null
        return try {
            json.decodeFromString<OAuthSession>(raw)
        } catch (e: Exception) {
            Timber.e(e, "Failed to deserialize ATProto session")
            null
        }
    }

    override suspend fun save(session: OAuthSession) {
        prefs.edit().putString(KEY, json.encodeToString(session)).apply()
    }

    override suspend fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "atproto_oauth_session"
    }
}
