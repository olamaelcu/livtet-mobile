# Sync verification — reading progress between this phone and a peered desktop

This file is both a runbook and a prompt. An agent or a reader with the phone in
hand performs the round trip below, then **edits the Status table in a follow-up
commit**. Nothing here is green because the source says so: `reading_progress`
is only verified when a position written on one device reads back on the other.

## Status

| Field | Value |
| --- | --- |
| Last verified | 2026-11-08 |
| Mobile commit | `d796709` + core `0f8d119` (the installed APK was built from the same tree; the claim fix is committed as `0f8d119`) |
| core submodule | `0f8d119` |
| Phone (model / Android / serial) | SM-G991U / Android 14 / `192.168.1.105:5555` |
| Desktop build | `livtet-desktop` `b085e81`, daemon core `7d5bf7f` (predates the skip change; it only receives in this run) |
| `cargo test -p livtet-sync -p livtet-ffi` | pass; `livtet-sync-server` lib 21/21 after the claim fix |
| Instrumented `BridgeSyncTest` | 24/24 on SM-G991U, including `anUnpairedAppStartBindsNothing` and `aPairedAppStartBindsTheEndpoint` |
| Phone → desktop: position arrives | pass — phone wrote `0.0805` / 69 s / `OEBPS/909196632077550380_1342-h-1.htm.html` (Chapter IV, 8%); the daemon pull at 19:25:09 stored the same locator |
| Desktop → phone: position arrives | pass — desktop wrote `OEBPS/desktop-marker.html` at version 4060; the phone pull applied it (`applied=71 conflicts=11`, latest change won) and the row carries the marker |
| Orphan row: counted as `skipped`, page kept | pass — a progress row planted for edition `01a0…aa01` produced `applied=0 skipped=1 cursor=4061`, no FK error and nothing written; the next pull reported `skipped=0` |
| Unpaired install: nothing advertised | pass in the instrumented suite; not re-run on this paired install |
| Deviations / follow-ups | The instrumented run reset app data, so pairing was recreated. The reverse claim first failed with `this device has no valid id` (hex host id); fixed in core `0f8d119`. The phone keeps one row per edition (unique edition+format), and row ids converged after the first cross-pull. Backfill pulls recorded conflicts as designed, with the latest change applying. |

### Run notes

```
SyncAdvertisement: sync: advertising while the app is open as 6862e5c3
SyncAdvertisement$pause: sync: stopped advertising; the app is in the background
Bridge: sync: 86cc2985 applied=0 conflicts=0 skipped=1 offers=0 cursor=4061 more=false
Bridge: sync: 86cc2985 applied=0 conflicts=0 skipped=0 offers=0 cursor=4061 more=false
```

## What this verifies

The rule the phone implements, and the reason the steps below look the way
they do:

- **One row per edition.** `reading_progress` is keyed by edition (and format)
  and is a core syncable entity, so a position is one row, not a log.
- **Latest version wins.** A change carries the change-log `version` it was
  written at; the newer row is applied and the older one is recorded as a
  conflict. No device id is stored on the row, so nothing else can arbitrate.
- **Pull-only.** The phone never pushes. It writes its own change-log rows and
  **advertises** an endpoint (`_livtet-sync._tcp`) that a paired peer pulls
  from. The endpoint therefore has to be up for a position to leave the phone.
- **The advertisement follows the visible app.** It starts at app start when at
  least one device is paired, stops when the last activity stops, and starts
  again on the next foreground. An unpaired install binds nothing.
- **Orphans are skipped, not fatal.** A progress row whose `edition_id` this
  device does not hold would fail the client's foreign key. The pull stores its
  cursor *before* applying, so that failure would lose the whole page: the row
  is skipped, counted in `SyncPullReport.skipped`, and logged instead.

## Preconditions

- Phone on the `playstore` flavour (`net.olamaelcu.livtet`), which is the one
  the library and sync screens run in.
- One completed pairing, in either direction, between the phone and the
  desktop. Pairing is mutual: the phone's Sync & Devices screen shows the link
  a desktop can claim, and the approval happens on the phone's
  `sync_pending_pairings` list.
- Desktop and phone on the same LAN, with mDNS working (a pairing that only
  completed over the relay will advertise somewhere the desktop cannot browse).
- `adb` reaching the phone (`adb devices` lists it).
- The desktop app is the reader's to start. An agent must not start, restart, or
  kill it; if it is down, the round trip is blocked and the Status table says so.

