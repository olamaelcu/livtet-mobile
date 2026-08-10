# Livtet iOS app

Native iOS client for the Livtet library desktop app. SwiftUI + UniFFI
bindings into the Rust core (`core/livtet-ffi/`). The iOS app and
the desktop Tauri app share the same Rust database layer; the iOS app
sees it through a generated Swift bridge.

See [ADR 0017](../../docs/reference/adr/0017-ios-app-architecture.md)
for the architectural rationale behind the dependency split,
deployment target, and APFS case-folding workaround.

## What users can do today

The app ships five tabs, a polished boot flow, and a unified design
system. Dashboard, Library, Puzzle, and Settings are functional and
talk to the local Rust database; the Feed tab is a stub for a future
social / recommendations surface.

### Shipped

- **Boot flow.** A splash screen spins while the Rust database is
  initialised through `LivtetCoreBridge.initialize`. On failure the
  screen switches to an inline error message with a **Retry** button;
  on success it cross-fades into the tab container.
- **Dashboard tab.** A greeting card shows a quote drawn from African
  American and African diaspora authors, with a time-of-day label
  ("Good morning", "Evening tide", …) mirrored in the navigation
  title. Beneath it, a 2×2 stats grid surfaces total books, books in
  progress, finished books, and total reading time (formatted `Xh` or
  `Xm`). A row of conditional quick-action cards nudges first-run
  goals — *Add Your First Book* (under 10 books), *Record Your
  Reading* (no first reading yet, or under 14 days in), *Finish a
  Book* (no finished books yet). *Add Your First Book* opens the
  Add Book wizard; the other two jump straight to the Library tab. A
  brand-tinted *Continue Reading* card surfaces the
  most recently read book with a percent meter, and a *Recent
  Searches* row sits below. Pull-to-refresh and an inline retry banner
  cover transient failures.
- **Library tab.** A scrollable list of every edition in the local
  database (each work expands into its editions), with a toolbar that
  offers a list/grid **Toggle view** button, a **Sort** menu (Newest
  first / Oldest first), a **Filter** button that opens a modal
  sheet, a **Duplicates** button that pushes the duplicate-management
  flow, and an **Add** button. The filter sheet exposes a segmented
  sort picker and horizontally-scrolling chip rows for format,
  language, and work-status (language chips carry an optional
  flag-emoji prefix); chip changes are coalesced through a 150 ms
  Combine debounce so the list never flashes between intermediate
  states. The empty state renders a literary pull-quote drawn from
  `data/quotes/empty.txt` next to the *Add Book* CTA. Pull-to-refresh
  and an inline error banner are wired identically to the Dashboard.
- **Design system.** Brand color tokens, surface / text / semantic
  palettes, and corner-radius tokens (`LivtetRadius.s/m/l`) drive
  both tabs. The app ships Work Sans (heading), Geist (body), and
  JetBrains Mono (code), plus the Lucide icon set generated from the
  shared design-tokens source. Dark mode is supported out of the box;
  on iPad the tab bar adapts into a sidebar via `.sidebarAdaptable`
  (iOS 18+).
- **Local storage.** Books, editions, authors, formats, languages,
  work-status values, reading-progress rows, and recent searches all
  live in a single SQLite database at `livtet.db` inside the app's
  Application Support directory (`~/Library/Application Support/net.olamaelcu.livtet/`) —
  created on first launch and persisted across reinstalls under the
  device's normal app-container backup rules.
- **Add Book wizard.** Both the Library tab's toolbar `+` button
  and its empty-state *Add Book* CTA open a full-screen
  `AddBookWizardView` (search → title/authors → hub → metadata
  fields → cover). The wizard routes every step through
  `LivtetCoreBridge` (and the `LivtetWizardBridgeAdapter`) so FFI
  calls like `createBook`, `createEdition`, `findOrCreateAuthor`,
  and `linkWorkAuthor` land in the same Rust database the rest of
  the app reads from. The Library tab reloads automatically when the
  wizard posts a `.livtetBookCreated` notification.

