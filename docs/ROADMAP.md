# Mobile Roadmap

The Livtet mobile apps: Android and iOS. Each is its own product UI; the only
shared layer is the Rust core in `core/` (`livtet-ffi` / `LivtetStore`), which
owns the store, the sync protocol, covers, and the EPUB reader engine.

The goals for the platform are:

- keep a local library on the device and read it in-app
- sync a library with paired desktops and phones, files and covers included
- hand books between paired devices through an inbox the reader decides on
- import local files and read their metadata
- render the platform's public book, review, and profile links natively

## Status legend

`[x]` done · `[~]` in progress · `[ ]` backlog

> **Layout note.** Android lives under `android/` (app + `android/core/*`
> library modules); iOS under `ios/` (`Livtet/` app, `LivtetKit/` UniFFI
> wrappers, `LivtetUnitTests/`). Both build the same `core/` submodule through
> `mise` tasks declared in [`mise.toml`](../mise.toml).

## Implemented

### Library (Android)

- [x] Home shelf wall — three-column grid of covers with title and author,
      initials placeholder while art resolves, and a shared top inset that
      includes the display cutout. `LibraryActivity.kt`, `Insets.kt`.
- [x] Shelves and named reading lists — browse, create, rename, and add books.
      `ShelvesActivity.kt`; reading lists in the core.
- [x] Catalog search over the local store, with answers cached on disk.
      `CatalogClient.kt`, `CatalogCache.kt`.
- [x] Cover art resolution: a device-local file when present, otherwise the
      edition's `cover_image_url`. A sync fills missing art from the peer
      (bounded: 24 editions per sync, 8 per request).
- [x] Bulk operations from the shelf wall — select several books, shelve them,
      or remove them. `LibraryActivity.kt` toolbar.

### Feed (Android)

- [x] Activity feed as cards, grouped by day, with conversational shelving runs
      collapsed into a single row. Tapping a book opens a catalog-hydrated list.
      `FeedActivity.kt`, `FeedTimeline.kt`, `FeedClient.kt`.
- [x] Feed answers cached on disk so a cold open does not refetch everything.

### Sync and pairing (Android)

- [x] Pair with a desktop or phone by scanning a QR ticket or typing the peer's
      node id; the peer's advertised name is carried into the pin.
      `PairingTicket.kt`, `NearbyPeers.kt`.
- [x] Library pull with a per-peer cursor; a paired peer pulls this device's
      own changes. `Bridge.kt`, `SyncActivity.kt`.
- [x] Offers inbox — a peer's offered book waits here until the reader accepts
      or declines; accepting downloads the file and cover and registers the
      book locally. `OffersActivity.kt`, `OfferPresentation.kt`.
- [x] Background worker — pulls every 15 minutes and on launch, and announces
      new offers in a notification. `SyncPoll.kt`, `LivtetApp.kt`.
- [x] Device files stay device-local: absolute file paths are never synced, so
      each side records only its own copy.
- [x] Reading progress syncs across paired devices — whole row (locator,
      progression, reading time), latest change version wins, and a phone
      advertises while the app is open once a device is paired. Verified end to
      end on 2026-11-08:
      [`docs/SYNC_VERIFICATION.md`](SYNC_VERIFICATION.md).
- [x] Visible polling — the app pulls every paired device every 30 s while it
      is on screen and posts a transient, silent "Sync complete — N change(s)"
      notification when a pass brings something; the 15-minute worker remains
      for the background.

### Reader (Android)

- [x] EPUB reader over the core `ReaderSession` (Readium engine), with a
      chapter list and reading-position persistence. `ReaderActivity.kt`,
      `ReaderDisplay.kt`, `android/core/reader`.
- [x] Notes on a passage, stored as annotations and listed from the chapter.
- [x] Notes surface in a pull-up sheet — All/This-chapter filters, per-row
      delete, and a preview card with Jump to passage; the header counts what
      is stored. Locators are points, so no in-page highlight yet.
- [x] Instrumented coverage for open/position/annotations and the FFI golden
      scenario. `android/app/src/androidTest`.

### Import (Android)

