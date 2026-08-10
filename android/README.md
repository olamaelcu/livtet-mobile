# Android App

## End-User Capabilities

What someone with `livtet` installed on Android can actually do today.

### First launch

Splash screen (brand mark, "Loading your library…" spinner) initializes the
Rust core (`core/livtet-ffi/`) via `Bridge.init` and hands off to the
main app. Source: `SplashActivity.kt`, `Bridge.kt:init`.

### Bottom navigation

Four tabs hosted by `DashboardActivity`'s Compose `NavHost`:

- **Dashboard** — at-a-glance view.
- **Library** — paginated book list.
- **Social** — placeholder, "Coming soon" (`FeedScreen.kt`); also hosts a
  hidden launcher for the Jigsaw Puzzle easter egg.
- **Account** — Google / Apple / AT Protocol sign-in (`account/AccountScreen.kt`).

A gear icon in the top app bar navigates to **Settings** from every tab.

### Dashboard

`DashboardScreen.kt`:

- Time-of-day literary greeting pulled from the Rust core (`getGreeting`).
- Reading stats, "Continue Reading", recent searches, and the
  quick-action cards (Add Your First Book, Record Your Reading,
  Finish a Book) are **not currently rendered**: the matching Bridge
  wrappers (`getDashboardStats`, `getRecentlyReadBooks`,
  `getRecentSearches`) are stubs returning `null` / empty until the
  upstream FFI catches up. See "What is NOT exposed to end users yet"
  below.
- A "Feed" placeholder card pointing to the upcoming social/recommendations
  tab.

### Library

`LibraryScreen.kt` (state owned by `LibraryViewModel.kt`):

- Paginated book list (50 per page, newest first) via `Bridge.listBooks`,
  ordered by `works.created_at` (`DESCENDING`).
- Empty state shows a literary quotation via `Bridge.getEmptyStateQuotation`.
- When KOReader is installed, each book row exposes an "Open in KOReader"
  affordance (`KoreaderPresence` + `OpenInKoreader`).
