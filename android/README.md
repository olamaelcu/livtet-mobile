# Android App

## End-User Capabilities

What someone with `livtet` installed on Android can actually do today.

### First launch

Splash screen (brand mark, "Loading your library…" spinner) initializes the
Rust core (`crates/ffi/livtet-ffi/`) via `Bridge.init` and hands off to the
main app. Source: `SplashActivity.kt`, `Bridge.kt:init`.

### Bottom navigation

Three tabs hosted by `DashboardActivity`'s Compose `NavHost`:

- **Dashboard** — at-a-glance view.
- **Library** — full book list with search and filters.
- **Feed** — placeholder, "Coming soon" (`FeedScreen.kt`).

### Dashboard

`DashboardScreen.kt`:

- Time-of-day literary greeting pulled from the Rust core (`getGreeting`).
- Reading stats: total books, in-progress, finished, reading time
  (`getDashboardStats`).
- "Continue Reading" card for the most-recently-read book
  (`getRecentlyReadBooks`).
- Up to three quick-action cards when the library is empty (Add Your First
  Book, Record Your Reading, Finish a Book).
- Recent searches as chips (`getRecentSearches`).
- A "Feed" placeholder card pointing to the upcoming social/recommendations
  tab.

### Library

`LibraryScreen.kt`:

- Paginated book list (50 per page, newest first) via `Bridge.listBooks`.
- Pull-to-refresh.
- Search box at the top.
- Animated filter panel: sort (Newest / Oldest), filter chips for format,
  language (with flag emoji), and status — populated from
  `getDistinctFormats` / `getDistinctLanguages` / `getDistinctWorkStatuses`.
- Tap the `+` in the top bar to open the **Add Book Wizard**.

### Add Book Wizard

The headline flow, a 3-step `ModalBottomSheet` (`wizard/AddBookWizard.kt`).

1. **Search** (`StepSearch.kt`) — type a title or ISBN (≥3 characters,
   750 ms debounce). The app runs two searches in parallel:
   - Local library dedup against existing works
     (`Bridge.findWorksByTitlePrefix`).
   - Online provider search via `Bridge.initPlugins` + `Bridge.searchProviders`,
     showing title, authors, year, ISBN, and source (`via <source>`).
   - Provider errors surface a tailored callout: auth required, rate limited
     (with `retry_after_seconds`), timeout, not found, provider down
     (strings in `res/values/strings.xml`).
   - Pick an online result to populate the wizard, or tap "Skip search and
     add manually".

2. **Authors** (`StepAuthors.kt`) — add one or more authors, each with a role
   (author, illustrator, translator, narrator). Each addition calls
   `findOrCreateAuthor` + `linkWorkAuthor`.

3. **Review** (`StepReview.kt`) — edit title (required), description, ISBN,
   published date, language, publisher. Every ISBN change triggers a live
   duplicate check (`Bridge.findWorkByIsbn`).
   - **Create Book** runs an atomic Work + Edition + authors + ISBN +
     publisher save (`Bridge.createBookComplete`).
   - On a duplicate collision (pre-check or `MobileException.IsbnConflict`
     from the save) the **Duplicate recovery dialog** appears.

### Duplicate recovery

`DuplicateWorkDialog.kt`. Three actions:

- **Replace (merge)** — `Bridge.mergeReplaceWork`. Merges new metadata into
  the existing work; preserves all existing editions, identifiers, reading
  progress, and inventory.
- **Add as new edition** — `Bridge.createEditionForWork`. Attaches the new
  ISBN/metadata as another edition of the existing work.
- **Link ISBN to existing** — opens `EditionPickerDialog` (same file); pick
  an edition and `Bridge.linkIsbnToExistingEdition` attaches the ISBN to it.

### What is NOT exposed to end users yet

The Rust core has working endpoints for these, but no Android UI calls them
today. End users cannot do the following on Android at this time:

- **Record or view reading progress** — `upsert_reading_progress`,
  `get_reading_progress` exist in FFI but have no Kotlin caller.
