package net.olamaelcu.livtet.core.auth.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

class SecureTokenStore(context: Context, private val testMode: Boolean = false) {
    private val prefs: SharedPreferences = if (testMode) {
        context.getSharedPreferences("livtet_auth_tokens", Context.MODE_PRIVATE)
    } else {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "livtet_auth_tokens_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun putToken(key: String, value: String) {
        Timber.d("storing token: $key")
        prefs.edit().putString(key, value).apply()
    }

    fun getToken(key: String): String? {
        val value = prefs.getString(key, null)
        Timber.d("reading token: $key (present=${value != null})")
        return value
    }

    fun clearProvider(prefix: String) {
        Timber.d("clearing provider tokens: $prefix")
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun clearAll() {
        Timber.d("clearing all auth tokens")
        prefs.edit().clear().apply()
    }
}
