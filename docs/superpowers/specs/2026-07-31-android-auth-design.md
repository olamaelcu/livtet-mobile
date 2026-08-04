# Android Sign-In: Google, Apple, ATProto — Design Spec

**Date**: 2026-07-31
**Status**: approved
**Scope**: `mobile/android/` only — no Rust/FFI changes

## 1. Summary

Add optional sign-in to the Livtet Android app via three providers: Google,
Apple, and AT Protocol. Auth is Android-only (no Rust core changes), optional
(not a launch gate), and progressive (unlocks future social/sync features).
Implementation uses Android's modern Credential Manager API for Google, native
Apple sign-in for Apple, and a custom ATProto OAuth (DID-based PKCE) client.

## 2. Architecture

### 2.1 New module: `:core:auth`

```
core/auth/
├── build.gradle.kts
└── src/main/kotlin/net/olamaelcu/livtet/core/auth/
    ├── AccountManager.kt          # Singleton: auth state, sign-in/out, token store
    ├── AccountState.kt            # Data class: signed-in providers, profiles
    ├── provider/
    │   ├── AuthProvider.kt        # Sealed interface for provider types
    │   ├── GoogleAuthProvider.kt  # Credential Manager integration
    │   ├── AppleAuthProvider.kt   # Sign in with Apple (SIWA)
    │   └── AtprotoAuthProvider.kt # ATProto OAuth (DID resolution -> PKCE -> token)
    └── storage/
        └── SecureTokenStore.kt    # EncryptedSharedPreferences wrapper
```

### 2.2 App module additions

```
app/
└── src/main/java/net/olamaelcu/livtet/
    ├── account/
    │   ├── AccountScreen.kt       # Signed-in providers, sign-in buttons
    │   ├── SignInSheet.kt         # Modal bottom sheet provider picker
    │   └── AccountViewModel.kt    # Bridges AccountManager -> Compose state
    └── DashboardActivity.kt       # Add Account tab to bottom nav
```

### 2.3 Dependencies

| Library | Purpose |
|---|---|
| `androidx.credentials:credentials` | Google Sign-In via Credential Manager |
| `com.google.android.libraries.identity.googleid:googleid` | Google ID token parsing |
| `io.ktor:ktor-client-core` + `io.ktor:ktor-client-okhttp` | ATProto OAuth HTTP |
| `androidx.security:security-crypto` | EncryptedSharedPreferences for tokens |

No new Rust FFI methods. No changes to `Bridge.kt`. No changes to Rust core.

## 3. Data Flow

### 3.1 AccountState

```kotlin
data class AccountState(
    val providers: Map<AuthProvider, ProviderAccount>,
) {
    val isAnySignedIn: Boolean get() = providers.isNotEmpty()
}

data class ProviderAccount(
    val provider: AuthProvider,
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
    val signedInAt: Instant,
)

sealed interface AuthProvider {
    data object Google : AuthProvider
    data object Apple : AuthProvider
    data class Atproto(val did: String, val handle: String) : AuthProvider
}
```

### 3.2 AccountManager

Singleton following the project's existing pattern (`Bridge`, `FeatureFlagsManager`,
`ThemeManager`).

```kotlin
object AccountManager {
    fun flow(context: Context): Flow<AccountState>
    suspend fun signIn(context: Context, provider: AuthProvider): ProviderAccount
    suspend fun signOut(context: Context, provider: AuthProvider)
}
```

- Reads/writes `EncryptedSharedPreferences` via `SecureTokenStore`.
- Emits updated `AccountState` after every sign-in/sign-out.
- Invalid tokens are removed from state and cleared from storage.
- Every step (sign-in start, provider callback received, token stored, error)
  is logged via Timber with the provider tag.

### 3.3 AccountViewModel

```kotlin
class AccountViewModel : ViewModel() {
    private val _state = MutableStateFlow(AccountState(emptyMap()))
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AccountEvent>()
    val events: SharedFlow<AccountEvent> = _events.asSharedFlow()

    fun signIn(provider: AuthProvider)
    fun signOut(provider: AuthProvider)
}
```

Collected in Compose via `viewModel.state.collectAsState()`. Events flow to
Snackbar display. Same pattern as `SettingsViewModel`.

### 3.4 AccountEvent