- **Local files / file cache** — `register_local_file`, `list_cached_files`,
  `get_cached_file_path`, `delete_cached_file` exist; no UI.
- **Sync between devices** — no Ktor sync client, no peer discovery, no
  prefetch worker, no `SyncManager`.
- **Browse OPDS catalogs** — no `:feature:opds` module, no UI surface.
- **Pair with another device** — no pairing screen, no mDNS service.
- **Scan ISBN barcodes with the camera** — no CameraX/ML Kit wired, no
  `CAMERA` permission in `AndroidManifest.xml`.
- **Edit tags, genres, or subjects from the UI** — FFI exposes CRUD for all
  three; no Kotlin caller.
- **Read a book in-app** — no reader screen; reading-progress FFI is
  unwired.
- **Settings screen** — no Sentry consent dialog, no analytics
  toggle. `LivtetApp` initializes Sentry and Timber unconditionally
  when `BuildConfig.SENTRY_DSN` is non-empty (the `playstore`
  flavor only — see "Application init" below). The Settings UI
  itself does exist (gear in the top bar of every tab) and covers
  Paired Devices, Discovered on this network, Plugins, **Labs**
  (experimental feature flags with per-flavor build-time gates;
  see "Labs feature flags" below), and Appearance.

---

The Android shell for livtet — a thin Compose UI over the shared Rust
core in `crates/ffi/livtet-ffi/`, wired through UniFFI. Two Gradle
modules — `:app` and `:core:designsystem` — plus an included `:build-logic`
for convention plugins.

Developer reference follows: Gradle Modules, Build Configuration, FFI
Surface, etc.

## Gradle Modules

| Module                | Type                            | Path                                          |
| --------------------- | ------------------------------- | --------------------------------------------- |
| `:app`                | `com.android.application`       | `mobile/android/app/`                         |
| `:core:designsystem`  | `livtet.android.library`        | `mobile/android/core/designsystem/`           |
| `:build-logic`        | included build (convention plugins) | `mobile/android/build-logic/`              |

Convention plugins live in `build-logic/` (e.g.
`AndroidApplicationLintConventionPlugin` and
`AndroidLibraryLintConventionPlugin`) and are applied via
`livtet.android.application.lint` to keep lint configuration consistent.

## Build Configuration

- `compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`
- `versionCode = 2`, `versionName = "0.1.1"`
- `ndkVersion = "27.1.12297006"`
- `applicationId = "net.olamaelcu.livtet"`
- `JavaVersion.VERSION_17` / `JvmTarget.JVM_17`
- Compose enabled via `org.jetbrains.kotlin.plugin.compose`
- `BuildConfig` enabled; `GOOGLE_BOOKS_API_KEY` is sourced from the
  `GOOGLE_BOOKS_ANDROID_API_KEY` env var, `GOOGLE_API_KEY` from the
  `LIVTET_GOOGLE_API_KEY_ANDROID` Gradle property, and `SENTRY_DSN` is
  per-flavor (default empty; set by the `playstore` flavor from the
  `LIVTET_SENTRY_DSN_MOBILE` Gradle property).

### Product Flavors

A `store` flavor dimension exposes three variants:

| Flavor      | applicationId suffix | Notes                                                                                              |
| ----------- | -------------------- | -------------------------------------------------------------------------------------------------- |
| `playstore` | (none)               | Default, Google-aligned build; Sentry DSN injected from `LIVTET_SENTRY_DSN_MOBILE`.                 |
| `fdroid`    | `.fdroid`            | F-Droid-aligned build; empty Sentry DSN (no tracking).                                             |
| `generic`   | `.generic`           | Sideload / unsigned-style build; empty Sentry DSN (no tracking).                                   |

### UniFFI bindings

The `android-generate-bindings` Mise task (see `.mise/tasks/`) runs
`uniffi-bindgen` against `crates/ffi/livtet-ffi/` and writes the
generated Kotlin files to `mobile/android/build/generated/source/uniffi/kotlin/`.
The `:app` module adds this directory as a `srcDir`:

