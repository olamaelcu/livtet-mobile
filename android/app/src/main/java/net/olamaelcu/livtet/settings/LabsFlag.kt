package net.olamaelcu.livtet.settings

import net.olamaelcu.livtet.R

/**
 * The set of Labs / experimental feature flags exposed in Settings → Labs.
 *
 * Each flag has three components:
 *
 * - [key]: a stable string token persisted in DataStore and referenced from
 *   `BuildConfig.LABS_BUILD_TIME_DEFAULTS_JSON` in `app/build.gradle.kts`. **Do not rename a key**
 *   — it invalidates user overrides and breaks Gradle/Kotlin drift checks. To retire a flag, leave
 *   the entry here (so old overrides are still resolved) and force its build-time default to
 *   `false`.
 * - [defaultEnabled]: what a fresh install sees before the user touches the toggle and before any
 *   build-time gate is applied. The runtime effective value is `buildTimeDefault(flag) &&
 *   persistedOverride(flag, default = defaultEnabled)`, so a `false` default here means the runtime
 *   starts off unless the Gradle gate unlocks it.
 * - [titleRes] / [descriptionRes]: string-resource ids for the Settings UI. We resolve at compose
 *   time, so this stays UI-only and keeps the enum free of `Context`.
 *
 * Adding a flag requires exactly **one** edit here AND **one** edit in `app/build.gradle.kts`
 * `labsFlagBuildDefaults` — the Gradle map emits `BuildConfig.LABS_BUILD_TIME_DEFAULTS_JSON`, the
 * Kotlin enum reads it back. If the two lists disagree, the resolver falls back to "unlocked"
 * (`true`) so a missing Gradle entry never silently enables a brand-new flag.
 *
 * `LabsFlag.ALL` is the canonical ordered list rendered in the Labs section. Order is the order
 * entries are declared here — that's also the order they appear in Settings.
 */
enum class LabsFlag(
    val key: String,
    val defaultEnabled: Boolean,
    val titleRes: Int,
    val descriptionRes: Int,
) {
    SHOW_EXPERIMENTAL_DUPLICATES_BADGE(
        key = "show_experimental_duplicates_badge",
        defaultEnabled = false,
        titleRes = R.string.labs_flag_show_experimental_duplicates_badge_title,
        descriptionRes = R.string.labs_flag_show_experimental_duplicates_badge_description,
    ),
    INSTANT_LOCAL_FILE_IMPORT(
        key = "instant_local_file_import",
        defaultEnabled = false,
        titleRes = R.string.labs_flag_instant_local_file_import_title,
        descriptionRes = R.string.labs_flag_instant_local_file_import_description,
    ),
    ;

    companion object {
        /**
         * Lookup by persisted key. Returns `null` for unknown keys (e.g., a retired flag still
         * present in an old DataStore file). Callers should treat unknown keys as off.
         */
        fun fromKey(key: String): LabsFlag? = entries.firstOrNull { it.key == key }

        /** All flags in display order. */
        val ALL: List<LabsFlag> = entries.toList()
    }
}