- **Not yet wired** (per the source's own TODO): pull-to-refresh, search box,
  filter panel (sort / format / language / status), cover thumbnails, and the
  Add Book wizard entry point — all deferred to follow-up commits.

### Add Book Wizard

A 3-step `ModalBottomSheet` (`wizard/AddBookWizard.kt`). Today only the first
two steps do anything useful; the third is a form-only placeholder.

1. **Search** (`StepSearch.kt`) — type a title or ISBN (≥3 characters,
   750 ms debounce). On mount, calls `Bridge.initPlugins()` once. While the
   user types, calls `Bridge.searchProviders(query)` and renders title,
   authors, year, ISBN, and source (`via <source>`). Pick a result to advance,
   or use "Next: Authors" with the entered title to skip search. Provider-error
   strings (`res/values/strings.xml`: auth required, rate-limited with
   `retry_after_seconds`, timeout, not found, provider down) are defined in
   resources but **not yet rendered** — the step currently shows a generic
   `"Could not search online: <exception message>"` on error.
   - **Not wired**: local library dedup against existing works
     (`findWorksByTitlePrefix` and friends are absent from the current
     `core/livtet-ffi` build).

2. **Authors** (`StepAuthors.kt`) — add one or more authors, each with a role
   (author, illustrator, translator, narrator). Local-only — entries are held
   in `StepAuthors` state, not persisted. **No FFI calls** in this step.

3. **Review** (`StepReview.kt`) — form-only preview with title and a "Saving
   books is unavailable in this build" message. The intended Bridge calls
   (`findWorkByIsbn`, `createBookComplete`, `mergeReplaceWork`,
   `createEditionForWork`, `linkIsbnToExistingEdition`) and the
   `MobileException.IsbnConflict` handler are documented in the source as
   "previously called" — the upstream FFI surface is missing them, so this
   step is reduced to a placeholder until the Rust side catches up.

### Settings

`settings/SettingsScreen.kt` (state owned by `SettingsViewModel.kt`). Two
top-level sections, plus a debug-only "Developer tools" panel:

- **Labs** — every flag declared in `LabsFlag` is rendered as a
  switch with title + description; flags whose build-time gate is `false`
  render disabled with a "Locked by this build" caption (see
  "Labs feature flags" below).
- **Appearance** — System / Light / Dark theme mode (`ThemeManager`,
  DataStore-backed).
- **Developer tools** (`BuildConfig.DEBUG` only) — wipes the local SQLite
  database and re-seeds it with N demo works via `Bridge.resetAndSeed`,
  with two-tap confirmation and a `SeedResultMobile` summary.

The Settings screen also spins up `DiscoveryService` (mDNS
`_livtet-sync._tcp` browsing via NsdManager) in the background, but its
results are not surfaced in the UI yet. The Paired Devices,
Discovered on this network, and Plugins sections are gone — they depended
on FFI calls (`getPairedDevices`, `listInstalledPlugins`,
`getNetworkAddresses`, `pairDevice`, `unpairDevice`, `setPluginEnabled`)
that are not in the current `core/livtet-ffi` build. The screen shows an
"unavailable in this build" hint at the top.

### What is NOT exposed to end users yet

The Rust core has working endpoints for these, but no Android UI calls them
today (and several aren't even wrapped in `Bridge.kt` yet). End users cannot
do the following on Android at this time:

- **Record or view reading progress** — `upsert_reading_progress`,
  `get_reading_progress` exist in FFI but have no Kotlin caller.
- **Local files / file cache** — `register_local_file`, `list_cached_files`,
  `get_cached_file_path`, `delete_cached_file` exist; no UI.
- **Pair with another device** — `Bridge.pairWithDesktop`,
  `Bridge.syncOnce`, `Bridge.cancelSync` are exposed but no UI surfaces
  them; `DiscoveryService` collects peers in the background but the
  Settings screen does not render them.
- **Browse OPDS catalogs** — no `:feature:opds` module, no UI surface.
- **Scan ISBN barcodes with the camera** — no CameraX/ML Kit wired, no
  `CAMERA` permission in `AndroidManifest.xml`.
- **Edit tags, genres, or subjects from the UI** — FFI exposes CRUD for all
  three; no Kotlin caller.
- **Read a book in-app** — no reader screen; reading-progress FFI is
  unwired.
- **Save a book from the wizard** — `StepReview` is a placeholder; no FFI
  save call is reached.
- **Resolve duplicate works / editions** — `DuplicateWorkDialog.kt`
  does not exist; the merge / add-as-edition / link-ISBN paths it would
  drive are not in the FFI build either.
- **Provider-error callouts in the wizard** — string resources are
  defined (`res/values/strings.xml`) but `StepSearch` only renders a
  generic error message.

---

The Android shell for livtet — a thin Compose UI over the shared Rust
core in `core/livtet-ffi/`, wired through UniFFI. Four Gradle
modules (`:app`, `:core:designsystem`, `:core:auth`, `:jigsaw`) plus two
included builds: `:build-logic` for convention plugins and the
`branding/android` composite build that substitutes the
`net.olamaelcu:livtet-branding` library from the checked-out
`livtet-branding` repo (one level up from `mobile/`, at
`livtet-ecosystem/branding/android`).

Developer reference follows: Gradle Modules, Build Configuration, FFI
Surface, etc.

## Gradle Modules

| Module                | Type                            | Path                                          |
| --------------------- | ------------------------------- | --------------------------------------------- |
| `:app`                | `com.android.application`       | `mobile/android/app/`                         |
| `:core:designsystem`  | `com.android.library`           | `mobile/android/core/designsystem/`           |
| `:core:auth`          | `com.android.library`           | `mobile/android/core/auth/`                   |
| `:jigsaw`             | `com.android.library`           | `mobile/android/jigsaw/`                      |
| `:build-logic`        | included build (convention plugins) | `mobile/android/build-logic/`              |

A second included build, the sibling `livtet-ecosystem/branding/android`
repo (substituted as `net.olamaelcu:livtet-branding` via
`includeBuild("…") { dependencySubstitution { … } }` in
`settings.gradle.kts`), supplies the launcher icons, brand color, and
brand color scheme consumed by `:core:designsystem` and the `:app`
flavors. It is not a normal `:include(":…")` module — see
`settings.gradle.kts`.

Convention plugins live in `build-logic/` (e.g.
`AndroidApplicationLintConventionPlugin` and
`AndroidLibraryLintConventionPlugin`) and are applied via
`livtet.android.application.lint` / `livtet.android.library.lint` to keep
lint configuration consistent across every module.

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
`uniffi-bindgen` against `core/livtet-ffi/` and writes the
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

| File                              | Role                                                                                                                              |
| --------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `LivtetApp.kt`                    | `Application` subclass; seeds `setSystemSecrets` (Google Books API key), conditionally starts Sentry (only when `BuildConfig.SENTRY_DSN` is non-empty), plants `Timber.DebugTree` in `BuildConfig.DEBUG`, and registers `KoreaderPresence`. |
| `SplashActivity.kt`               | Cold-start splash; calls `Bridge.init(databasePath)` and starts `DashboardActivity` once the Rust core is up.                      |
| `DashboardActivity.kt`            | Root `ComponentActivity`; renders `LivtetTheme` + a Compose `NavHost` with bottom `NavigationBar` (Dashboard / Library / Social / Account) and a gear icon that pushes Settings.    |
| `DashboardScreen.kt`              | Greeting + (currently-stub) reading stats, Continue Reading, Recent Searches, quick-action cards, and a "Feed coming soon" placeholder. Only the greeting is live. |
| `LibraryScreen.kt`                | Paginated book list (50/page, newest first) with KOReader handoff per row when KOReader is installed. Search / filters / pull-to-refresh / Add-Book entry are deferred.    |
| `LibraryViewModel.kt`             | State holder for the Library screen; calls `Bridge.listBooks` and `Bridge.getEmptyStateQuotation`.                                |
| `FeedScreen.kt`                   | "Coming soon" placeholder for the Social tab, with a hidden Jigsaw Puzzle launcher in the top-end corner.                          |
| `Bridge.kt`                       | Thin UniFFI wrapper exposing 13 of the generated UniFFI Kotlin functions as `suspend` (or plain) functions on `Dispatchers.IO` (`init`, `getGreeting`, `getEmptyStateQuotation`, `initPlugins`, `lookupIdentifier`, `searchProviders`, `listBooks`, `pairWithDesktop`, `syncOnce`, `cancelSync`, `seedDatabase`, `resetAndSeed`, and `isInitialized`). `getDashboardStats` / `getRecentlyReadBooks` / `getRecentSearches` are stubs returning `null` / empty until the upstream FFI catches up. |
| `JniBridge.kt`                    | Defensive `System.loadLibrary("livtet_ffi")` (UniFFI also loads on first call).                                                   |
| `DiscoveryService.kt`             | mDNS discovery for `_livtet-sync._tcp` peers on the LAN; started by `SettingsViewModel` but not yet surfaced in the UI.          |
| `KoreaderPresence.kt`             | Broadcast-receiver-backed `StateFlow<Boolean>` for "is KOReader installed?"; registered from `LivtetApp.onCreate`.                 |
| `OpenInKoreader.kt`               | Hand-off helper that starts KOReader; `openBookInKoreader(book)` is a stub until the FFI exposes a local file URI per `Book`. |
| `account/AccountScreen.kt`        | Sign-in screen for the Account tab: Google (gated on `BuildConfig.GOOGLE_SIGN_IN_ENABLED`), Apple (`APPLE_SIGN_IN_ENABLED`), AT Protocol — ATProto expects the OAuth flow routed through `DashboardActivity`'s `livtet://atproto-callback` intent filter. |
| `account/AccountViewModel.kt`     | `AndroidViewModel` orchestrating `AccountManager`; emits `SignInSucceeded` / `SignInFailed` / `SignOutComplete` events for the UI. |
| `settings/SettingsScreen.kt`      | Settings UI: Labs (per-flag toggles + locked-by-build caption), Appearance (System / Light / Dark), and a debug-only "Reset and seed library" developer tool. Paired Devices / Plugins sections are absent with an "unavailable in this build" hint. |
| `settings/SettingsViewModel.kt`   | State holder; starts `DiscoveryService` and the Labs / theme DataStore flows; runs the debug `resetAndSeed` job. |
| `settings/ThemeManager.kt`        | DataStore-backed `Mode` (System / Light / Dark) read by `DashboardActivity` and `SplashActivity`. |
| `settings/LabsFlag.kt`            | Enum of every Labs flag (`SHOW_EXPERIMENTAL_DUPLICATES_BADGE`, `INSTANT_LOCAL_FILE_IMPORT`) with stable key, default, and string-resource ids. |
| `settings/FeatureFlagsManager.kt` | DataStore-backed Labs resolver; effective value = `buildTimeDefault(flag) && persistedOverride(flag, flag.defaultEnabled)`. |
| `wizard/AddBookWizard.kt`         | `ModalBottomSheet` hosting the 3-step add-book flow.                                                                              |
| `wizard/WizardState.kt`           | `WizardData` and `ProviderResult` state classes used by the wizard. Step 3 has no FFI calls — it is a form-only placeholder.   |
| `wizard/StepSearch.kt`            | Step 1 — debounced title/ISBN search (≥3 chars, 750 ms). Calls `Bridge.initPlugins()` once on mount and `Bridge.searchProviders(query)` on each edit; renders title / authors / year / ISBN / `via <source>`. Provider-error strings in `res/values/strings.xml` are not yet wired. |
| `wizard/StepAuthors.kt`           | Step 2 — add authors with role (author / illustrator / translator / narrator). Local-only state, no FFI.                            |
| `wizard/StepReview.kt`            | Step 3 — form-only preview; the planned `findWorkByIsbn` / `createBookComplete` / duplicate-recovery flow is documented in the source as "previously called" but the upstream FFI is missing, so the screen renders a "Saving books is unavailable in this build" message and a working **Done** button that just dismisses the wizard. |

### Application init

`LivtetApp.onCreate()` runs in this order:

1. Builds a `Map<String, String>` with the Google Books API key
   (`BuildConfig.GOOGLE_API_KEY`, sourced from the
   `LIVTET_GOOGLE_API_KEY_ANDROID` Gradle property) and passes it to the
   FFI via `setSystemSecrets(...)` so plugins can resolve it by name.
2. Starts Sentry **only when** `BuildConfig.SENTRY_DSN` is non-empty — in
   this codebase that means only the `playstore` flavor (the `fdroid`
   and `generic` flavors have an empty DSN by design). There is no
   runtime consent dialog; telemetry is opt-out by not shipping the DSN.
   Sentry's auto-init ContentProvider is disabled in `AndroidManifest.xml`
   (`io.sentry.auto-init=false`) so an empty DSN does not crash at
   attach time.
3. Plants a `Timber.DebugTree()` **only when** `BuildConfig.DEBUG` is
   true. Release builds ship with no logging tree at all.
4. Registers `KoreaderPresence` so the "Open in KOReader" affordance on
   each Library row updates live when KOReader is installed or removed.

## Labs feature flags

Experimental / staged-release toggles live in **Settings → Labs**.
Each flag has two independent gates:

1. **Build-time gate** (`BuildConfig.LABS_BUILD_TIME_DEFAULTS_JSON`).
   Emitted by `app/build.gradle.kts` from the `labsFlagBuildDefaults`
   map (in `defaultConfig`) and re-emitted per flavor in
   `productFlavors`. The `playstore` flavor unlocks
   `show_experimental_duplicates_badge` for canary-style rollouts (every
   other flag stays off); `fdroid` and `generic` lock every flag off
   because they ship no experimental surfaces.
2. **Persisted override** (DataStore file `feature_flags` — separate
   from the theme `settings` file). Keys are namespaced
   `flag:<labs_key>` and store the user's toggle state. Missing entries
   fall back to `LabsFlag.defaultEnabled`. Both Labs flags currently
   have `defaultEnabled = false`.

Effective value:
`buildTimeDefault(flag) && persistedOverride(flag, default = flag.defaultEnabled)`.

When the build-time gate is off, the toggle in Settings renders as
disabled with a "Locked by this build" caption and `setEnabled()` is
a no-op — the override is **not** persisted in that case, so promoting
the gate later will leave the flag at `defaultEnabled` until the user
touches the toggle. All flags default to `off` and the
runtime resolver falls back to `unlocked` for any flag missing from
the Gradle JSON (or for a malformed JSON literal) — so a stale build
never silently locks a brand-new flag, and a broken Gradle build
never silently disables a flag the codebase thinks should be available.

Adding a new Labs flag is a **two-edit** operation:

- Add an entry to `LabsFlag` in `settings/LabsFlag.kt` (with stable
  `key`, `defaultEnabled`, and string-resource ids).
- Add the matching `key -> boolean` pair to the
  `labsFlagBuildDefaults` map in `app/build.gradle.kts` AND to each
  flavor that should override the default. The
  `LABS_BUILD_TIME_DEFAULTS_JSON` `buildConfigField` is re-emitted per
  flavor even when the JSON is identical, so flavors can opt into
  different staged rollouts without touching `defaultConfig`.

## Native Library Layout

`liblivtet_ffi.so` is packaged into the APK by the
`org.mozilla.rust-android-gradle.rust-android` Gradle plugin out of
`core/livtet-ffi/`. AGENTS.md notes that
`app/src/main/jniLibs/` is a cache for prebuilt `.so` files and is safe
to delete before any build that touches the FFI crate.

`defaultConfig.ndk.abiFilters` narrows the runtime set to
`arm64-v8a`, `x86`, `x86_64` (the `rust-android` Gradle plugin targets
the same three — `armeabi-v7a` is intentionally not built). The
checked-in prebuilt currently on disk is `x86_64` only; the other two
ABIs are produced from source by the rust-android plugin during the
Gradle build. UniFFI's Kotlin bindings load the library lazily on
first call, so the `:app` module just needs the bindings on its classpath
— the `JniBridge` / `Bridge` objects in `:app` trigger the load the first
time any UniFFI-typed function is invoked.

## FFI Surface (high level)

The generated Kotlin bindings (in `mobile/android/build/generated/source/uniffi/kotlin/`)
expose the `livtet-ffi` Rust crate as Kotlin objects and `suspend` functions.
Operations are typed against the `livtet-ffi-types` Rust crate and serialized
across the boundary via UniFFI's built-in `Record` / `Enum` support — no
JSON marshalling on the Android side. The `Bridge.kt` object wraps the raw
UniFFI-typed calls into `:app`-facing `suspend` functions on `Dispatchers.IO`
(see the App Code table above for the exact list of 13 wrapped functions
plus 3 stub Bridge methods awaiting upstream FFI).

## Resources

Under `mobile/android/app/src/main/res/`:

- `values/strings.xml` — `app_name`, provider-error strings
  (auth required / rate-limited with `retry_after_seconds` / timeout /
  not found / provider down), duplicate-dialog and edition-picker copy,
  Labs flag titles + descriptions + the "Locked by this build" caption,
  and a Save-failure recovery block. Some of these (the provider-error
  callouts in particular) are defined but not yet referenced by code.
- `values/themes.xml` — `Theme.Livtet` (`android:Theme.Material.Light.NoActionBar`,
  brand-tinted status / navigation / window background so cold-start
  matches the Compose `LivtetTheme` once it renders).
- `drawable/logo.xml` — splash brand mark.
- `font/` — empty; font assets come from the `livtet-branding`
  composite build (see "Gradle Modules").

The launcher icons (`res/mipmap-*/ic_launcher{,_round}.xml` plus the
`mipmap-anydpi-v26` adaptive-icon descriptor) and the brand color
resources (`ic_launcher_background`, `brand`) live in the
`livtet-branding` composite build, not in this module. Each
flavor — `playstore` / `fdroid` / `generic` — has its own per-flavor
mipmap set in
`livtet-ecosystem/branding/android/library/src/<flavor>/res/`.

## Permissions

Declared in `AndroidManifest.xml`:

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.CHANGE_WIFI_MULTICAST_STATE` — required by
  `NsdManager.discoverServices` on some OEM Android builds; it's a
  normal permission (no runtime prompt), and it is used by
  `DiscoveryService` to browse `_livtet-sync._tcp` peers on the LAN.
  The corresponding `discoverServices` call is started from
  `SettingsViewModel` even though no UI surfaces the result yet.
- `<queries>` block for `org.koreader.launcher` and
  `org.koreader.launcher.fdroid` — required on Android 11+ so the
  package-visibility rules let `KoreaderPresence` see whether KOReader
  is installed and `OpenInKoreader.launchKoreader` can start it.

No camera permission is declared — barcode scanning is not implemented.

## Tests

### Instrumented tests — `mobile/android/app/src/androidTest/java/net/olamaelcu/livtet/`

- `AddBookSearchTest.kt` — exercises the Add-Book wizard's search flow.
- `CrashBufferRule.kt` — JUnit rule shared by the other tests.
- `DashboardA11yTest.kt` — regression tests for the dashboard
  accessibility fixes (Compose semantics, selected-tab semantics,
  click actions).
- `account/AccountScreenTest.kt` — covers the Account tab UI.

Frameworks: AndroidX Test 1.7.0, Espresso 3.7.0, Compose UI Test 1.10.0.

There are no `:app`-module unit tests under `src/test/` in the current
codebase (`./gradlew :app:test` resolves no test sources). Unit tests do
exist for `:jigsaw` (`jigsaw/src/test/kotlin/...`) and `:core:auth`
(`core/auth/src/test/kotlin/...`). Screenshot tests are not yet
implemented for `:app`.

## How to Build

The user manages the dev server / build lifecycle, so no commands are
duplicated here. The `:app` module produces the three flavor variants
(`playstoreDebug`/`playstoreRelease`, `fdroidDebug`/`fdroidRelease`,
`genericDebug`/`genericRelease`) when invoked through the standard
AGP `assemble*` tasks. `versionCode = 2`, `versionName = "0.1.1"`. Only
the `playstore` flavor has a configured Sentry DSN.