### Wired but not yet finished

- **Status filter chips.** The filter sheet already shows a *Status*
  section with chips for every distinct work-status value in the
  library, but tapping a chip only mutates the view-model's published
  set — `LibraryViewModel.fetchBooks()` does not forward the
  selection to the FFI. The section header reads *"Coming soon"*;
  the underlying status predicate is the next deferred feature.
- **Feed tab.** Selecting the *Feed* tab shows a single *"Coming
  soon"* panel; a *Feed* placeholder card also appears at the bottom
  of the Dashboard so the user understands what will live there.
- **First-launch consent dialog.** The Info.plist permission strings
  (`NSUserTrackingUsageDescription`, `NSLocalNetworkUsageDescription`,
  `NSCameraUsageDescription`, `NSBonjourServices = _livtet-sync._tcp`)
  are in place, but the actual first-launch dialog and the analytics
  wiring have not been reintroduced. Sentry is initialised in
  `LivtetApp.init` whenever `BuildConfig.sentryDSN` is non-empty;
  the generated `BuildConfig.generated.swift` currently ships a DSN
  value, so crash telemetry IS being sent from this build. Until the
  consent dialog ships there is no opt-in surface. See *Privacy and
  consent* below for the implementation-facing details.

### Hot reload support (Inject for SwiftUI)

