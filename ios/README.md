# Livtet iOS app

Native iOS client for the Livtet library desktop app. SwiftUI + UniFFI
bindings into the Rust core (`crates/ffi/livtet-ffi/`). The iOS app and
the desktop Tauri app share the same Rust database layer; the iOS app
sees it through a generated Swift bridge.

See [ADR 0017](../../docs/reference/adr/0017-ios-app-architecture.md)
for the architectural rationale behind the dependency split,
deployment target, and APFS case-folding workaround.

## What users can do today

The app currently ships two functional tabs plus a placeholder Feed
tab, a polished boot flow, and a unified design system. The Dashboard
and Library tabs work end-to-end against the local Rust database; the
Feed tab is a stub for a future social / recommendations surface.

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
  `Ym`). A row of conditional quick-action cards nudges first-run
  goals — *Add Your First Book* (under 10 books), *Record Your
  Reading* (no first reading yet, or under 14 days in), *Finish a
  Book* (no finished books yet) — each one jumping straight to the
  Library tab. A brand-tinted *Continue Reading* card surfaces the
  most recently read book with a percent meter, and a *Recent
  Searches* row sits below. Pull-to-refresh and an inline retry banner
  cover transient failures.
- **Library tab.** A scrollable list of every book in the local
  database, with a toolbar that offers a **Sort** menu (Newest first
  / Oldest first), a **Filter** button that opens a modal sheet, and
  an **Add** button. The filter sheet exposes a segmented sort picker
  and horizontally-scrolling chip rows for format and language
  (language chips carry an optional flag-emoji prefix); chip changes
  are coalesced through a 150 ms debounce so the list never flashes
  between intermediate states. The empty state renders a literary
  pull-quote drawn from `data/quotes/empty.txt` next to the *Add
  Book* CTA. Pull-to-refresh and an inline error banner are wired
  identically to the Dashboard.
- **Design system.** Brand color tokens, surface / text / semantic
  palettes, and corner-radius tokens (`LivtetRadius.s/m/l`) drive
  both tabs. The app ships Work Sans (heading), Geist (body), and
  JetBrains Mono (code), plus the Lucide icon set generated from the
  shared design-tokens source. Dark mode is supported out of the box;
  on iPad the tab bar adapts into a sidebar via `.sidebarAdaptable`
  (iOS 18+).
- **Local storage.** Books, editions, authors, formats, languages,
  work-status values, reading-progress rows, and recent searches all
  live in a single SQLite database at `livtet.dat` inside the app's
  Application Support directory (`~/Library/Application Support/net.olamaelcu.livtet/`) —
  created on first launch and persisted across reinstalls under the
  device's normal app-container backup rules.

### Wired but not yet finished

- **Add Book wizard.** The Library tab's toolbar `+` button and the
  empty-state *Add Book* CTA both surface an alert that reads *"The
  Add Book wizard ships in a follow-up change."* The underlying FFI
  calls (`createBook`, `createEdition`, `findOrCreateAuthor`,
  `linkWorkAuthor`) are already wired through `LivtetCoreBridge`, so
  the wizard slots in without touching the data layer.
- **Status filter chips.** The filter sheet already shows a *Status*
  section with chips for every distinct work-status value in the
  library, but tapping a chip only mutates the view-model's published
  set — `LibraryViewModel.fetchBooks()` does not forward the
  selection to the FFI. The section header reads *"Coming soon"*;
  the underlying status predicate is the next deferred feature.
- **Feed tab.** Selecting the *Feed* tab shows a single *"Coming
  soon"* panel; a *Feed* placeholder card also appears at the bottom
  of the Dashboard so the user understands what will live there.