```kotlin
sealed interface AccountEvent {
    data class SignInSucceeded(val provider: AuthProvider) : AccountEvent
    data class SignInFailed(val provider: AuthProvider, val message: String) : AccountEvent
    data class SignOutComplete(val provider: AuthProvider) : AccountEvent
}
```

## 4. Provider Implementations

### 4.1 Google Sign-In

- **API**: `androidx.credentials.CredentialManager` with `GetGoogleIdOption`.
- **Flow**: `getCredential()` -> parse `GoogleIdTokenCredential` -> extract
  display name, email, avatar URL, ID token -> store in `SecureTokenStore`.
- **Silent sign-in**: On app start, `getCredential()` with
  `autoSelectEnabled = true`. If the user previously signed in and hasn't
  revoked, returns immediately without UI.
- **Provider type**: `AuthProvider.Google` (singleton object).
- **Logging**: Timber tag `"Auth/Google"`.
- **Flavor gating**: Enabled only when `BuildConfig.GOOGLE_SIGN_IN_ENABLED` is
  `true` (set in `app/build.gradle.kts` for `playstore` only).

### 4.2 Apple Sign-In

- **API**: Native Android Sign in with Apple (SIWA) via web OAuth flow.
- **Flow**: Launch Chrome Custom Tab for Apple ID auth page -> receive
  `ASAuthorizationAppleIDCredential` -> extract user identifier, display name,
  email, identity token -> store in `SecureTokenStore`.
- **Provider type**: `AuthProvider.Apple` (singleton object).
- **Logging**: Timber tag `"Auth/Apple"`.
- **Flavor gating**: Enabled only when `BuildConfig.APPLE_SIGN_IN_ENABLED` is
  `true` (set in `app/build.gradle.kts` for `playstore` only).
- **Note**: The Apple developer account must register the Android app's bundle
  ID for the "Sign in with Apple" service.

### 4.3 ATProto OAuth (DID-based)

- **API**: Custom Ktor HTTP client. No third-party ATProto SDK.
- **Flow**:
  1. User enters handle (e.g., `user.bsky.social`).
  2. Resolve DID: fetch `https://{handle}/.well-known/atproto-did` or check
     DNS `_atproto.{handle}` TXT record. Log result.
  3. OAuth server discovery: from DID, resolve to PDS endpoint, fetch
     `/.well-known/oauth-authorization-server`. Log discovered endpoints.
  4. PKCE: generate code verifier (32 random bytes, base64url) + challenge
     (SHA-256 of verifier, base64url).
  5. Open `authorization_endpoint` in Chrome Custom Tab with `scope=atproto`,
     `response_type=code`, `code_challenge`, `code_challenge_method=S256`,
     `redirect_uri=livtet://atproto-callback`.
  6. Intent-filter in manifest catches `livtet://atproto-callback` with the
     authorization code. Log redirect received.
  7. Token exchange: POST to `token_endpoint` with code + code verifier +
     DPoP proof -> receive access token, refresh token, DPoP nonce.
  8. Generate and persist ES256 keypair for DPoP-bound token requests.
  9. Store DID, handle, access token, refresh token, DPoP keypair in
     `SecureTokenStore`. Log sign-in complete with DID.
- **Provider type**: `AuthProvider.Atproto(did, handle)`.
- **Logging**: Timber tag `"Auth/ATProto"`.
- **DPoP proofs**: ~50 lines of custom JWT signing using `kotlinx-serialization`
  + `javax.crypto` for ES256. No external JWT library needed.
- **Error recovery**: If Chrome Custom Tabs unavailable (unlikely on API 24+),
  fall back to opening in default browser. Network failures retry 2x with
  exponential backoff.

## 5. Token Storage

`SecureTokenStore` wraps `EncryptedSharedPreferences` from
`androidx.security:security-crypto`. Keys are store-internal; the master key
lives in Android Keystore (hardware-backed when available).

- **Google**: ID token (string), refresh token (string, optional).
- **Apple**: Identity token (string), user identifier (string).
- **ATProto**: DID (string), handle (string), access token (string), refresh
  token (string), DPoP ES256 keypair (JWK JSON).

All tokens are cleared on `signOut()` and when the app is uninstalled
(Keystore-bound encryption means uninstall wipes the master key).

## 6. UI

### 6.1 Navigation change

Replace the "Feed" bottom tab (currently a "Coming soon" placeholder) with a
new "Account" tab using `Icons.Default.Person`:

```
[Dashboard] [Library] [Account]
```