```kotlin
sourceSets["main"]
    .kotlin
    .srcDir(layout.buildDirectory.dir("../../build/generated/source/uniffi/kotlin"))
```

### Key Dependencies

- **Compose** — Material 3 via `androidx.compose:compose-bom:2026.03.00`,
  with `material-icons-extended` and `navigation-compose:2.9.8`.
- **Coroutines** — `kotlinx-coroutines-{core,android}:1.11.0`.
- **Serialization** — `kotlinx-serialization-json:1.7.3`.
- **Logging** — `Timber` and `Sentry Android 8.43.0` (initialized manually in
  `LivtetApp`; only enabled when `BuildConfig.SENTRY_DSN` is non-empty,
  i.e. in the `playstore` flavor).
- **Identifiers** — `ULID Kotlin` for client-side id generation.
- **Native bridge** — JNA 5.14.0, plus UniFFI-generated Kotlin bindings at
  `mobile/android/build/generated/source/uniffi/kotlin/` (see above).

## App Code

`mobile/android/app/src/main/java/net/olamaelcu/livtet/`:

| File                            | Role                                                                                                                              |
| ------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `LivtetApp.kt`                  | `Application` subclass; initializes Timber and (for `playstore` builds with a configured DSN) Sentry.                              |
| `SplashActivity.kt`             | Cold-start splash; calls `Bridge.init(databasePath)` and starts `DashboardActivity` once the Rust core is up.                      |
| `DashboardActivity.kt`          | Root `ComponentActivity`; renders `LivtetTheme` + a Compose `NavHost` with bottom `NavigationBar` (Dashboard / Library / Feed).    |
| `DashboardScreen.kt`            | Greeting + reading stats + Continue Reading + Recent Searches + quick-action cards.                                               |
| `FeedScreen.kt`                 | "Coming soon" placeholder for the future Feed tab.                                                                                |
| `LibraryScreen.kt`              | Paginated book list, search, animated filter panel (sort/format/language/status), pull-to-refresh; opens `AddBookWizard` on `+`.    |
| `Bridge.kt`                     | Thin UniFFI wrapper exposing ~26 of the generated Kotlin bindings as `suspend` functions on `Dispatchers.IO`.                       |
| `JniBridge.kt`                  | Defensive `System.loadLibrary("livtet_ffi")` (UniFFI also loads on first call).                                                   |
| `wizard/AddBookWizard.kt`       | `ModalBottomSheet` hosting the 3-step add-book flow.                                                                              |
| `wizard/WizardState.kt`         | `WizardData` state class carried across the 3 steps.                                                                              |
| `wizard/StepSearch.kt`          | Step 1 — debounced title/ISBN search; local dedup + provider search; provider-error callout.                                      |
| `wizard/StepAuthors.kt`         | Step 2 — add authors with role.                                                                                                   |
| `wizard/StepReview.kt`          | Step 3 — review + atomic save; live ISBN duplicate check.                                                                         |
| `wizard/DuplicateWorkDialog.kt` | Duplicate-recovery dialog (merge / add as edition / link ISBN) and `EditionPickerDialog` sub-dialog.                              |

### Application init

`LivtetApp.onCreate()` initializes Timber unconditionally and Sentry
conditionally. Sentry is only started when `BuildConfig.SENTRY_DSN` is
non-empty; in this codebase that means only the `playstore` flavor (the
`fdroid` and `generic` flavors have an empty DSN by design). There is no
runtime consent dialog — telemetry is opt-out by not shipping the DSN.

## Labs feature flags

Experimental / staged-release toggles live in **Settings → Labs**.
Each flag has two independent gates:

1. **Build-time gate** (`BuildConfig.LABS_BUILD_TIME_DEFAULTS_JSON`).
   Emitted by `app/build.gradle.kts` from the `labsFlagBuildDefaults`
   map (in `defaultConfig`) and re-emitted per flavor in
   `productFlavors`. The `playstore` flavor unlocks individual flags
   for canary-style rollouts; `fdroid` and `generic` lock every flag
   off because they ship no experimental surfaces.
