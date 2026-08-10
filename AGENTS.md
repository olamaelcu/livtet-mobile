# Agent Guidelines for `mobile/`

Root of the Livtet mobile repository (`livtet-mobile`). This file governs
shared cross-platform behavior only. Most of the binding rules live in the
per-platform files — start there.

## Where the rules live

| Path                                       | What it governs                                                                   |
| ------------------------------------------ | --------------------------------------------------------------------------------- |
| [android/AGENTS.md](android/AGENTS.md)     | Android app — Kotlin / Compose / Gradle / FFI / Labs flags                         |
| [ios/AGENTS.md](ios/AGENTS.md)             | iOS app — SwiftUI / XcodeGen / UniFFI / xcframework / 6-letter form `Livtet`       |
| [CONTRIBUTING.md](CONTRIBUTING.md)         | Worktree workflow, commit message style, DCO signoff, AI attribution, secrets     |
| [README.md](README.md)                     | Repo overview, architecture diagram, quick start                                   |
| [GETTING_STARTED.md](GETTING_STARTED.md)   | Toolchain installation and the `core/` submodule                                   |
| `core/`                                    | Git submodule — Rust workspace; has its own `AGENTS.md` and `CONTRIBUTING.md`      |

Read the per-platform `AGENTS.md` first whenever you are about to touch code
under `android/` or `ios/`. They enumerate the commands, naming conventions,
generated artifacts, and binding rules that this file deliberately does not
duplicate.

## Cross-cutting rules

### Both apps are separate products
Neither `android/` nor `ios/` is a Tauri target, a "mobile build of Tauri",
or otherwise related to Tauri. Tauri configuration (`.browserslistrc`,
`tauri.conf.json`, `packages/livtet-tauri-web/`) is not present and is
irrelevant to the mobile builds.

### Work in a worktree
Always work in a `.worktree/` directory, never directly on `main`. See
[CONTRIBUTING.md](CONTRIBUTING.md#working-in-a-worktree) for the
`git worktree add` flow and the rationale (the `core/` submodule pins a
commit per branch; Android `.cxx/` and iOS DerivedData caches blow up the
working tree; the CI runner provisions a worktree per PR).

### Commit messages and DCO
- Subject: imperative mood, ≤ 72 characters, no trailing period. Prefix with
  the area: `android: …`, `ios: …`, `core: …`, `mise: …`, `docs: …`, `ci: …`.
- Body: wrap at 72 columns; explain *why*, not *what*.
- DCO signoff (`Signed-off-by:`) is required on every commit. Use
  `git commit -s` to add it.
- AI contributions carry a `Co-Authored-By:` trailer naming the tool (see
  the project's model-attribution convention).

See [CONTRIBUTING.md](CONTRIBUTING.md#commit-messages) for examples.

### Toolchain
[`mobile/mise.toml`](mise.toml) declares the full cross-platform toolchain.
`mise install` (or `direnv allow`) provisions it; OS gates select the right
subset per host (Linux → Android; macOS → iOS). SOPS, age, and the
per-platform tool lists are also pinned there.

### `core/` submodule
The shared Rust workspace lives in `core/` as a git submodule and is
versioned independently from this repository. Read `core/CONTRIBUTING.md`
and `core/AGENTS.md` before touching Rust code, and follow the "land Rust
first, regenerate bindings second" flow documented in
[CONTRIBUTING.md](CONTRIBUTING.md#working-with-the-core-submodule).

### Required Skills

- `guardrails` — applies to both per-platform contexts. Ask before running
  build / install / launch commands on either Android or iOS.
- `mise-tools` — applies when a `mise.toml`-declared binary returns
  `command not found` (for example `uniffi-bindgen` or the platform tools).
- `model-attribution` — applies to commit messages and DCO signoff lines.