- **First-launch consent dialog.** The consent data model, the
  persistence layer, and the Info.plist permission strings
  (`NSUserTrackingUsageDescription`, `NSLocalNetworkUsageDescription`,
  `NSBonjourServices = _livtet-sync._tcp`) are all in place, but the
  actual first-launch dialog and the Sentry / analytics wiring have
  not been reintroduced. Sentry is initialised only when
  `BuildConfig.sentryDSN` is non-empty; until the dialog ships, no
  telemetry is sent. See *Privacy and consent* below for the
  implementation-facing details.

### Hot reload support (injection for SwiftUI)

The iOS app supports **InjectionIII** for rapid SwiftUI development. This
allows you to modify views and see changes instantly without restarting the
simulator.

To set up:

1. **Download InjectionIII.app:**
   - Visit <https://github.com/hunbase/InjectionIII/releases>
   - Download the latest `InjectionIII.app.zip`
   - Extract to `~/Applications/InjectionIII.app`

Then use it:

1. Open `Livtet.xcworkspace` in Xcode
2. Select an iPhone simulator
3. **Run the app** (⌘R) — ensure you don't check "Wait for Debuggers"
4. Make changes to SwiftUI views and press **⌘S** or click the
   Injection icon in the menu bar

**Important:** Injection only works in Debug configuration. It is enabled
via `#if DEBUG` conditional compilation and uses the Inject SPM package,
which is a no-op in Release builds.

**How it works:**

- The Inject package provides `@ObserveInjection` property wrappers and
  `.enableInjection()` view modifiers
- A bundle is loaded at launch from `/Applications/InjectionIII.app/Contents/Resources/iOSInjection.bundle`
- Code changes trigger live reloads of views in the running simulator

## Prerequisites

| Tool                     | Version | Installed via                                                        |
| ------------------------ | ------- | -------------------------------------------------------------------- |
| Xcode                    | 26+     | App Store                                                            |
| Xcode Command Line Tools | latest  | `xcode-select --install`                                             |
| CocoaPods                | 1.16+   | `gem install cocoapods` (via mise gem)                               |
| Ruby                     | 3.3.6   | `mise install ruby@3.3.6`                                            |
| Rust (stable)            | 1.94+   | `mise install rust@1.94`                                             |
| `uniffi-bindgen`         | latest  | `cargo install uniffi --features cli` (via `mise exec cargo:uniffi`) |
| `xcodegen`               | latest  | `brew install xcodegen` (mise-installed)                             |
| SwiftLint                | latest  | `brew install swiftlint` (mise-installed)                            |
| `mise`                   | latest  | <https://mise.jdx.dev>                                               |

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
mise run ios-ipad     # alias for `mise run ios-run-simulator --ipad` (iPad 9th gen)
```

If you change `mobile/ios/project.yml` (XcodeGen spec) or
`mobile/ios/Podfile`, re-run `mise run ios-dev-init`. If you change the
Rust side of `crates/ffi/livtet-ffi/`, run `mise run ios-bindings` before re-building
(the native libraries are rebuilt automatically by `mise run ios-build`).

## Where the Rust core lives and how bindings flow

```
crates/
├── livtet-ffi/              ← Rust crate, #[uniffi::export] functions
├── livtet-ffi-types/        ← UniFFI type mappings (shared with Android)
└── livtet-core/             ← entities, migrations, business logic