2. **Persisted override** (DataStore file `feature_flags` — separate
   from the theme `settings` file). Stores the user's toggle state.
   Missing entries fall back to `LabsFlag.defaultEnabled`.

Effective value:
`buildTimeDefault(flag) && persistedOverride(flag, default = flag.defaultEnabled)`.

When the build-time gate is off, the toggle in Settings renders as
disabled with a "Locked by this build" caption and `setEnabled()` is
a no-op (the override is still persisted, so promoting the gate later
will respect the prior intent). All flags default to `off` and the
runtime resolver falls back to `unlocked` for any flag missing from
the Gradle JSON — so a stale build never silently enables a brand-new
flag.

Adding a new Labs flag is a **two-edit** operation:

- Add an entry to `LabsFlag` in `settings/Labs.kt` (with stable
  `key`, `defaultEnabled`, and string-resource ids).
- Add the matching `key -> boolean` pair to the
  `labsFlagBuildDefaults` map in `app/build.gradle.kts` AND to each
  flavor that should override the default.

## Native Library Layout

`liblivtet_ffi.so` is committed at `app/src/main/jniLibs/` for four ABIs
(`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) and additionally packaged
into the APK by the `org.mozilla.rust-android-gradle.rust-android` Gradle
plugin out of `crates/ffi/livtet-ffi/` (see `app/build.gradle.kts`
`cargo { }` block):

```
mobile/android/app/src/main/jniLibs/
├── arm64-v8a/liblivtet_ffi.so
├── armeabi-v7a/liblivtet_ffi.so
├── x86/liblivtet_ffi.so
└── x86_64/liblivtet_ffi.so
```

`defaultConfig.ndk.abiFilters` narrows the runtime set to
`arm64-v8a`, `x86`, `x86_64` (the `rust-android` Gradle plugin targets
the same three). UniFFI's Kotlin bindings load the library lazily on
first call, so the `:app` module just needs the bindings on its classpath
— the `Bridge` object in `:app` triggers the load the first time any
UniFFI-typed function is invoked.

## FFI Surface (high level)

The generated Kotlin bindings (in `mobile/android/build/generated/source/uniffi/kotlin/`)
expose the `livtet-ffi` Rust crate as Kotlin objects and `suspend` functions.
Operations are typed against the `livtet-ffi-types` Rust crate and serialized
across the boundary via UniFFI's built-in `Record` / `Enum` support — no
JSON marshalling on the Android side. The `Bridge.kt` object wraps the raw
UniFFI-typed calls into `:app`-facing `suspend` functions on `Dispatchers.IO`.

## Resources

- `res/values/strings.xml` — `app_name`, provider-error strings, duplicate-dialog and edition-picker copy
- `res/values/colors.xml` — `ic_launcher_background`, `brand` (generated from design tokens)
- `res/values/themes.xml` — `Theme.Livtet` (Material Light NoActionBar with brand-tinted system bars)
- `res/mipmap-*/ic_launcher{,_round}.xml` — adaptive launcher icon
- `res/drawable/`, `res/font/` — vector and font assets

## Permissions

Declared in `AndroidManifest.xml`:

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`

No camera, Wi-Fi, or multicast permissions are declared — barcode scanning
and peer discovery are not implemented yet.

## Tests

### Instrumented tests — `mobile/android/app/src/androidTest/java/net/olamaelcu/livtet/`

- `AddBookSearchTest.kt`
- `CrashBufferRule.kt`

Frameworks: AndroidX Test, Espresso 3.6.1, Compose UI Test.

There are no unit tests under `src/test/` in the current codebase.
Screenshot tests are not yet implemented.

## How to Build

The user manages the dev server / build lifecycle, so no commands are
duplicated here. The `:app` module produces the three flavor variants
(`playstoreDebug`/`playstoreRelease`, `fdroidDebug`/`fdroidRelease`,
`genericDebug`/`genericRelease`) when invoked through the standard
AGP `assemble*` tasks. `versionCode = 2`, `versionName = "0.1.1"`. Only
the `playstore` flavor has a configured Sentry DSN.
