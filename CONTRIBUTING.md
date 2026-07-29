# Contributing

Thanks for your interest in Livtet. This guide covers how to file
issues, the development workflow, and the rules of engagement for each
part of the codebase. Read it before opening a pull request.

## Code of Conduct

All participants are expected to follow the spirit of the
[Contributor Covenant](https://www.contributor-covenant.org/version/2/1/code_of_conduct/).
Be patient, be kind, assume good faith, and leave the codebase in a
better shape than you found it.

## Filing issues

Use the issue tracker for the right repository:

- **This repository** — anything that lives under `android/` or `ios/`,
  the build glue (`.mise.toml`, `scripts/`, `fastlane/`), or the
  coordination between the mobile clients and the `core/` submodule.
- **The `core/` repository** — bugs or feature requests in the shared
  Rust workspace (`livtet-core`, `livtet-ffi`, `livtet-search`,
  `livtet-sync`, `livtet-ffi-types`, `livtet-types`, `livtet-database`,
  `livtet-plugin`, `livtet-cover`, the `livtet-lua-*` crates, …).
  The submodule points at <https://github.com/olamaelcu/livtet>.

A useful issue includes: the device and OS version (Android: API level
+ manufacturer; iOS: OS version + device), the steps to reproduce, the
expected and actual behavior, and a log excerpt or screenshot. For FFI
bugs, also note which side of the boundary failed (Rust panic,
UniFFI-generated binding, or platform-side wrapper) and the commit of
the `core/` submodule you reproduced against.

## Working in a worktree

The repository's `.worktree/` directory is reserved for task isolation.
Always work in a worktree, never directly on `main`:

```bash
git worktree add .worktree/short-topic-name -b feat/short-topic-name
cd .worktree/short-topic-name
git submodule update --init --recursive
```

Why:

- The `core/` submodule pins a commit. Different tasks frequently need
  it checked out at different commits; a worktree keeps each branch's
  checkout independent.
- iOS Xcode DerivedData and Android `.cxx/` caches blow up the working
  tree. A worktree keeps the noise out of `main`.
- The CI runner provisions a worktree per PR; matching the workflow
  locally makes "works on my machine" almost meaningless.

Before removing a worktree, commit or stash everything in it
(`git status` clean) — the `.worktree/` rule forbids deleting an
uncommitted worktree.

## Commit messages

The project follows a lightweight Conventional Commits style with
DCO signoff.

- **Subject line** — imperative mood, ≤ 72 characters, no trailing
  period. Prefix with the area: `android: …`, `ios: …`, `core: …`,
  `mise: …`, `docs: …`, `ci: …`.
- **Body** — wrap at 72 columns; explain *why*, not *what*. Link
  relevant issues with `Refs #123` or `Closes #123`.
- **DCO signoff** — every commit must include a `Signed-off-by:` line.
  Use `git commit -s` to add it automatically. By signing off you
  certify the [Developer Certificate of Origin 1.1](https://developercertificate.org/).
- **AI attribution** — if an AI tool (Claude, Copilot, Cursor, …) made
  a non-trivial contribution to the change, add a `Co-Authored-By:`
  trailer naming the tool. The repo follows the model-attribution
  convention; see the `model-attribution` skill for the canonical
  trailer format per provider.

Example:

```
ios: ship injection-driven Add Book wizard stub

Switches the Library tab's "+" button from the placeholder alert to
the wizard entry point. The FFI calls are already wired through
LivtetCoreBridge; this commit only ships the SwiftUI surface and the
view-model state.

Refs #42
Signed-off-by: Jane Doe <jane@example.com>
Co-Authored-By: Claude <noreply@anthropic.com>
```

## Coding standards

Each part of the repository has its own contributor notes. Read the
relevant `AGENTS.md` before touching it:

- [android/AGENTS.md](./android/AGENTS.md) — Gradle module layout,
  product flavors, native library hygiene, Labs feature flags,
  baseline profile, generated design tokens.
- [ios/AGENTS.md](./ios/AGENTS.md) — the 6-letter product name
  (`Livtet`), APFS case-folding workaround, UniFFI Swift bindings,
  generated `Livtet.xcodeproj/`, xcframework layout.
- The `core/` submodule carries its own contributor docs (Rust style,
  workspace lints, mutation testing, SeaORM migrations).

A few conventions that apply across the whole repo:

- **No hand-edits to generated files.** UniFFI bindings
  (`android/build/generated/source/uniffi/kotlin/`,
  `ios/LivtetKit/Sources/{LivtetKitFFI,LivtetKitFFI_types,livtet_ffiFFI,livtet_ffi_typesFFI}/`),
  the iOS xcframework, the Android `.so` cache, and the Xcode project
  bundle are all build artifacts.
- **No secrets in commits.** Use SOPS for anything that would
  otherwise live in a `.env` file or `gradle.properties`. Pre-commit
  hooks scan for accidental secret leakage — see
  [Secrets (SOPS)](./GETTING_STARTED.md#7-secrets-sops) for the
  intended workflow.
- **No surprises in shared config.** Changes to `.mise.toml`,
  `scripts/`, `fastlane/`, `.cargo/config.toml`, and `mise.lock` are
  high-blast-radius. Discuss them in an issue first and call them out
  in the PR description.
- **No emojis in source files or commits.** Keep the diff readable in
  monochrome.

## Testing

- **Rust** — `cd core && cargo test --workspace` must pass before you
  open a PR. Mutation tests live under `cargo mutants` and run in CI
  on the `core/` repo; you do not need to run them locally unless your
  change is in a `mutants.* = "skip"` region.
- **Android** — `mise run android-test` runs the unit + instrumented
  test suites. Add an instrumented test under
  `android/app/src/androidTest/java/net/olamaelcu/livtet/` for any
  user-visible flow you add. The `:benchmarks` module is local-only;
  there is no CI runner for it.
- **iOS** — `mise run ios-test` runs the XCTest suite in
  `ios/LivtetUnitTests/`. Snapshot tests render to
  `ios/LivtetUnitTests/Snapshots/` (Swift source) and emit PNGs into
  the gitignored `__Snapshots__/` directory.
- **Linting** — `mise run android-lint` (ktlint + detekt + Android
  lint) and `mise run ios-lint` (SwiftLint, periphery). The CI
  pipeline will not merge a PR that fails either.

## Submitting a pull request

1. **Open an issue first** for non-trivial changes so the design can
   be discussed. Small bug fixes and typo corrections do not need an
   issue.
2. **Branch from `main`** (or the long-lived branch the issue
   references) inside a worktree, not from a fork you cannot push to.
3. **Keep the diff focused.** One concern per PR. If a fix has
   follow-up work, file a follow-up issue and link it from the PR.
4. **Update the docs that go with the change.** If you add an
   end-user-facing feature, update `android/README.md` or
   `ios/README.md`. If you add a build/lint task, update
   `.mise.toml` and reference it from `GETTING_STARTED.md`.
5. **Fill in the PR template** with:
   - What the change does and why.
   - How you tested it (commands, devices, screenshots for UI changes).
   - Anything reviewers should pay extra attention to.
   - The submodule SHA used for the Rust core (it should match the
     pinned commit, or call out the deviation explicitly).
6. **Wait for CI.** Two approvals are required for non-trivial
   changes; one is enough for typo / docs / generated-file PRs.

### Pre-PR checklist

- [ ] `git status` is clean (no stray files staged or unstaged)
- [ ] `git diff --stat` matches the PR description
- [ ] Commit messages follow the convention above and include
      `Signed-off-by:`
- [ ] AI contributions carry a `Co-Authored-By:` trailer
- [ ] `cd core && cargo test --workspace` passes (or you only touched
      non-Rust files)
- [ ] `mise run android-test` and `mise run android-lint` pass (or
      you only touched non-Android files)
- [ ] `mise run ios-test` and `mise run ios-lint` pass on macOS (or
      you only touched non-iOS files)
- [ ] No hand-edits to generated files
- [ ] No secrets, API keys, or `.env` content in the diff
- [ ] Docs updated to match the change
- [ ] Submodule SHA pinned (or the PR description explains why it
      moved)

## Working with the `core/` submodule

Most changes that touch Rust code live in the `core/` submodule, not
in this repository. The typical cross-repo flow is:

1. Open an issue or PR in <https://github.com/olamaelcu/livtet> for
   the Rust change. Land it on the `main` branch of the submodule.
2. In this repository, bump the submodule pointer to the new commit:
   ```bash
   cd core && git fetch && git checkout <new-sha> && cd ..
   git add core
   git commit -s -m "core: bump submodule to <new-sha>"
   ```
3. On a macOS worktree, regenerate the Swift bindings:
   `mise run ios-bindings`. On any worktree, regenerate the Kotlin
   bindings by running `mise run android-build`.
4. Land the regenerated bindings as **separate commits** so a partial
   merge cannot land one half of the change without the other. The PR
   description should reference both the `core/` change and the
   binding-regeneration commits.

If a change crosses both sides of the FFI boundary (e.g. you are
adding a new entity), it is usually cleaner to land the Rust side
first, then the bindings + app call sites together in this
repository.

## Release process (for maintainers)

Tag-driven, manual for now:

1. Pick the version. Bump `versionName` in `android/app/build.gradle.kts`
   and `CFBundleShortVersionString` / `CFBundleVersion` in
   `ios/project.yml` (XcodeGen regenerates `Info.plist`).
2. Run `mise run android-build --profile release --flavor playstore`
   and `mise run ios-build --profile release`. Verify the generated
   artifacts locally.
3. `mise run android-deploy` uploads to Play Console; Fastlane lanes
   under `fastlane/` push to App Store Connect and TestFlight.
4. Tag the release in this repository and in the `core/` submodule
   separately. The two are not version-locked.

## Questions

If something is unclear, open a draft PR or an issue and ask. The
"rules" above are not enforced by CI as a hard gate — they exist to
make review easier, not to gatekeep contributions.