mobile/ios/
├── Livtet/                  ← SwiftUI app (Models, Services, Utilities, Views)
├── LivtetKit/               ← local SPM package (Products: LivtetKit,
│                              LivtetKitFFI, LivtetKitFFI_types)
├── LivtetKit.xcframework/   ← static lib slices (device + sim fat),
│                              produced by `mise run ios-build`
├── Livtet.xcodeproj/        ← generated by xcodegen; not for hand-editing
├── Livtet.xcworkspace/      ← open this in Xcode
├── Pods/                    ← CocoaPods (RxSwift, Alamofire, Moya, …)
└── Podfile                  ← CocoaPods manifest
```

Build pipeline:

1. `mise run ios-build` (or `mise run ios-bindings` separately for Swift bindings)
    - Builds Rust FFI (`cargo build -p livtet-ffi --target ...`)
    - Creates fat library for simulator via `lipo -create`
    - Packaging into `mobile/ios/LivtetKit.xcframework/`
2. `mise run ios-bindings`
    - Run `uniffi-bindgen generate --library .../liblivtet_ffi.a --language swift`
    - Drop the generated `livtet_ffi.swift` and C headers into
      `mobile/ios/LivtetKit/Sources/{LivtetKitFFI,LivtetKitFFI_types,livtet_ffiFFI,livtet_ffi_typesFFI}/`
3. `mise run ios-dev-init`
    - `xcodegen generate --spec mobile/ios/project.yml --project mobile/ios`
    - `pod install` (inside `mobile/ios/`)
4. `mise run ios-build`
    - `xcodebuild -workspace mobile/ios/Livtet.xcworkspace -scheme Livtet -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build`

## Debugging tips

| Task                                    | Command                                                                                     |
| --------------------------------------- | ------------------------------------------------------------------------------------------- |
| Lint Swift code                         | `mise run ios-lint`                                                                         |
| Run unit tests                          | `mise run ios-test`                                                                         |
| Clean build artifacts                   | `mise run ios-clean`                                                                        |
| Regenerate UniFFI Swift bindings        | `mise run ios-bindings`                                                                     |
| Rebuild xcframework                     | Delete `mobile/ios/LivtetKit.xcframework/` then run `mise run ios-build`                    |
| Open project in Xcode                   | `open mobile/ios/Livtet.xcworkspace`                                                        |
| Stream Console / device logs            | Use Xcode → Window → Devices and Simulators, or `xcrun simctl spawn booted log stream`      |

### Common build failures

- **`Module 'LivtetKit' not found`** — run `mise run ios-bindings` after
  a Rust-side change, then `mise run ios-dev-init`, then rebuild.
- **`Library not found: -llivtet_ffi`** — delete `mobile/ios/LivtetKit.xcframework/`
   then run `mise run ios-build` to regenerate, then clean and rebuild.
- **`xcodebuild` complains about the xcworkspace path** — make sure the
  on-disk directory is spelled `Livtet.xcworkspace` (six-letter prefix,
  no `e` between `Livt` and `et`). If you see `LIVTET.xcworkspace` or
  any other variant you've hit the APFS case-folding issue
  documented in ADR 0017; the fix is a clean `git clean -fd` followed by
  `mise run ios-dev-init`.

## Privacy and consent

The consent model is not yet wired end-to-end. The Info.plist declares
the user-facing
`NSUserTrackingUsageDescription`,
`NSLocalNetworkUsageDescription`, and `NSBonjourServices`
(`_livtet-sync._tcp`) entries iOS surfaces when network code requests
local-network access at runtime. The actual first-launch consent dialog
and the Sentry/analytics wiring have not yet been reintroduced; until
they are, no telemetry is sent from this app.

## Project layout

```
mobile/ios/
├── Gemfile                         Bundler manifest (slather for coverage)
├── Livtet/                         App target source (see below)
├── LivtetUnitTests/                XCTest unit-test target
├── Livtet.xcodeproj/               Generated by xcodegen; do not hand-edit
├── Livtet.xcworkspace/             Open this in Xcode
├── Podfile                         CocoaPods manifest
├── project.yml                     XcodeGen spec — edit this, not the .xcodeproj
├── README.md                       This file
└── tools/                          Local CocoaPods plugins / scripts

