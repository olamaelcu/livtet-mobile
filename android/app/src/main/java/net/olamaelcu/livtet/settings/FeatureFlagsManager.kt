package net.olamaelcu.livtet.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import net.olamaelcu.livtet.BuildConfig

/**
 * Persistence + effective-value resolution for [LabsFlag].
 *
 * Two inputs determine whether a flag is enabled at runtime:
 *
 * 1. **Build-time default** — emitted as
 *    `BuildConfig.LABS_BUILD_TIME_DEFAULTS_JSON` by
 *    `app/build.gradle.kts`. Lets a product flavor (fdroid,
 *    generic, canary playstore) force a flag off regardless of
 *    user preference. Missing JSON entries default to `true`
 *    (unlocked) so a stale build never silently enables a
 *    brand-new flag.
 *
 * 2. **Persisted override** — stored in the `feature_flags`
 *    DataStore (separate file from `settings` so Labs writes
 *    don't churn the theme read pipeline). `null` means the user
 *    has not touched the toggle; we substitute the enum's
 *    `defaultEnabled`.
 *
 * Effective value:
 * `buildTimeDefault(flag) && persistedOverride(flag, flag.defaultEnabled)`.
 *
 * If the build-time gate is off, the persisted override is
 * effectively dead weight — the UI hides the toggle so the user
 * can't even set it. We still persist the override so
 * re-enabling the gate later (e.g., promoting canary →
 * playstore) respects the prior intent.
 */
object FeatureFlagsManager {
    private const val NAME = "feature_flags"

    private val Context.store by preferencesDataStore(NAME)

    /**
     * One-shot lookup of the build-time default for a single
     * flag. Reads the JSON constant emitted by Gradle.
     *
     * Returns `true` (unlocked) if the JSON is malformed, the
     * flag is missing, or the Gradle task didn't run — safer to
     * over-enable than to silently lock a flag that the codebase
     * thinks should be available.
     */
    fun buildTimeDefault(flag: LabsFlag): Boolean {
        val raw = BuildConfig.LABS_BUILD_TIME_DEFAULTS_JSON
        return try {
            val obj = Json.parseToJsonElement(raw) as? JsonObject ?: return true
            obj[flag.key]?.jsonPrimitive?.boolean ?: true
        } catch (_: Throwable) {
            true
        }
    }

    /**
     * Map of every flag's current effective value (after both
     * gates). Emits whenever the DataStore changes — use this
     * for reactive UI.
     */
    fun flow(context: Context): Flow<Map<String, Boolean>> =
        context.store.data.map { prefs ->
            LabsFlag.ALL.associate { flag ->
                flag.key to resolve(flag, prefs[keyFor(flag)])
            }
        }

    /**
     * Per-flag reactive lookup. Returns the same effective value
     * as [flow] for the requested flag.
     */
    fun isEnabled(
        context: Context,
        flag: LabsFlag,
    ): Flow<Boolean> =
        context.store.data.map { prefs ->
            resolve(flag, prefs[keyFor(flag)])
        }

    /** Suspending snapshot read for non-Compose callers. */
    suspend fun isEnabledNow(
        context: Context,
        flag: LabsFlag,
    ): Boolean = resolve(flag, context.store.data.first()[keyFor(flag)])

    /**
     * Persist a user override. Ignored silently when the
     * build-time gate is off — the UI shouldn't even call this
     * in that case, but we don't crash on a race.
     */
    suspend fun setEnabled(
        context: Context,
        flag: LabsFlag,
        enabled: Boolean,
    ) {
        if (!buildTimeDefault(flag)) return
        context.store.edit { it[keyFor(flag)] = enabled }
    }

    /**
     * Wipe every Labs override. Each flag falls back to its
     * `defaultEnabled`, then is AND'd with the build-time gate.
     * We remove every key whose name starts with the `flag:`
     * namespace prefix — that's the only prefix this object
     * writes — so we never accidentally clear a future unrelated
     * DataStore entry.
     */
    suspend fun reset(context: Context) {
        context.store.edit { prefs ->
            prefs
                .asMap()
                .keys
                .filter { it.name.startsWith(KEY_PREFIX) }
                .forEach { prefs.remove(it) }
        }
    }

    private const val KEY_PREFIX = "flag:"

    private fun keyFor(flag: LabsFlag) = booleanPreferencesKey("$KEY_PREFIX${flag.key}")

    private fun resolve(
        flag: LabsFlag,
        persisted: Boolean?,
    ): Boolean {
        val gate = buildTimeDefault(flag)
        if (!gate) return false
        return persisted ?: flag.defaultEnabled
    }
}
