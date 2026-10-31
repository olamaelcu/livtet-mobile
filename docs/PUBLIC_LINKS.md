# Public links (book, review, profile)

The platform publishes shareable permalinks at `https://livtet.olamaelcu.net`;
the Android and iOS apps claim the three that render read-only natively.

| Platform URL | In-app screen | Data |
| --- | --- | --- |
| `/book/{did}/{rkey}` | Book | cover, title, authors, description, metadata, genres, up to 25 reviews |
| `/review/{did}/{rkey}` | Review | reviewer, rating, text, date, linked book |
| `/profile/{didOrHandle}` (`?tab=`) | Profile | avatar, name, handle, bio, status counts, 20 reviews, tabs |
| `/profile/{did}/shelf` | Profile → Shelves | shelf names + counts |
| `/profile/{did}/shelf/{shelfId}` | Profile → Shelves, shelf selected | titles on that shelf |
| `/u/{did}` | Profile | parser only; the platform 303s it to `/profile/{did}` |

Accepted forms: `https://livtet.olamaelcu.net/...` and `livtet://book/...`,
`livtet://review/...`, `livtet://profile/...` (host stands in for the path
prefix). Anything else — wrong host or scheme, missing/extra segments, invalid
DID, record key, or shelf id — is "not handled": the app opens normally and
performs no navigation.

`did` accepts `did:plc:…`, `did:web:…`, and the alias `bibliograph`, which both
platforms resolve to `did:web:biblio.livtet.olamaelcu.net` before any request.
Record keys and shelf ids are `[A-Za-z0-9._~:-]{1,512}`; unknown `tab` values
fall back to `reviews`.

## Data sources

Catalog records come from Bibliograph's hydrated `com.atproto.repo.getRecord`
view; user-owned records (reviews, `bookShelving`, `shelf`) are read from the
actor's PDS, resolved from their DID document (`plc.directory` for `did:plc`,
`/.well-known/did.json` for `did:web`). Identity comes from
`app.bsky.app.actor.getProfile`.

`net.olamaelcu.livtet.biblio.getReviewsByBook` currently returns an empty list for
real books, so a book's reviews are found through Constellation's
`blue.microcosm.links.getBacklinks` (`…bookReview:book.uri`, then
`…bookReview:book`) and each hit is hydrated from its author's PDS.

## Association documents

Both are served by `platform/packages/livtet-service/src/hooks.server.ts` from
`$lib/app-links.ts`, and answer `503 app-link-not-configured` until the matching
variable is set on the platform host:

| Endpoint | Variable |
| --- | --- |
| `/.well-known/apple-app-site-association` | `LIVTET_IOS_APP_ID` = `<TeamID>.net.olamaelcu.livtet` |
| `/.well-known/assetlinks.json` | `LIVTET_ANDROID_APP_LINK_SHA256` = comma-separated fingerprints (upload key **and** Play App Signing) |

Only the Play package `net.olamaelcu.livtet` is listed in `assetlinks.json`. The
`fdroid` and `generic` flavors declare the same intent filters but do not
auto-verify; target them explicitly when testing locally.

## Local verification

```bash
# Android (any flavor, explicit package)
adb shell am start -a android.intent.action.VIEW -d "livtet://book/bibliograph/ol.OL3425796M"
adb shell pm get-app-links net.olamaelcu.livtet   # verify after a Play build

# iOS (simulator honours the custom scheme; Universal Links need a device)
xcrun simctl openurl booted "livtet://review/did:plc:e2ctbutx6kya6si4if5ngjmm/3myy5wjqiydz7"
```

Unit tests: `mobile/android/app/src/test/java/net/olamaelcu/livtet/AppLinkRouterTest.kt`
and `LivtetUnitTests/PublicRouteParserTests.swift` cover the accepted/rejected
URL matrix; `…/biblio/BiblioClientTest.kt` and `BiblioClientTests.swift` cover
decoding and the review fallback chain.
