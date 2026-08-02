package net.olamaelcu.livtet.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Theme preference persistence for the Android app. UI-only — the theme choice is never sent to the
 * Rust core and never crosses the FFI boundary, so we keep it in `DataStore` rather than the SQLite
 * DB that the FFI sees.
 *
 * Values are exactly one of the strings `"system"`, `"light"`, `"dark"`. Any other value (including
 * an un-set key) maps to `Mode.SYSTEM`. The mapping is case-sensitive in the data store but
 * case-insensitive on read, so a future iOS bridge that writes `"System"` would still resolve to
 * `SYSTEM`.
 */
object ThemeManager {
    enum class Mode {
        SYSTEM,
        LIGHT,
        DARK;

        companion object {
            /** Lenient parse — never throws, never panics. */
            fun parse(raw: String?): Mode =
                when (raw?.lowercase()) {
                    "light" -> LIGHT
                    "dark" -> DARK
                    else -> SYSTEM
                }
        }

        fun asToken(): String =
            when (this) {
                SYSTEM -> "system"
                LIGHT -> "light"
                DARK -> "dark"
            }
    }

    private val NAME = "settings"
    private val KEY = stringPreferencesKey("theme_mode")
    private val Context.store by preferencesDataStore(NAME)

    /**
     * Stream of the user's current theme preference. Emits `SYSTEM` on first launch before the user
     * has set anything.
     */
    fun mode(context: Context): Flow<Mode> =
        context.store.data.map { prefs -> Mode.parse(prefs[KEY]) }

    /**
     * Persist a new theme preference. Safe to call from the main thread — DataStore routes writes
     * to its internal IO dispatcher.
     */
    suspend fun setMode(context: Context, mode: Mode) {
        context.store.edit { it[KEY] = mode.asToken() }
    }
}