mobile/ios/Livtet/
├── LivtetApp.swift                 @main entry point — hosts SplashScreenView
├── Resources/
│   ├── Assets.xcassets/            AppIcon, Brand colorset, dynamic surface/text/semantic
│   │                               colorsets, Logo, LucideIcons dataset
│   │                               (regenerated by `mise run design-tokens`)
│   ├── Fonts/                      *.ttf shipped with the bundle (regenerated by
│   │                               `mise run design-tokens`)
│   └── Info.plist                  Bundle id, orientations, UIAppFonts, Bonjour keys
├── Services/
│   ├── AppError.swift              Domain error types
│   ├── FFIErrorBridge.swift        MobileError → AppError mapping
│   └── LivtetCoreBridge.swift       UniFFI bridge facade (livtetFfiInit, dashboard, etc.)
├── Utilities/
│   ├── BrandColor+Extension.swift  GENERATED by design-tokens — do not hand-edit
│   ├── BrandFont+Extension.swift   GENERATED by design-tokens — do not hand-edit
│   ├── LivtetRadius.swift          Corner radius tokens (LivtetRadius.s/m/l)
│   └── LucideIcons.swift           GENERATED by design-tokens — do not hand-edit
├── ViewModels/
│   └── DashboardViewModel.swift    MVVM view-model for the Dashboard tab
└── Views/
    ├── Components/
    │   ├── GreetingCard.swift
    │   ├── StatsRow.swift
    │   ├── QuickActionCard.swift
    │   ├── ContinueReadingCard.swift
    │   ├── RecentSearchesRow.swift
    │   ├── FeedPlaceholderCard.swift
    │   └── ErrorBanner.swift
    ├── DashboardView.swift          Root dashboard tab
    ├── RootTabView.swift            Tab container (Dashboard/Library/Feed)
    └── SplashScreenView.swift      Loads the Rust database via livtetFfiInit()

mobile/ios/LivtetUnitTests/
├── DashboardViewModelTests.swift   Unit tests for the Dashboard view-model
├── DiscoveryServiceTests.swift     Bonjour browse state machine
├── KeychainServiceTests.swift      sync_pair_token storage
├── OPDSCatalogStoreTests.swift     Catalog persistence
├── PiiScrubberTests.swift          Sentry beforeSend PII allowlist
└── Snapshots/
    └── DashboardSnapshotTests.swift  Pixel snapshot tests for each dashboard
                                      component (requires SnapshotTesting SPM
                                      dependency; see "Snapshot tests" below)

Note: a number of unit tests (Discovery, Keychain, OPDS, PiiScrubber)
reference services that are not yet present in `Livtet/Services/`; they
will not link until those services are reintroduced. Treat the test
target as a guide for what to rebuild.

### Snapshot tests

The dashboard component snapshot tests live in
`LivtetUnitTests/Snapshots/DashboardSnapshotTests.swift`. They depend on
the `pointfreeco/swift-snapshot-testing` SPM package, which is **not**
currently in `mobile/ios/project.yml`'s `packages:` block because the SPM
resolver in the current environment cannot reconcile its transitive
`swift-custom-dump` dependency against the available version range.

To enable snapshot tests:

1. Add the package to `mobile/ios/project.yml`:

   ```yaml
   packages:
     SnapshotTesting:
       url: https://github.com/pointfreeco/swift-snapshot-testing
       revision: "1bc16f430d8410e7f087d4c787767b26fd32fe30" # 1.19.3
   ```

2. Add it to the `LivtetUnitTests` target's `dependencies:`.

3. Run `xcodegen generate --spec mobile/ios/project.yml --project mobile/ios`.

4. Run the tests with `SNAPSHOT_RECORD=true` on first run to write
   golden images into `LivtetUnitTests/Snapshots/__Snapshots__/`,
   then commit the golden images and re-run without the env var.
```

## Note on directory naming

The iOS project uses the directory name `Livtet` (six-letter prefix,
L-i-v-t-e-t) for both the inner source directory and the unit-tests
directory. This is documented in `AGENTS.md` and matches the bundle id
`net.olamaelcu.livtet`. macOS APFS is case-INSENSITIVE by default, which
makes case-only renames fragile; see ADR 0017 §"APFS case-folding
workaround" for the details and the workaround applied to the
`LivtetKit.xcframework` path.