The iOS app uses the **[Inject](https://github.com/krzysztofzablocki/Inject)**
SPM package (`Inject` 1.2.4, declared in `project.yml`) for live SwiftUI
reloads during development. Views are wrapped with `@ObserveInjection`
and `.enableInjection()`; the package polls the source files and applies
edits to the running simulator without a full rebuild.

**Important:** InjectionIII.app is NOT used. A legacy load site at
`LivtetApp.swift` for
`/Applications/InjectionIII.app/Contents/Resources/iOSInjection.bundle`
is wrapped in `#if false` (disabled — see the inline comment for why:
it crashes on Intel macOS). The Inject SPM package is a no-op in
Release builds, so leaving it in place does not affect shipping
binaries.

To use it:

1. Open `Livtet.xcodeproj` (not `.xcworkspace` — see *Note on
   directory naming* below) in Xcode.
2. Select the `Livtet iPhone` simulator.
3. **Run the app** (⌘R).
4. Save a SwiftUI view (`⌘S`); Inject picks up the change and rebuilds
   the view in the running simulator.

## Prerequisites

| Tool                     | Version  | Installed via                                                        |
| ------------------------ | -------- | -------------------------------------------------------------------- |
| Xcode                    | 16.4     | App Store (or `xcodes install 16.4` via `mise exec xcodes`)          |
| Xcode Command Line Tools | latest   | `xcode-select --install`                                             |
| CocoaPods                | 1.16.2   | `gem install cocoapods` (via mise gem)                               |
| Ruby                     | 3.3.6    | `mise install ruby@3.3.6`                                            |
| Rust (stable)            | 1.97.0   | `mise install rust@1.97.0`                                           |
| `uniffi-bindgen`         | 0.32.0   | `cargo install uniffi --features cli` (via `mise exec cargo:uniffi`) |
| `xcodegen`               | 2.42.0   | `brew install xcodegen` (mise-installed)                             |
| SwiftLint                | 0.58.0   | `brew install swiftlint` (mise-installed)                            |
| `mise`                   | latest   | <https://mise.jdx.dev>                                               |

The toolchain is pinned via the repo's top-level `mise.toml`; after
cloning, `mise run dev-setup` will install all of the above and
configure the git hooks for DCO sign-off.

## Quick start

From the repo root:

```bash
# 1. Install all toolchains (mise, Ruby, Rust, cargo crates)
mise run dev-setup

# 2. One-time iOS project generation + CocoaPods install
mise run ios-dev-init

# 2a. The xcframework is generated automatically (if missing) by `mise run ios-build`.
#     To force a rebuild, delete `mobile/ios/LivtetKit.xcframework/` first.

# 3. Build for iPhone Simulator (Debug)
mise run ios-build

# 4. Run on a simulator
mise run ios-run-simulator
mise run ios-run-simulator --ipad     # iPad simulator ("Livtet iPad")
```

If you change `mobile/ios/project.yml` (XcodeGen spec) or
`mobile/ios/Podfile`, re-run `mise run ios-dev-init`. If you change
the Rust side of `mobile/core/livtet-ffi/` (or `mobile/core/livtet-types/`),
`mise run ios-build` regenerates the Swift UniFFI bindings into
`mobile/ios/LivtetKit/Sources/` before invoking Xcode; no separate
`ios-bindings` task is needed.

## Where the Rust core lives and how bindings flow

```
mobile/core/                  ← Cargo workspace
├── livtet-ffi/               ← Rust crate, #[uniffi::export] functions
├── livtet-types/             ← UniFFI type mappings (shared with Android)
└── livtet-core/              ← entities, migrations, business logic
   (+ livtet-data, livtet-search, livtet-sync, livtet-cli, livtet-covers,
    livtet-plugins, livtet-plugins-lua, livtet-backup, livtet-test-utils)

mobile/ios/
├── Livtet/                  ← SwiftUI app (Models, Services, Utilities, Views)
├── LivtetKit/               ← local SPM package (Products: LivtetKit,
│                              LivtetKitFFI); exposes a Reader/ sub-module too
├── LivtetJigsaw/            ← local SPM package (jigsaw puzzle engine
│                              consumed by the Puzzle tab)
├── LivtetKit.xcframework/   ← static lib slices (device + sim fat),
│                              produced by `mise run ios-build`
├── Livtet.xcodeproj/        ← generated by xcodegen; not for hand-editing
└── Podfile                  ← CocoaPods manifest (currently a no-op
                               placeholder; CocoaPods integration is
                               disabled in `project.yml` and the Podfile
                               installs no pods)
```

Build pipeline:

1. `mise run ios-build` (single task — also generates Swift bindings)
    - Builds Rust FFI (`cargo build -p livtet-ffi --target ...`)
    - Creates fat library for simulator via `lipo -create` and merges
      the vendored mlua Lua static archive into `liblivtet_ffi.a`
    - Packages the result into `mobile/ios/LivtetKit.xcframework/`
      with `xcodebuild -create-xcframework`
    - Runs `uniffi-bindgen generate --library .../liblivtet_ffi.a --language swift`
      and stages the generated files into
      `mobile/ios/LivtetKit/Sources/LivtetKitFFI/livtet_ffi.swift` and
      `mobile/ios/LivtetKit/Sources/livtet_ffiFFI/{livtet_ffiFFI.h, module.modulemap}`
2. `mise run ios-dev-init`
    - `xcodegen generate --spec project.yml --project .` (inside `mobile/ios/`)
    - `pod install --repo-update` (inside `mobile/ios/`)
3. `mise run ios-build`
    - `xcodebuild -project mobile/ios/Livtet.xcodeproj -scheme Livtet -configuration Debug -sdk iphonesimulator -destination 'platform=iOS Simulator,id=<UDID>' build`

## Debugging tips

| Task                                    | Command                                                                                     |
| --------------------------------------- | ------------------------------------------------------------------------------------------- |
| Lint Swift code                         | `mise run ios-lint`                                                                         |
| Run unit tests                          | `mise run ios-test`                                                                         |
| Regenerate UniFFI Swift bindings        | `mise run ios-build` (bindings are regenerated as part of every `ios-build`)               |
| Rebuild xcframework                     | `mise run ios-build --clean` (deletes `mobile/ios/LivtetKit.xcframework/` first)            |
| Open project in Xcode                   | `open mobile/ios/Livtet.xcodeproj`                                                          |
| Stream Console / device logs            | `mise run ios-log-watch` (or `xcrun simctl spawn booted log stream --predicate 'subsystem == "net.olamaelcu.livtet"'`) |

### Common build failures

- **`Module 'LivtetKit' not found`** — run `mise run ios-build` after a
  Rust-side change. The task regenerates the UniFFI Swift bindings
  into `mobile/ios/LivtetKit/Sources/` before invoking Xcode.
- **`Library not found: -llivtet_ffi`** — delete
  `mobile/ios/LivtetKit.xcframework/` then run `mise run ios-build
  --clean` to regenerate, then rebuild.
- **`xcodebuild` complains about the project path** — make sure the
  on-disk directory is spelled `Livtet.xcodeproj` (six-letter prefix,
  no `e` between `Livt` and `et`). If you see `LIVTET.xcodeproj` or
  any other variant you've hit the APFS case-folding issue
  documented in ADR 0017; the fix is a clean `git clean -fd` followed
  by `mise run ios-dev-init`.

## Privacy and consent

The consent model is not yet wired end-to-end. The Info.plist declares
the user-facing
`NSUserTrackingUsageDescription`,
`NSLocalNetworkUsageDescription`,
`NSCameraUsageDescription`, and `NSBonjourServices`
(`_livtet-sync._tcp`) entries iOS surfaces when network code requests
local-network access at runtime, or when the Settings tab's QR scanner
opens the camera. The actual first-launch consent dialog and the
Sentry/analytics wiring have not yet been reintroduced. Sentry is
initialised in `LivtetApp.init` whenever `BuildConfig.sentryDSN` is
non-empty; the generated `BuildConfig.generated.swift` shipped with
this repo currently carries a DSN, so crash telemetry IS being sent
from this build. Until the consent dialog ships there is no opt-in
surface.

## Project layout

```
mobile/ios/
├── Brewfile                        Homebrew dependency pins (minisign, …)
├── Gemfile                         Bundler manifest (slather for coverage)
├── Livtet/                         App target source (see below)
├── LivtetJigsaw/                   Local SPM package — jigsaw puzzle engine
├── LivtetKit/                      Local SPM package — FFI bindings + Reader
├── LivtetUnitTests/                XCTest unit-test target
├── Livtet.xcodeproj/               Generated by xcodegen; do not hand-edit
├── Podfile                         CocoaPods manifest (placeholder; no pods)
├── project.yml                     XcodeGen spec — edit this, not the .xcodeproj
├── README.md                       This file
├── docs/                           iOS-local notes (currently empty)
└── tools/                          Auxiliary Swift packages (e.g. CoverageThreshold)

mobile/ios/Livtet/
├── LivtetApp.swift                 @main entry point — hosts SplashScreenView
├── Models/                         Plain-Swift data models (SyncModels,
│                                   GoogleBooksModels, PairingDecision, …)
├── Resources/
│   ├── Assets.xcassets/            AppIcon, Logo, dynamic surface/text/semantic
│   │                               colorsets, LucideIcons imageset (regenerated by
│   │                               `mise run design-tokens`)
│   ├── Fonts/                      *.ttf shipped with the bundle (regenerated by
│   │                               `mise run design-tokens`)
│   ├── BuildConfig.generated.swift GENERATED by `mise run secrets-ios-buildconfig`
│   │                               — holds googleAPIKey + sentryDSN
│   ├── Info.plist                  Bundle id, orientations, UIAppFonts, Bonjour
│   │                               keys, NSCameraUsageDescription, …
│   └── overdrive-libraries.json    Bundled library catalog
├── Services/
│   ├── AppError.swift              Domain error types
│   ├── FFIErrorBridge.swift        MobileError → AppError mapping
│   ├── LivtetCoreBridge.swift      UniFFI bridge facade (livtetFfiInit, dashboard, library, …)
│   └── LivtetCoreBridge+Wizard.swift  Wizard-specific bridge surface
├── Utilities/
│   ├── LivtetRadius.swift          Corner radius tokens (LivtetRadius.s/m/l)
│   └── LucideIcons.swift           Symlink into the design-tokens build output
├── ViewModels/
│   ├── AddBookWizardViewModel.swift  Add-book flow state
│   ├── DashboardViewModel.swift      MVVM view-model for the Dashboard tab
│   ├── DuplicateManagementViewModel.swift  Duplicate merge state
│   ├── LibraryViewModel.swift        MVVM view-model for the Library tab
│   └── SetCoverViewModel.swift       Set-cover sheet state
└── Views/
    ├── AddBook/                    AddBookWizardView + per-step sub-views
    ├── Components/                  Shared widgets (GreetingCard, StatsRow,
    │   QuickActionCard, ContinueReadingCard, RecentSearchesRow,
    │   FeedPlaceholderCard, ErrorBanner) + Pairing/ sub-folder
    ├── DashboardView.swift          Root dashboard tab
    ├── DuplicateDetailView.swift    Single duplicate work detail
    ├── DuplicateManagementView.swift Duplicate merge list
    ├── DuplicateMergeConflictView.swift  Field-level conflict resolver
    ├── Edition/                     SetCoverSheet
    ├── Library/                     LibraryView + EditionRow + EmptyStateQuoteView
    │                                 + LibraryFilterSheet
    ├── Puzzle/                      PuzzleTabView + NewPuzzleSheet + ViewModel
    ├── Reading/                     ReadingProgressControl
    ├── RootTabView.swift            Tab container (Dashboard/Library/Puzzle/Feed/Settings)
    ├── Settings/                    SettingsView + QRScannerView +
    │                                 ManualPairingSheet + OverdriveLibraryPicker
    └── SplashScreenView.swift      Loads the Rust database via livtetFfiInit()

mobile/ios/LivtetUnitTests/
├── AddBookWizardViewModelTests.swift
├── AddBookWizardViewTests.swift
├── DashboardViewModelTests.swift   Unit tests for the Dashboard view-model
├── DeviceIdentityServiceTests.swift
├── DiscoveryServiceTests.swift     Bonjour browse state machine
├── DuplicateManagementViewModelTests.swift
├── KeychainServiceTests.swift      sync_pair_token storage
├── KeychainServiceIntegrationTests.swift
├── LibraryViewModelTests.swift
├── PairingSheetSnapshotTests.swift
├── PiiScrubberTests.swift          Sentry beforeSend PII allowlist
├── SplashScreenViewTests.swift     Boot flow / database init tests
├── SyncManagerTests.swift          Sync state machine
├── URLParserLivtetSyncTests.swift  Sync URL deep-link parsing
├── Mocks/
│   ├── MockLibraryBridge.swift
│   └── MockWizardBridge.swift
└── Snapshots/
    ├── AddBookWizardSnapshotTests.swift
    └── DashboardSnapshotTests.swift  Pixel snapshot tests for each dashboard
                                      component (see "Snapshot tests" below)

### Snapshot tests

The dashboard component snapshot tests live in
`LivtetUnitTests/Snapshots/DashboardSnapshotTests.swift` and the
add-book wizard pixel tests live in
`LivtetUnitTests/Snapshots/AddBookWizardSnapshotTests.swift`. Both
depend on the `pointfreeco/swift-snapshot-testing` SPM package, which
IS already declared in `mobile/ios/project.yml`'s `packages:` block and
added to the `LivtetUnitTests` target's `dependencies:` (pinned at
1.18.0 with `swift-custom-dump` 1.3.3 and `xctest-dynamic-overlay` 1.3.0).

To record golden images:

```bash
# First run writes goldens into LivtetUnitTests/Snapshots/__Snapshots__/
SNAPSHOT_RECORD=true mise run ios-test
# Commit the goldens, then re-run without the env var.
```

## Note on directory naming

The iOS project uses the directory name `Livtet` (six-letter prefix,
L-i-v-t-e-t) for both the inner source directory and the unit-tests
directory. This is documented in `AGENTS.md` and matches the bundle id
`net.olamaelcu.livtet`. macOS APFS is case-INSENSITIVE by default, which
makes case-only renames fragile; see ADR 0017 §"APFS case-folding
workaround" for the details and the workaround applied to the
`LivtetKit.xcframework` path.