- [x] EPUB import with metadata read from the package, an ISBN normalized to
      `urn:isbn:…`, and a file registered in `digital_inventory`.
      `ImportBook.kt`, `EpubMetadataReader.kt`.
- [x] PDF import as a file (metadata is not parsed; the in-app reader is
      EPUB-only).

### Platform auth (Android)

- [x] Auth providers behind one account manager — Google, Apple, and ATProto —
      with the OAuth callback handled by the app's `livtet://oauth` link.
      `PlatformAuth.kt`, `android/core/auth`.

### iOS foundation

- [x] Launcher and splash screen with the brand treatment. `LauncherView.swift`,
      `SplashScreenView.swift`.
- [x] The core is wired: `LivtetCoreBridge` opens `LivtetStore` from the FFI
      bindings produced by `mise run ios-build`.
- [x] Unit tests for the FFI golden scenario, PII scrubbing, and the splash.
      `ios/LivtetUnitTests`.

## In progress

- [~] **iOS feature parity and verification.** iOS is a launcher today: it
      opens the store but has no library, feed, inbox, or reader UI. The
      verification runbook and its status table live in
      [`docs/IOS_VERIFICATION.md`](IOS_VERIFICATION.md); the table is still all
      `_pending_` because the compile-and-run pass requires macOS. The bridge
      already calls `LivtetStore`, so the remaining work is UI port plus the
      recorded verification.

## Backlog

- [ ] **iOS library and shelf wall** — port the three-column wall, bulk
      selection, shelves, and reading lists. Depends on the parity phase above.
- [ ] **iOS feed, inbox, and background sync** — activity cards, the offers
      decision flow, and the polling worker with notifications.
- [ ] **iOS EPUB reader** — chapter navigation, position sync, and annotations
      over the same core reader engine.
- [ ] **Android offers-inbox depth** — an offer detail view (metadata, file
      size, cover), and cancel/retry for an offer the reader has not decided
      yet. Scope note: the daemon exposes a single decision per offer; a retry
      after a failed download is the first case to pin down.
- [ ] **Android import UX** — batch import with one progress surface instead of
      a per-file dialog, and a way to correct metadata after a file lands.
- [ ] **Public book/review/profile links** — [`docs/PUBLIC_LINKS.md`]
      (PUBLIC_LINKS.md) describes native handling for three platform URLs, but
      `AndroidManifest.xml` currently claims only the OAuth callback. Decide
      whether to restore the native screens or retire the document.

- [ ] **Confirm Readium progress persistence (Android)** — the reader should
      persist and restore its position through `ReaderSession`; prove it across
      a cold start before relying on it for progress sync.
- [ ] **Mobile PDF reader** — PDFs import as files today; decide the viewer
      (native PDF view vs webview) and cover both platforms.
- [ ] **Mobile book view** — a detail screen reached from every shelf card,
      showing cover, title, authors, and file info, hosting the two actions
      below.
- [ ] **Reading status and PDS sync** — set a book's reading status from the
      book view and publish it to the reader's PDS; decide the platform record
      schema first and queue writes while offline.
- [ ] **Cover picking from the book view** — search Bibliograph
      (`CatalogClient` already wraps `searchEditions`) or pick a local file and
      adopt it as the cover.
- [ ] **Formats as a syncable entity** — `formats` is migration-seeded with
      fixed ids but has no change-log triggers and no dump entry. Sync the full
      row (change log + dump, with the dump field defaulting to empty so
      versions interoperate) so a peer can learn a format row it lacks.
- [ ] **Progress indicators** — a stripe on every card surface fed by
      `reading_progress`, and a transient "Resumed at N%" chip in the reader.
      Depends on reading progress syncing.
- [ ] **Push path and a background advertisement** — a way for the phone to
      send local change-log rows on demand, and moving the advertised sync
      endpoint into a service that outlives the app process.

## How to read this

- Each done item names the source that implements it, not an ADR: the mobile
  side has no ADR set (the desktop repo carries those).
- The working queue is Taskwarrior, not this file: one task per item somebody
  is about to pick up, with `livtet_component:mobile`. See
  [`docs/tasks.md`](tasks.md) for the setup and the across-device workflow.
- Keep the two in sync by hand: move an item to **In progress** here when a
  task starts, and to **Implemented** when it lands.
