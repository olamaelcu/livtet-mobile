# iOS verification — LivtetKit FFI wiring

This file is both a runbook and a prompt. A macOS agent picks it up, performs
the verification (including the deferred iOS app port), then **edits the
Status table below in a follow-up commit** so we can confirm iOS is genuinely
wired to the revived `livtet-ffi` and not just assumed to be.

Do not mark anything green by reading source alone: the point of this pass is
to compile and run on a Mac.

## Status

| Field | Value |
| --- | --- |
| Last verified | _pending_ |
| Branch | `feat/livtet-ffi-golden-tests` |
| Mobile commit | `803d2f3` (later when this doc is updated) |
| core submodule | `9889d72` |
| Toolchain (Xcode / rust / uniffi) | _pending_ |
| `mise run ios-build` | _pending_ |
| iOS app ported to `LivtetStore` | _pending_ |
| `FfiGoldenScenarioTests` | _pending_ |
| Deviations / follow-ups | _pending_ |

## Context

`livtet-ffi` was revived as a UniFFI 0.32 proc-macro crate that exposes a
single object facade, `LivtetStore`, with native `async` methods. Its surface
reaches the whole workspace through `livtet-core` alone (which forwards a
`uniffi` feature to `livtet-types` and `livtet-search`).

Bindings split into three components, each with its own Kotlin package and C
bridge:

| Component | Kotlin package | Swift source | C module |
| --- | --- | --- | --- |
| `livtet-ffi` | `net.olamaelcu.livtet.ffi` | `livtet_ffi.swift` | `livtet_ffiFFI` |
| `livtet-types` | `net.olamaelcu.livtet.types` | `LivtetTypes.swift` | `LivtetTypesFFI` |
| `livtet-search` | `net.olamaelcu.livtet.search` | `LivtetSearch.swift` | `LivtetSearchFFI` |

Swift has no equivalent of Kotlin's `external_packages`, so **all three
generated `.swift` files compile into one module, `LivtetKit`**, alongside the
hand-written shims. Consumers `import LivtetKit`.

Android is already migrated (`android/.../Bridge.kt`); iOS is not. The old
free-function surface is gone: there is no `init`, `isInitialized`,
`listBooks`, `getBook`, `createBook`, `createEdition`, `syncOnce`, plugin
lookup, or `PairedDeviceMobile`. Everything goes through `LivtetStore`.

## Preconditions

- macOS with Xcode 16+ and command-line tools.
- `mise install` (installs rust 1.97, `cargo:uniffi` 0.32.1, shellcheck, shfmt).
- Rust targets: `rustup target add aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios`.
- Submodule present and pinned:
  ```
  git submodule update --init --recursive
  git -C core rev-parse --short HEAD   # expect 9889d72
  ```
- First-time only: `mise run ios-dev-init`.

## Step 1 — regenerate bindings and build the XCFramework

```
mise run ios-build --sim
```

Expect exit 0 and, after the run:

- `ios/LivtetKit.xcframework/` exists.
- Generated sources landed in the package:
  - `ios/LivtetKit/Sources/LivtetKit/livtet_ffi.swift`
  - `ios/LivtetKit/Sources/LivtetKit/LivtetTypes.swift`
  - `ios/LivtetKit/Sources/LivtetKit/LivtetSearch.swift`
  - `ios/LivtetKit/Sources/livtet_ffiFFI/{livtet_ffiFFI.h,module.modulemap}`
  - `ios/LivtetKit/Sources/LivtetTypesFFI/{LivtetTypesFFI.h,module.modulemap}`
  - `ios/LivtetKit/Sources/LivtetSearchFFI/{LivtetSearchFFI.h,module.modulemap}`

The generated `.swift` files are build artifacts — never hand-edit them.

## Step 2 — port the iOS app bridge (the deferred work)

This is the part that has not been done yet and is why this doc exists.

1. Find the stale references:
   ```
   grep -rn 'LivtetKitFFI\|livtetFfi' ios --include='*.swift' --include='*.pbxproj'
   ```
2. `ios/LivtetKit/Sources/LivtetKit/FFIBridge.swift` is the old free-function
   bridge (`livtetFfiInit`, `livtetFfiListBooks`, …). Rewrite or delete it and
   move callers onto `LivtetStore`.
3. Rebase `ios/Livtet/Services/LivtetCoreBridge.swift` (and
   `LivtetCoreBridge+Wizard.swift`, `FFIErrorBridge.swift`, the view models,
   and the `LivtetUnitTests` mocks) on `LivtetStore`:
   - open: `LivtetStore.open(dbPath:indexDir:)` (async, throws).
   - close: `store.shutdown()` (not `close`).
   - create/seed: `seedSampleData(numWorks:)` / `resetAndSeed(numWorks:)`.
   - dashboard: `getDashboardStats()`, `getRecentlyReadBooks(limit:)`,
     `getRecentSearches(limit:)`.
   - filters: `listFormats()`, `listLanguages()`,
     `listWorksFiltered(filters:offset:)`, `countWorksFiltered(filters:)`.
   - `DashboardStats.firstReadingAt` is an RFC 3339 `String?` — convert to
     whatever the UI needs at the boundary (Android parses to epoch millis).
4. Delete `import LivtetKitFFI`: after the merge the generated types live in
   `LivtetKit` itself. Type-only references (`PairedDeviceMobile`,
   `InstalledPluginMobile`, sync/plugin methods) must either be removed or
   re-backed by whatever core exposes today — do not leave them dangling.
5. `import LivtetKit` is the only module import needed.

## Step 3 — run the golden test and the app

```
mise run ios-test
mise run ios-run-simulator
```

`ios/LivtetUnitTests/FfiGoldenScenarioTests.swift` exercises the store
lifecycle through reading lists and must pass.

## Step 4 — update this document and commit

Fill in the Status table above with the mobile commit SHA, the toolchain
versions, the outcome of each step, and any deviations. Then:

```
git add docs/IOS_VERIFICATION.md
git commit -m "docs: iOS verification results for the LivtetKit FFI wiring"
```

## Definition of done

- `mise run ios-build` succeeds on a simulator slice.
- `grep -rn 'LivtetKitFFI' ios` returns nothing.
- The iOS app compiles and launches against `LivtetStore`.
- `mise run ios-test` is green, including `FfiGoldenScenarioTests`.
- The Status table is filled in and committed.