## Runbook

Substitute the phone's serial for `$SERIAL` and the playstore application id
(`net.olamaelcu.livtet`) wherever a command names one.

### 1. Build and install the phone app

```
mise run android-build --flavor playstore --target device
mise run android-install --flavor playstore --device $SERIAL
```

It must be the full build: `android-install-incremental` ships Kotlin without
regenerating the UniFFI bindings, and `SyncPullReport.skipped` exists only in
the regenerated ones. The first launch after an install spends about 30s
opening the store; a spinner is not a hang.

### 2. Confirm the advertisement starts with the app

With the app foregrounded and a pairing on record:

```
adb -s $SERIAL logcat -c
adb -s $SERIAL logcat -s SyncAdvertisement Bridge
```

Expect `sync: advertising while the app is open as <8 hex>` from
`SyncAdvertisement`. Press home and confirm the endpoint is withdrawn (no
further lines; the app logged `sync: stopped advertising; the app is in the
background`), then reopen the app and expect the advertising line again.

On an install with no pairing the line is
`sync: no paired device, so this device is not advertised` and there is no
advertising line — that is the gate, not a failure.

### 3. Write a position on the phone

Open a book in the reader, turn a few pages, and back out to the library. The
row is written by the reader as the position changes; the client triggers log
it to `change_log` (`reading_progress`, operation `UPDATE`).

Record what the phone holds:

```
mise run android-sqlite --package net.olamaelcu.livtet --db \
  --sql "SELECT lower(hex(edition_id)), progress, last_location, total_reading_time_secs FROM reading_progress"
```

### 4. Pull from the phone on the desktop

Desktop → Sync & Devices → **Check for books** (the daemon method is
`devices.pull`; the desktop also pulls its paired devices about once a minute).
The device must be listed as **Two-way**; a **One-way** row means this desktop
may only be read from, and pulling it is refused.

Then read the desktop's copy:

```
sqlite3 "${XDG_DATA_HOME:-$HOME/.local/share}/net.olamaelcu.livtet/data/livtet.db" \
  "SELECT lower(hex(edition_id)), progress, last_location, total_reading_time_secs FROM reading_progress"
```

The row for the phone's edition must carry the same `last_location` JSON, the
same `progress`, and the same `total_reading_time_secs`. A different locator
for the same edition with an older `version` is the conflict path, and the
desktop's `conflicts` table would say so.

### 5. Write a position on the desktop and pull it back

Open the same book in the desktop reader and move the position. Bring the phone
app to the foreground (which restarts the advertisement if it had stopped) and
pull from it: open Offers (which calls `Bridge.refreshOffers`) or wait for the
15-minute background poll. Then repeat the phone query from step 3: the phone's
row must now carry the desktop's locator and progression — the later version
won.

### 6. The orphan row

1. On the phone, remove the book from the library (the edition row is deleted
   locally; the phone no longer holds that `edition_id`).
2. On the desktop, open that edition and move the position, so a fresh
   `reading_progress` change is written for an edition the phone lacks.
3. Pull on the phone (Offers screen or the background poll) and watch the log:

```
adb -s $SERIAL logcat -s Bridge
```

Expect `sync: <peer> applied=.. conflicts=.. skipped=1 offers=.. cursor=..`.
`skipped` must be at least 1 and the pull must not fail; the cursor must have
advanced past the row (a second pull reports `skipped=0`, not the same page
again). If the pull fails with a foreign-key error, the skip did not happen and
the page was lost — that is the regression this step exists to catch.

### 7. Update this document and commit

Fill in the Status table above with the SHAs, the device, the toolchain, and the
outcome of each step (including a step that could not run and why). Then:

```
git add docs/SYNC_VERIFICATION.md
git commit -s -m "docs: sync verification results for reading progress"
```

## Definition of done

- `cargo test -p livtet-sync -p livtet-ffi` passes, including the two-database
  progress test (`reading_progress_is_pulled_and_a_row_with_no_edition_is_skipped`).
- The instrumented `BridgeSyncTest` passes with the phone connected, including
  `anUnpairedAppStartBindsNothing` and `aPairedAppStartBindsTheEndpoint`.
- Steps 2–6 ran against a paired desktop and their expected observations are
  recorded in the Status table.
- The orphan step shows `skipped=1` on the phone and no foreign-key failure.