The Feed screen can return later under a different navigational model when
social features are implemented.

### 6.2 AccountScreen

**Signed-out state**: A prompt card with subtitle text and sign-in
buttons (Google and Apple are hidden when their respective build config flags
are disabled, leaving ATProto as the sole option on fdroid/generic):

- "Continue with Google" — branded button per Google's guidelines. Hidden when
  `BuildConfig.GOOGLE_SIGN_IN_ENABLED` is false.
- "Continue with Apple" — branded button. Hidden when
  `BuildConfig.APPLE_SIGN_IN_ENABLED` is false.
- "Sign in with AT Protocol" — branded button with ATProto identity.

Each button shows an inline `CircularProgressIndicator` during the sign-in
flow. Tapping a button calls `viewModel.signIn(provider)`.

**Signed-in state**: A card per connected provider showing:

- Provider icon + display name + email.
- "Signed in since {formatted date}" subtext.
- "Sign out" text button that calls `viewModel.signOut(provider)`.

**Loading**: `CircularProgressIndicator` centered in the tab content area.
**Error**: Material3 `Snackbar` driven by `AccountViewModel.events`.

### 6.3 SignInSheet

Provider picker rendered inside a `ModalBottomSheet` matching the
`AddBookWizard` pattern already used in the app. Keeps the Account tab
clean and gives sign-in its own focused space.

## 7. Per-Flavor Behavior

| Provider | `playstore` | `fdroid` | `generic` |
|---|---|---|---|
| Google | Available | **Removed at build time** | **Removed at build time** |
| Apple | Available | **Removed at build time** | **Removed at build time** |
| ATProto | Available | Available | Available |

Google and Apple providers are gated behind build config constants
(`BuildConfig.GOOGLE_SIGN_IN_ENABLED`, `BuildConfig.APPLE_SIGN_IN_ENABLED`),
set to `true` only in the `playstore` product flavor. The `AccountScreen`
Composable checks these flags and omits the corresponding buttons when
disabled. ATProto is available in all flavors.

## 8. Error Handling

- All sign-in flows wrap in `runCatching`. Failures are logged via Timber
  with provider tag (`"Auth/Google"`, `"Auth/Apple"`, `"Auth/ATProto"`)
  and emitted as `AccountEvent.SignInFailed`.
- User-facing error messages are provider-specific and user-readable
  (e.g., "Could not sign in with Google. Try again.").
- Network failures (ATProto): retry 2x with exponential backoff
  (1s, 2s) before surfacing error to user.
- Chrome Custom Tabs unavailable: fall back to `ACTION_VIEW` intent in
  default browser.
- Token expiry: ATProto refresh tokens used proactively when access token
  is near expiry. Google/Apple tokens checked for validity on use; expired
  tokens trigger re-authentication prompt.
- DPoP nonce mismatch: caught as `401` with new nonce in header; retry
  once with updated nonce.

## 9. Testing

### 9.1 Unit tests

- `AccountManager.signIn`/`signOut` with faked `CredentialManager`,
  Ktor `MockEngine`, and in-memory token storage.
- ATProto: PKCE verifier generation, DPoP proof JWT signing, DID resolution
  parsing — all pure functions.
- `AccountState` merge semantics (sign into second provider, sign out of one).
- `SecureTokenStore` read/write/clear with faked `EncryptedSharedPreferences`.

### 9.2 Compose UI tests

- `AccountScreen` with mocked `AccountViewModel` in states: signed out,
  one provider signed in, multiple providers signed in, loading, error.
- `SignInSheet` rendering and provider button interactions.
- Google and Apple button visibility gated on their respective
  `BuildConfig` flags.

## 10. Decisions

- **ATProto OAuth server metadata caching**: Cache for 1 hour. Re-fetch on
  any failure (network error, 4xx from cached endpoint). No persistent cache
  across app restarts — cheap enough to re-discover on each launch.
- **Apple Sign-In**: Requires an active Apple Developer account with the
  Android app's bundle ID (`net.olamaelcu.livtet`) registered for SIWA
  before the Apple provider can be tested. The Apple button renders but
  surfaces a "not configured" message at runtime if registration is missing.
- **ATProto DPoP keypair**: Store until explicit sign-out. Rotating per
  session would force re-authentication on every app restart, which is
  poor UX. The keypair lives in `EncryptedSharedPreferences` alongside
  the tokens.
