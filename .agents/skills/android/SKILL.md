# Android Skill

How to build, install, and test the Android app in this repository.

Applies whenever a task touches `android/`. Every command below is a mise task;
reach for `gradle` directly only for something no task covers, and expect to
repeat what the task does (see the traps at the end).

There is no task literally named `android`. The one-command loop is
`android-run-emulator`; the rest are the `android-*` tasks listed here.

## First time on a machine

```bash
mise install                     # the toolchain mise.toml pins
mise run android-dev-init        # NDK/SDK cargo config + rust targets
mise run android-emulator --create
```

`android-dev-init` is what makes `core/` cross-compile without the shell's own
`ANDROID_HOME` leaking in; the build task also unsets `NDK_HOME`/`ANDROID_HOME`
for that reason.

## Tasks

| task | what it does | when |
|---|---|---|
| `android-run-emulator` | boots the phone emulator, installs KOReader, builds, installs | the one-command loop; slow because of KOReader |
| `android-build` | `cargo build` for the ABI, regenerates UniFFI bindings, assembles the APK | after any `core/` change, or a clean checkout |
| `android-install` | installs the **already built** APK and launches it | after `android-build` |
| `android-install-incremental` | Kotlin-only: `installDebug` + launch, no cargo/bindgen | the everyday loop while editing Kotlin |
| `android-test` | JVM unit tests; `--instrumented` adds the device suite | before a commit |
| `android-lint` | ktfmt + ktlint + detekt | before a commit |
| `android-emulator` | create/start/select the phone and tablet AVDs | device management |
| `android-sqlite` | builds a device `sqlite3` and can open the app's DB | inspecting the library |
| `android-clean` | removes generated files from `android/` | when the build is lying to you |

Common flags: `--flavor generic|playstore|fdroid` (default `generic`),
`--profile debug|release`, `--target emulator|device|all` on `android-build`,
`--device <serial>` on `android-install`, `--select phone|tablet` to pick an
emulator by name.

## The three loops

```bash
# first run, or after Rust/bindings changed
mise run android-build --flavor generic --target emulator
mise run android-install --flavor generic

# day to day, Kotlin only
mise run android-install-incremental --flavor generic

# everything at once, including booting a device
mise run android-run-emulator --flavor generic
```

## Flavours

| flavour | application id | notes |
|---|---|---|
| `generic` | `net.olamaelcu.livtet.generic` | default for development |
| `fdroid` | `net.olamaelcu.livtet.fdroid` | |
| `playstore` | `net.olamaelcu.livtet` | no suffix, so it coexists with `generic` on one device |

`android-build --flavor playstore` then `android-install --flavor playstore` is
the playstore path; the task launches
`net.olamaelcu.livtet/net.olamaelcu.livtet.MainActivity`.

## Tests

```bash
mise run android-test                          # JVM only
mise run android-test --instrumented           # JVM + device suite
mise run android-test --flavor all             # every flavour's JVM tests
```

`--instrumented` requires a running device and keeps the app installed
afterwards (`leaveApksInstalledAfterRun`), so the library and seeded state are
still there when you look at the screen. A raw
`gradle :app:connected…AndroidTest` does **not** do that: it uninstalls the app
and its data when the run ends.

Instrumented test methods cannot be backticked with spaces at this minSdk —
dex rejects the name before any test runs. camelCase, like the rest of the
suite.

## Looking at the result

```bash
adb exec-out screencap -p > /tmp/shot.png      # read the image
adb shell uiautomator dump /sdcard/ui.xml      # clickable bounds
adb shell am start -n net.olamaelcu.livtet.generic/net.olamaelcu.livtet.MainActivity
```

Take taps from `uiautomator`'s `clickable="true"` node, not from the text: a
text node's centre is often a few pixels outside the row's touch target, and
the tap silently does nothing.

## Traps

- **`android-install` does not build.** It installs
  `android/app/build/outputs/apk/$flavor/$profile/app-$flavor-$profile.apk`; a
  stale or missing APK is installed or refused, silently. Build first.
- **The first launch after an install spends ~30s opening the store** on an
  empty database (the Rust store plus its search index). A spinner is not a
  hang; wait before concluding anything.
- **Worktrees need two things main has for free**: `mise run secrets-decrypt`
  in the worktree, and `mobile/.worktree/branding` symlinked to
  `livtet-ecosystem/branding`, because `settings.gradle.kts` includes
  `../../branding/android`, which resolves outside the worktree.
  `android-build` depends on `secrets-export-env`, so without a decrypted
  `.mise/secrets.json` it stops there with "secrets.json missing" and never
  reaches cargo — a failure that looks unrelated to what you asked for.
- **Direct `gradle` needs `ANDROID_HOME`** set to the mise SDK
  (`mise where android-sdk`, e.g. `~/.local/share/mise/installs/android-sdk/23.0`),
  and a running device for `connected…AndroidTest`. The mise tasks do both.
- **`android-run-emulator` also downloads KOReader.** If you only need the
  app, run `android-emulator --start phone` first and then the build/install
  pair.
- **`android-install-incremental` never runs cargo or UniFFI.** After a change
  in `core/`, an incremental install ships old native code with new Kotlin.
