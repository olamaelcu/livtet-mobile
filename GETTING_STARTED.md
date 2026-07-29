# Getting Started

This guide walks you from a fresh clone to a running dev build of
either mobile client. Read the relevant [CONTRIBUTING.md](./CONTRIBUTING.md)
section and the per-platform `AGENTS.md` before opening a pull request.

## 1. Prerequisites

- **Git** 2.40+ with submodule support.
- **macOS** (Apple silicon or Intel) for iOS work; **Linux** is
  sufficient for Android-only work.
- **mise** — the [asdf-compatible version manager](https://mise.jdx.dev).
  Install with `curl https://mise.run | sh` or `brew install mise`.
- **direnv** (optional, but recommended) to load `.envrc` automatically.
- The platform SDKs and CLIs that your platform needs:
  - **Android** — Java 20, Kotlin 2.3.21, Gradle 8.14.5, Android SDK +
    NDK r27.1.12297006, ktlint 1.8.0. `mise install` provisions the
    entire toolchain and triggers `mise run android-dev-init` to fetch
    the SDK and NDK.
  - **iOS** — Xcode CLT, CocoaPods 1.16.2, SwiftLint 0.58.0, xcodegen
    2.42.0, Xcode 16+ for the iOS 18 sidebar APIs. `mise install`
    triggers `mise run ios-dev-init` after install.
- **SOPS** with an `age` key for decrypting secrets (Gradle signing,
  Sentry DSN, Google Books API key). See [Secrets](#7-secrets-sops)
  below.

`mise.toml` declares the exact versions. Do not hand-install Java or
Gradle; let mise manage them so paths and `JAVA_HOME` line up with the
task graph.

## 2. Clone the repository

```bash
git clone <repo-url> livtet-mobile
cd livtet-mobile
git submodule update --init --recursive
```

The `core/` submodule is a real checkout — `--recursive` picks up any
nested submodules the Rust workspace pulls in. If you cloned without
`--recursive`, run `git submodule update --init --recursive` from the
repo root.

Verify the workspace is sound:

```bash
mise trust && mise install    # provisions every tool listed in .mise.toml
direnv allow                   # loads .envrc (mise + direnv integration)
git submodule status          # both core/ entries should report a real commit
```

`mise install` may take a few minutes on first run — it downloads
Android SDK components and NDK archives.

## 3. Build the Rust core

The mobile clients do not call into `core/` directly; they consume a
curated FFI surface (`core/livtet-ffi`). Build and test it in
isolation:

```bash
cd core
cargo build --workspace --all-targets
cargo test  --workspace
```

`core/Cargo.toml` is a workspace member list. The four crates that the
mobile apps actually exercise are `livtet-core`, `livtet-types`,
`livtet-database`, `livtet-search`, and `livtet-ffi`. Default
`cargo test` runs those four plus anything else in the workspace.

If you change `core/livtet-ffi` (or its companion `livtet-ffi-types`),
regenerate the platform bindings before building the apps — see
[Regenerating UniFFI bindings](#6-regenerating-uniffi-bindings).

## 4. Build the Android app

Android toolchain setup is automatic after `mise install` (the
`postinstall` hook runs `mise run android-dev-init`).

```bash
mise run android-build          # native build + UniFFI bindings + APK
mise run android-install        # install on the booted device/emulator
mise run android-test           # unit + instrumented tests
mise run android-lint           # ktlint + detekt + Android lint
```

Useful shell aliases exposed by `[shell_alias]` in `.mise.toml`:

| Alias | Resolves to                          |
| ----- | ------------------------------------ |
| `aw`  | `mise run android-watch`             |
| `aii` | `mise run android-install-incremental` |
| `apk` | `mise run android-run-emulator` (boot + build + install) |

### Native library hygiene

Before any build that touches `core/livtet-ffi`, delete the cached
prebuilt `.so` files:

```bash
rm -rf mobile/android/app/src/main/jniLibs
mise run android-build
```

Stale `.so` files in `app/src/main/jniLibs/` produce confusing
"Library not found" or ABI mismatch errors. The directory is
gitignored.

### Product flavors

| Flavor      | applicationId suffix | Notes                                                        |
| ----------- | -------------------- | ------------------------------------------------------------ |
| `playstore` | (none)               | Default Google-aligned build; Sentry DSN injected from `LIVTET_SENTRY_DSN_MOBILE`. |
| `fdroid`    | `.fdroid`            | F-Droid build; empty Sentry DSN (no tracking).               |
| `generic`   | `.generic`           | Sideload build; empty Sentry DSN (no tracking).              |

To build a specific flavor: `mise run android-build --profile release --flavor playstore`.

## 5. Build the iOS app (macOS only)

Linux worktrees cannot run `ios-*` Mise tasks — treat them as
unavailable in that environment. From a macOS worktree:

```bash
mise run ios-dev-init     # xcodegen + pod install (one-time, after spec/dependency changes)
mise run ios-build        # Rust FFI static libs + xcframework + xcodebuild
mise run ios-bindings     # regenerate UniFFI Swift bindings (after Rust FFI changes)
mise run ios-run-simulator
mise run ios-test
mise run ios-lint
```

The build chain is strict:

1. `ios-bindings` runs `uniffi-bindgen` against `core/livtet-ffi` and
   writes Swift sources to
   `ios/LivtetKit/Sources/{LivtetKitFFI,LivtetKitFFI_types,livtet_ffiFFI,livtet_ffi_typesFFI}/`.
2. `ios-build` cross-compiles `core/livtet-ffi` for iOS device and
   simulator slices, packages them into `ios/LivtetKit.xcframework/`,
   then drives `xcodebuild`.

The xcframework and the generated binding sources are gitignored and
build artifacts. Do not hand-edit them.

### Project naming

Every iOS path component that contains the product name uses the
**6-letter form `Livtet`** (L I V T E T). The bundle identifier is
`net.olamaelcu.livtet`. There is no "Livapp" or "LivtetApp" spelling —
the 6-letter form is the only acceptable one for product name,
directory basenames, file basenames, the Xcode project bundle name, and
unit-test target names. See [ios/AGENTS.md](./ios/AGENTS.md) for the
canonical path list and the APFS case-folding workaround.

### Hot reload for SwiftUI

The iOS app supports InjectionIII for fast SwiftUI iteration in Debug.
Download `InjectionIII.app` from
<https://github.com/hunbase/InjectionIII/releases>, run it once, then
launch the app from Xcode with "Wait for Debugger" unchecked. Press
`⌘S` in the Injection menu bar to live-reload on save. Injection is
disabled in Release builds.

## 6. Regenerating UniFFI bindings

The Kotlin bindings at `android/build/generated/source/uniffi/kotlin/`
and the Swift bindings at
`ios/LivtetKit/Sources/{LivtetKitFFI,LivtetKitFFI_types,livtet_ffiFFI,livtet_ffi_typesFFI}/`
are produced by `uniffi-bindgen` from `core/livtet-ffi`. Treat them as
build artifacts:

1. Make the FFI change in `core/livtet-ffi` (and, if needed,
   `core/livtet-ffi-types`).
2. From the Android worktree:
   ```bash
   rm -rf mobile/android/app/src/main/jniLibs
   mise run android-build
   ```
   The `android-generate-bindings` task regenerates the Kotlin sources
   and the Android Gradle plugin repopulates the cached `.so` files.
3. From the macOS worktree:
   ```bash
   mise run ios-bindings
   mise run ios-build
   ```
4. Commit the FFI change in `core/` (push it to the submodule's
   branch) and the regenerated bindings in a **single commit per
   platform** so a partial merge never lands one half of the change.

## 7. Secrets (SOPS)

The repo uses [SOPS](https://github.com/getsops/sops) + `age` for
secrets (Gradle keystore password, Sentry DSN, Google Books API key,
Fastlane App Store Connect API key, etc.). The `.sops.yaml` rule file
declares which paths are encrypted and which age recipients may
decrypt them.

If you are a contributor without access to the project's age key, you
can still build the apps — the `fdroid` and `generic` Android flavors
ship without Sentry, and the iOS Debug configuration does not require
a release signing identity.

If you **do** have access:

```bash
# Your private age key must live outside the repo, e.g. at ~/.config/sops/age/keys.txt
export SOPS_AGE_KEY_FILE=~/.config/sops/age/keys.txt
mise run secrets-decrypt      # writes .mise/secrets.json (gitignored)
```

`secrets-ios-buildconfig` then writes
`ios/Livtet/Resources/BuildConfig.generated.swift` from the decrypted
bundle. Gradle reads `LIVTET_SENTRY_DSN_MOBILE`,
`LIVTET_STORE_FILE`, `LIVTET_STORE_PASSWORD`, `LIVTET_KEY_ALIAS`, and
`LIVTET_KEY_PASSWORD` from the environment or `~/.gradle/gradle.properties`.

Never commit the decrypted bundle. Never paste a key into a commit
message, a test fixture, or a public Slack thread.

## 8. Running the dev loop

A typical day looks like:

```bash
mise install                          # once per session, refreshes toolchains
cd core && cargo test                 # run core tests first; cheap, fast

# In one shell — the Android watcher
aw                                    # auto-installs the APK on save

# In another shell — incremental install after touching FFI
rm -rf android/app/src/main/jniLibs && aii

# On macOS — Xcode workspace
open ios/Livtet.xcworkspace
```

`mise watch` powers `android-watch`; it polls the Kotlin sources and
re-runs `android-install-incremental` on save.

## 9. Troubleshooting

A short list of things that trip up first-time contributors. Most of
these are already covered by the per-platform `AGENTS.md` files — read
them first.

- **`uniffi-bindgen: command not found`.** Run `mise install` (or
  `mise upgrade`) — the binary is declared in `.mise.toml`.
- **Stale Android `.so` files.** Delete
  `android/app/src/main/jniLibs` and rebuild. See [Native library
  hygiene](#native-library-hygiene).
- **Xcode complains about missing `LivtetKit.xcframework`.** Run
  `mise run ios-build`. The framework is generated from
  `core/livtet-ffi` and lives in the gitignored `ios/` directory.
- **iOS rename landed on a 7-letter directory.** macOS APFS is
  case-insensitive, so renaming to "Livtet" from a different case
  requires a two-step POSIX `mv` dance — see [ios/AGENTS.md](./ios/AGENTS.md)
  for the workaround.
- **`adb` does not see the emulator.** `mise run android-emulator
  --start phone` boots one; the `--start` flag is idempotent.
- **`gradle` is missing from PATH.** `mise run android-dev-init`
  exports the SDK + `ANDROID_HOME`; always launch Gradle through
  `mise run android-build` rather than a raw `gradle` invocation.
- **Submodule is empty.** `git submodule update --init --recursive`
  from the repo root. If `core/` already exists but is empty, also
  run `cd core && git submodule update --init --recursive`.

If something here is wrong or missing, open an issue or PR — the docs
live in this repository and are easy to keep honest.