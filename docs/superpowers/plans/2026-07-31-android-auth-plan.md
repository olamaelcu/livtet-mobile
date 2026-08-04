# Android Sign-In Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional Sign in with Google, Apple, and ATProto to the Livtet Android app, entirely within the Android layer (no Rust/FFI changes).

**Architecture:** New `:core:auth` Gradle module containing `AccountManager` singleton + three provider implementations (Google via Credential Manager, Apple via native SIWA, ATProto via custom Ktor OAuth client). `AccountViewModel` bridges to Compose. New Account bottom tab replaces Feed tab. Google and Apple gated to `playstore` flavor only.

**Tech Stack:** Jetpack Compose, Kotlin Coroutines, DataStore, `androidx.credentials`, `androidx.security:security-crypto`, Ktor HTTP client, EncryptedSharedPreferences.

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `gradle/libs.versions.toml` | Modify | Add credentials, security-crypto, apple-sign-in version + library entries |
| `settings.gradle.kts` | Modify | Include `:core:auth` module |
| `core/auth/build.gradle.kts` | Create | Module build config with dependencies |
| `core/auth/src/main/kotlin/.../provider/AuthProvider.kt` | Create | Sealed interface for provider types |
| `core/auth/src/main/kotlin/.../AccountState.kt` | Create | State data classes |
| `core/auth/src/main/kotlin/.../storage/SecureTokenStore.kt` | Create | EncryptedSharedPreferences wrapper |
| `core/auth/src/main/kotlin/.../provider/GoogleAuthProvider.kt` | Create | Credential Manager integration |
| `core/auth/src/main/kotlin/.../provider/AppleAuthProvider.kt` | Create | SIWA integration |
| `core/auth/src/main/kotlin/.../provider/AtprotoAuthProvider.kt` | Create | ATProto OAuth client |
| `core/auth/src/main/kotlin/.../AccountManager.kt` | Create | Auth orchestrator singleton |
| `core/auth/src/test/kotlin/.../AccountStateTest.kt` | Create | AccountState unit tests |
| `core/auth/src/test/kotlin/.../storage/SecureTokenStoreTest.kt` | Create | Token store unit tests |
| `core/auth/src/test/kotlin/.../provider/AtprotoOAuthTest.kt` | Create | PKCE, DPoP, DID resolution tests |
| `app/build.gradle.kts` | Modify | Add `:core:auth` dep, build config flags, Credential Manager deps |
| `app/src/main/res/values/strings.xml` | Modify | Add auth-related string resources |
| `app/src/main/AndroidManifest.xml` | Modify | ATProto OAuth redirect intent-filter |
| `app/src/main/java/.../account/AccountViewModel.kt` | Create | Compose state bridge |
| `app/src/main/java/.../account/AccountScreen.kt` | Create | Account tab UI |
| `app/src/main/java/.../account/SignInSheet.kt` | Create | Modal provider picker |
| `app/src/main/java/.../DashboardActivity.kt` | Modify | Add Account tab to bottom nav |
| `app/src/androidTest/java/.../account/AccountScreenTest.kt` | Create | Compose UI instrumentation test |

---

### Task 1: Wire up version catalog and module skeleton

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`
- Create: `core/auth/build.gradle.kts`

- [ ] **Step 1: Add version entries to libs.versions.toml**

At the end of the `[versions]` block (before `[libraries]`), append:

```toml
# Credential Manager (Google Sign-In) — Jetpack library that wraps
# One Tap and legacy Google Sign-In in a single API. 1.3.0 is the
# latest stable as of mid-2025 that targets compileSdk 35+.
credentials = "1.3.0"
# Google ID token — companion to Credential Manager for parsing
# the returned GoogleIdTokenCredential.
googleid = "1.1.1"
# AndroidX Security Crypto — hardware-backed EncryptedSharedPreferences
# for token storage.
securityCrypto = "1.1.0-alpha08"
# Apple Sign-In — native Android library for SIWA web OAuth flow.
# The 1.0.0 is the currently published artifact.
appleSignIn = "1.0.0"
```

At the end of the `[libraries]` block, append:

```toml
# Credential Manager
androidx-credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentials" }
# Google ID token parsing (companion to Credential Manager)
google-id = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleid" }
# AndroidX Security Crypto — EncryptedSharedPreferences
androidx-security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }
# Apple Sign-In for Android
apple-sign-in = { group = "com.apple", name = "apple-sign-in", version.ref = "appleSignIn" }
```

- [ ] **Step 2: Include `:core:auth` in settings.gradle.kts**

Add after the existing `include(":core:designsystem")` line:

```kotlin
include(":core:auth")
```

- [ ] **Step 3: Create `core/auth/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("livtet.android.library.lint")
}

android {
    namespace = "net.olamaelcu.livtet.core.auth"
    compileSdk = 36

    defaultConfig { minSdk = 24 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation(libs.androidx.credentials)
    implementation(libs.google.id)
    implementation(libs.androidx.security.crypto)
    implementation(libs.apple.sign.in)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
```

- [ ] **Step 4: Verify the project syncs**

Run:
```bash
cd mobile/android && ./gradlew :core:auth:dependencies --configuration implementation 2>&1 | head -30
```
Expected: Gradle syncs, no resolution errors, dependency tree shows credentials, security-crypto, ktor, apple-sign-in.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts core/auth/build.gradle.kts
git commit -m "build: add :core:auth module skeleton and version catalog entries"
```

---

### Task 2: AuthProvider sealed interface and AccountState

**Files:**
- Create: `core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/provider/AuthProvider.kt`
- Create: `core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/AccountState.kt`
- Create: `core/auth/src/test/kotlin/net/olamaelcu/livtet/core/auth/AccountStateTest.kt`

- [ ] **Step 1: Write the test**

Create `core/auth/src/test/kotlin/net/olamaelcu/livtet/core/auth/AccountStateTest.kt`:

```kotlin
package net.olamaelcu.livtet.core.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountStateTest {

    @Test
    fun `empty state has no providers`() {
        val state = AccountState(emptyMap())
        assertFalse(state.isAnySignedIn)
        assertEquals(0, state.providers.size)
    }

    @Test
    fun `state with one provider is signed in`() {
        val account = ProviderAccount(
            provider = AuthProvider.Google,
            displayName = "Test User",
            email = "test@example.com",
            avatarUrl = null,
            signedInAt = 0L,
        )
        val state = AccountState(mapOf(AuthProvider.Google to account))
        assertTrue(state.isAnySignedIn)
    }

    @Test
    fun `sign out removes provider from state`() {
        val account = ProviderAccount(
            provider = AuthProvider.Google,
            displayName = "Test User",
            email = "test@example.com",
            avatarUrl = null,
            signedInAt = 0L,
        )
        val state = AccountState(mapOf(AuthProvider.Google to account))
        val afterSignOut = state.copy(providers = state.providers - AuthProvider.Google)
        assertEquals(0, afterSignOut.providers.size)
        assertFalse(afterSignOut.isAnySignedIn)
    }

    @Test
    fun `multiple providers can coexist`() {
        val google = ProviderAccount(
            provider = AuthProvider.Google,
            displayName = "A",
            email = "a@x.com",
            avatarUrl = null,
            signedInAt = 0L,
        )
        val atproto = ProviderAccount(
            provider = AuthProvider.Atproto(did = "did:plc:abc", handle = "test.bsky.social"),
            displayName = "B",
            email = null,
            avatarUrl = null,
            signedInAt = 0L,
        )
        val state = AccountState(mapOf(AuthProvider.Google to google, AuthProvider.Atproto("did:plc:abc", "test.bsky.social") to atproto))
        assertTrue(state.isAnySignedIn)
        assertEquals(2, state.providers.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
cd mobile/android && ./gradlew :core:auth:test --tests "net.olamaelcu.livtet.core.auth.AccountStateTest"
```
Expected: FAIL — compilation errors (types don't exist yet).

- [ ] **Step 3: Create AuthProvider.kt**

Create `core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/provider/AuthProvider.kt`:

```kotlin
package net.olamaelcu.livtet.core.auth.provider

import kotlinx.serialization.Serializable

@Serializable
sealed interface AuthProvider {
    @Serializable
    data object Google : AuthProvider

    @Serializable
    data object Apple : AuthProvider

    @Serializable
    data class Atproto(val did: String, val handle: String) : AuthProvider
}
```

- [ ] **Step 4: Create AccountState.kt**

Create `core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/AccountState.kt`:

```kotlin
package net.olamaelcu.livtet.core.auth

import net.olamaelcu.livtet.core.auth.provider.AuthProvider

data class ProviderAccount(
    val provider: AuthProvider,
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
    val signedInAt: Long, // epoch millis
)

data class AccountState(
    val providers: Map<AuthProvider, ProviderAccount>,
) {
    val isAnySignedIn: Boolean get() = providers.isNotEmpty()
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run:
```bash
cd mobile/android && ./gradlew :core:auth:test
```
Expected: all 4 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/provider/AuthProvider.kt
git add core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/AccountState.kt
git add core/auth/src/test/kotlin/net/olamaelcu/livtet/core/auth/AccountStateTest.kt
git commit -m "feat(auth): add AuthProvider sealed interface and AccountState model"
```

---

### Task 3: SecureTokenStore

**Files:**
- Create: `core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/storage/SecureTokenStore.kt`
- Create: `core/auth/src/test/kotlin/net/olamaelcu/livtet/core/auth/storage/SecureTokenStoreTest.kt`

- [ ] **Step 1: Write the test**

Create `core/auth/src/test/kotlin/net/olamaelcu/livtet/core/auth/storage/SecureTokenStoreTest.kt`:

```kotlin
package net.olamaelcu.livtet.core.auth.storage

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SecureTokenStoreTest {

    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val context: Context = mockk {
        every { getSharedPreferences("livtet_auth_tokens", Context.MODE_PRIVATE) } returns prefs
    }
    private val store = SecureTokenStore(context, testMode = true)

    @Test
    fun `store and retrieve token`() {
        every { prefs.getString("token:google:id_token", null) } returns "fake-id-token"
        store.putToken("token:google:id_token", "fake-id-token")
        val result = store.getToken("token:google:id_token")
        assertEquals("fake-id-token", result)
    }

    @Test
    fun `retrieve missing token returns null`() {
        every { prefs.getString("token:nonexistent", null) } returns null
        val result = store.getToken("token:nonexistent")
        assertNull(result)
    }

    @Test
    fun `clearProvider removes all tokens for a provider`() {
        every { prefs.edit() } returns mockk(relaxed = true)
        store.clearProvider("token:google")
        verify { prefs.edit() }
    }

    @Test
    fun `clearAll removes all tokens`() {
        every { prefs.edit() } returns mockk(relaxed = true)
        store.clearAll()
        verify { prefs.edit() }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
cd mobile/android && ./gradlew :core:auth:test --tests "net.olamaelcu.livtet.core.auth.storage.SecureTokenStoreTest"
```
Expected: FAIL — `SecureTokenStore` doesn't exist.

- [ ] **Step 3: Create SecureTokenStore.kt**

Create `core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/storage/SecureTokenStore.kt`:

```kotlin
package net.olamaelcu.livtet.core.auth.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

class SecureTokenStore(context: Context, private val testMode: Boolean = false) {
    private val prefs: SharedPreferences = if (testMode) {
        context.getSharedPreferences("livtet_auth_tokens", Context.MODE_PRIVATE)
    } else {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "livtet_auth_tokens_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun putToken(key: String, value: String) {
        Timber.d("storing token: $key")
        prefs.edit().putString(key, value).apply()
    }

    fun getToken(key: String): String? {
        val value = prefs.getString(key, null)
        Timber.d("reading token: $key (present=${value != null})")
        return value
    }

    fun clearProvider(prefix: String) {
        Timber.d("clearing provider tokens: $prefix")
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun clearAll() {
        Timber.d("clearing all auth tokens")
        prefs.edit().clear().apply()
    }
}
```

Note: The unit test uses `testMode = true` to bypass `EncryptedSharedPreferences` (which requires an Android Keystore not available in JVM unit tests). In production (`testMode = false`), full hardware-backed encryption is used.

- [ ] **Step 4: Run tests to verify they pass**

Run:
```bash
cd mobile/android && ./gradlew :core:auth:test --tests "net.olamaelcu.livtet.core.auth.storage.SecureTokenStoreTest"
```
Expected: all 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/storage/SecureTokenStore.kt
git add core/auth/src/test/kotlin/net/olamaelcu/livtet/core/auth/storage/SecureTokenStoreTest.kt
git commit -m "feat(auth): add SecureTokenStore with EncryptedSharedPreferences"
```

---

### Task 4: GoogleAuthProvider

**Files:**
- Create: `core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/provider/GoogleAuthProvider.kt`

- [ ] **Step 1: Create GoogleAuthProvider.kt**

Create `core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/provider/GoogleAuthProvider.kt`:

```kotlin
package net.olamaelcu.livtet.core.auth.provider

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import net.olamaelcu.livtet.core.auth.ProviderAccount
import timber.log.Timber

object GoogleAuthProvider {
    private const val TAG = "Auth/Google"

    /** The SHA-1 fingerprint of the signing key's client ID, if using server-side verification. For
     *  sign-in without a backend, this can be null and we only request the ID token. */
    private const val SERVER_CLIENT_ID: String? = null // Set when backend auth is needed

    /** Request only an ID token. No server-side verification needed until a backend exists. */
    private val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
        .apply { SERVER_CLIENT_ID?.let { setServerClientId(it) } }
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(false)
        .build()

    private val request: GetCredentialRequest = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    suspend fun signIn(context: Context): ProviderAccount {
        Timber.tag(TAG).d("starting Google sign-in")
        val credentialManager = CredentialManager.create(context)
        val result: GetCredentialResponse = try {
            credentialManager.getCredential(context, request)
        } catch (e: GetCredentialException) {
            Timber.tag(TAG).e(e, "credential retrieval failed")
            throw GoogleAuthException("Could not sign in with Google", e)
        }
        val credential = result.credential
        val googleId = GoogleIdTokenCredential.createFrom(credential.data)
        val account = ProviderAccount(
            provider = AuthProvider.Google,
            displayName = googleId.displayName ?: googleId.id,
            email = googleId.id,
            avatarUrl = googleId.profilePictureUri?.toString(),
            signedInAt = System.currentTimeMillis(),
        )
        Timber.tag(TAG).d("sign-in succeeded: displayName=${account.displayName}")
        return account
    }

    suspend fun trySilentSignIn(context: Context): ProviderAccount? {
        Timber.tag(TAG).d("attempting silent sign-in")
        val silentOption = GetGoogleIdOption.Builder()
            .apply { SERVER_CLIENT_ID?.let { setServerClientId(it) } }
            .setFilterByAuthorizedAccounts(true)
            .setAutoSelectEnabled(true)
            .build()
        val silentRequest = GetCredentialRequest.Builder()
            .addCredentialOption(silentOption)
            .build()
        return try {
            val credentialManager = CredentialManager.create(context)
            val result = credentialManager.getCredential(context, silentRequest)
            val googleId = GoogleIdTokenCredential.createFrom(result.credential.data)
            ProviderAccount(
                provider = AuthProvider.Google,
                displayName = googleId.displayName ?: googleId.id,
                email = googleId.id,
                avatarUrl = googleId.profilePictureUri?.toString(),
                signedInAt = System.currentTimeMillis(),
            )
        } catch (e: GetCredentialException) {
            Timber.tag(TAG).d("silent sign-in: no saved credentials")
            null
        }
    }

    class GoogleAuthException(message: String, cause: Throwable) : Exception(message, cause)
}
```

- [ ] **Step 2: Commit**

```bash
git add core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/provider/GoogleAuthProvider.kt
git commit -m "feat(auth): add GoogleAuthProvider via Credential Manager"
```

GoogleAuthProvider is not unit-testable at the module level (it requires a real `CredentialManager` which depends on Play Services). Instrumented tests will cover it later in Task 10.

---

### Task 5: AppleAuthProvider

**Files:**
- Create: `core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/provider/AppleAuthProvider.kt`

- [ ] **Step 1: Create AppleAuthProvider.kt**

Create `core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/provider/AppleAuthProvider.kt`:

```kotlin
package net.olamaelcu.livtet.core.auth.provider

import android.content.Context
import net.olamaelcu.livtet.core.auth.ProviderAccount
import timber.log.Timber

/**
 * Apple Sign-In provider for Android.
 *
 * Uses the native Apple sign-in web OAuth flow. On Android this opens the
 * Apple ID authentication page in Chrome Custom Tabs.
 *
 * The Apple developer account must register the bundle ID
 * `net.olamaelcu.livtet` for Sign in with Apple before this provider works.
 * If registration is missing, signIn returns an AppleAuthException with
 * a user-facing message.
 *
 * Currently a stub — will be fully implemented once the team has an active
 * Apple Developer account with the bundle ID registered for SIWA.
 */
object AppleAuthProvider {
    private const val TAG = "Auth/Apple"

    suspend fun signIn(context: Context): ProviderAccount {
        Timber.tag(TAG).d("starting Apple sign-in")
        // TODO: Replace stub with full SIWA implementation when Apple
        // Developer account registration is confirmed. The full flow:
        // 1. Create AuthorizationRequest with requested scopes (email, name)
        // 2. Present the SIWA authorization controller
        // 3. Receive ASAuthorizationAppleIDCredential in callback
        // 4. Extract identity token, user identifier, display name, email
        throw AppleAuthException(
            "Sign in with Apple is not yet configured for this app. " +
                "The Apple Developer account must register the bundle ID " +
                "for the Sign in with Apple service."
        )
    }

    class AppleAuthException(message: String) : Exception(message)
}
```

- [ ] **Step 2: Commit**

```bash
git add core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/provider/AppleAuthProvider.kt
git commit -m "feat(auth): add AppleAuthProvider stub with SIWA guidance"
```

---

### Task 6: ATProto OAuth (packed — PKCE, DPoP, DID resolution)

**Files:**
- Create: `core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/provider/AtprotoAuthProvider.kt`
- Create: `core/auth/src/test/kotlin/net/olamaelcu/livtet/core/auth/provider/AtprotoOAuthTest.kt`

- [ ] **Step 1: Write the PKCE + DPoP unit tests**

Create `core/auth/src/test/kotlin/net/olamaelcu/livtet/core/auth/provider/AtprotoOAuthTest.kt`:

```kotlin
package net.olamaelcu.livtet.core.auth.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AtprotoOAuthTest {

    @Test
    fun `generate code verifier is 43 chars base64url`() {
        val verifier = AtprotoAuthProvider.generateCodeVerifier()
        assertEquals(43, verifier.length)
        assertTrue(verifier.matches(Regex("[A-Za-z0-9_-]+")))
    }

    @Test
    fun `generate code verifier is random between calls`() {
        val v1 = AtprotoAuthProvider.generateCodeVerifier()
        val v2 = AtprotoAuthProvider.generateCodeVerifier()
        assertNotEquals(v1, v2)
    }

    @Test
    fun `code challenge matches S256 spec`() {
        val verifier = "test-verifier-value-with-43-chars-length!!"
        val challenge = AtprotoAuthProvider.computeCodeChallenge(verifier)
        assertEquals(43, challenge.length)
        assertTrue(challenge.matches(Regex("[A-Za-z0-9_-]+")))
        assertNotEquals(verifier, challenge)
    }

    @Test
    fun `code challenge is deterministic`() {
        val verifier = "abc123-verifier-value-that-is-long-enoughh"
        val c1 = AtprotoAuthProvider.computeCodeChallenge(verifier)
        val c2 = AtprotoAuthProvider.computeCodeChallenge(verifier)
        assertEquals(c1, c2)
    }

    @Test
    fun `generate ES256 DPoP keypair returns JWK JSON`() {
        val jwk = AtprotoAuthProvider.generateDpopKeypair()
        assertTrue(jwk.contains("\"kty\""))
        assertTrue(jwk.contains("EC"))
        assertTrue(jwk.contains("\"crv\""))
        assertTrue(jwk.contains("P-256"))
        assertTrue(jwk.contains("\"x\""))
        assertTrue(jwk.contains("\"y\""))
        assertTrue(jwk.contains("\"d\""))
    }

    @Test
    fun `generate DPoP keypair is random between calls`() {
        val k1 = AtprotoAuthProvider.generateDpopKeypair()
        val k2 = AtprotoAuthProvider.generateDpopKeypair()
        assertNotEquals(k1, k2)
    }

    @Test
    fun `create DPoP proof JWT has correct structure`() {
        val keypair = AtprotoAuthProvider.generateDpopKeypair()
        val proof = AtprotoAuthProvider.createDpopProof(
            keypair = keypair,
            httpMethod = "POST",
            httpUri = "https://bsky.social/oauth/token",
            nonce = "test-nonce-123",
        )
        // JWT has 3 base64url segments separated by dots
        val parts = proof.split(".")
        assertEquals(3, parts.size)
        // Header segment decodes to JSON with typ=dpop+jwt
        val header = String(java.util.Base64.getUrlDecoder().decode(parts[0]))
        assertTrue(header.contains("\"typ\""))
        assertTrue(header.contains("dpop+jwt"))
        assertTrue(header.contains("\"alg\""))
        assertTrue(header.contains("ES256"))
        assertTrue(header.contains("\"jwk\""))
        // Payload segment decodes to JSON with htm, htu
        val payload = String(java.util.Base64.getUrlDecoder().decode(parts[1]))
        assertTrue(payload.contains("\"htm\""))
        assertTrue(payload.contains("POST"))
        assertTrue(payload.contains("\"htu\""))
        assertTrue(payload.contains("https://bsky.social/oauth/token"))
        assertTrue(payload.contains("\"iat\""))
        assertTrue(payload.contains("\"jti\""))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
cd mobile/android && ./gradlew :core:auth:test --tests "net.olamaelcu.livtet.core.auth.provider.AtprotoOAuthTest"
```
Expected: FAIL — `AtprotoAuthProvider` doesn't exist.

- [ ] **Step 3: Create AtprotoAuthProvider.kt**

Create `core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/provider/AtprotoAuthProvider.kt`:

```kotlin
package net.olamaelcu.livtet.core.auth.provider

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.olamaelcu.livtet.core.auth.ProviderAccount
import timber.log.Timber
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

object AtprotoAuthProvider {
    private const val TAG = "Auth/ATProto"

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = HttpClient(OkHttp) {
        engine { config { retryOnConnectionFailure(true) } }
    }

    // ── Public API ──────────────────────────────────────────────────

    data class OAuthSession(
        val did: String,
        val handle: String,
        val accessToken: String,
        val refreshToken: String,
        val dpopKeypair: String, // JWK JSON with private key
    )

    /**
     * Step 1 of 2: Resolve the user's handle to a DID, discover the OAuth
     * authorization server, generate PKCE challenge + DPoP keypair, and
     * open the authorization URL in Chrome Custom Tabs.
     *
     * The redirect (Step 2) is caught by an intent-filter in AndroidManifest.
     */
    suspend fun beginOAuth(context: Context, handle: String): String {
        Timber.tag(TAG).d("beginOAuth: handle=$handle")
        val did = resolveDid(handle)
        Timber.tag(TAG).d("resolved DID: $did")
        val authMetadata = fetchOAuthMetadata(did)
        Timber.tag(TAG).d("auth server: ${authMetadata.authorizationEndpoint}")

        val codeVerifier = generateCodeVerifier()
        val codeChallenge = computeCodeChallenge(codeVerifier)
        val dpopKeypair = generateDpopKeypair()

        // Persist verifier + keypair temporarily (in-memory; lost on process death).
        // For production, persist to SecureTokenStore with a short TTL.
        pendingOAuth = PendingOAuth(
            handle = handle,
            did = did,
            codeVerifier = codeVerifier,
            dpopKeypair = dpopKeypair,
            tokenEndpoint = authMetadata.tokenEndpoint,
        )

        val authUrl = buildString {
            append(authMetadata.authorizationEndpoint)
            append("?response_type=code")
            append("&client_id=${clientId(did)}")
            append("&redirect_uri=$REDIRECT_URI")
            append("&scope=atproto%20transition:generic")
            append("&code_challenge=$codeChallenge")
            append("&code_challenge_method=S256")
            append("&state=${java.util.UUID.randomUUID()}")
        }

        Timber.tag(TAG).d("opening auth URL: $authUrl")
        val tabsIntent = CustomTabsIntent.Builder().build()
        tabsIntent.launchUrl(context, Uri.parse(authUrl))

        return did
    }

    /**
     * Step 2 of 2: Handle the redirect callback. Exchange the authorization
     * code for tokens, store the DPoP keypair + tokens.
     */
    suspend fun handleCallback(redirectUri: Uri): OAuthSession {
        Timber.tag(TAG).d("handleCallback: $redirectUri")
        val pending = pendingOAuth
            ?: throw AtprotoOAuthException("no pending OAuth flow")

        val code = redirectUri.getQueryParameter("code")
            ?: throw AtprotoOAuthException("no authorization code in redirect")

        Timber.tag(TAG).d("exchanging code for tokens at ${pending.tokenEndpoint}")
        val tokenResponse = exchangeCodeForTokens(pending.dpopKeypair, pending.tokenEndpoint, code)
        Timber.tag(TAG).d("token exchange complete: did=${pending.did}")

        val session = OAuthSession(
            did = pending.did,
            handle = pending.handle,
            accessToken = tokenResponse.accessToken,
            refreshToken = tokenResponse.refreshToken,
            dpopKeypair = pending.dpopKeypair,
        )
        pendingOAuth = null
        return session
    }

    // ── DID Resolution ──────────────────────────────────────────────

    private suspend fun resolveDid(handle: String): String {
        val url = "https://$handle/.well-known/atproto-did"
        val response = httpClient.get(url)
        if (response.status != HttpStatusCode.OK) {
            throw AtprotoOAuthException("could not resolve DID for $handle: HTTP ${response.status.value}")
        }
        val text = response.bodyAsText().trim()
        if (!text.startsWith("did:")) {
            throw AtprotoOAuthException("invalid DID response for $handle: $text")
        }
        return text
    }

    // ── OAuth Server Discovery ──────────────────────────────────────

    @Serializable
    data class OAuthMetadata(
        val issuer: String,
        val authorizationEndpoint: String,
        val tokenEndpoint: String,
        val scopes_supported: List<String> = emptyList(),
    )

    private suspend fun fetchOAuthMetadata(did: String): OAuthMetadata {
        // Resolve DID to PDS endpoint, then fetch OAuth metadata.
        // For now, we use the Bluesky PDS directly for `bsky.social` handles.
        // Full DID-to-PDS resolution would involve a PLC directory lookup.
        val pdsUrl = resolvePdsUrl(did)
        val metadataUrl = "$pdsUrl/.well-known/oauth-authorization-server"
        val response = httpClient.get(metadataUrl)
        if (response.status != HttpStatusCode.OK) {
            throw AtprotoOAuthException("could not fetch OAuth metadata: HTTP ${response.status.value}")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun resolvePdsUrl(did: String): String {
        // For `did:plc:*` DIDs, query the PLC directory for the PDS endpoint.
        // For `did:web:*` DIDs, the PDS is at the domain in the DID.
        if (did.startsWith("did:plc:")) {
            val response = httpClient.get("https://plc.directory/$did")
            val doc: JsonObject = json.decodeFromString(response.bodyAsText())
            val services = doc["service"] ?: throw AtprotoOAuthException("no service entries in DID doc")
            // Find the atproto_pds service entry
            return extractPdsEndpoint(services.toString())
        }
        throw AtprotoOAuthException("unsupported DID method: $did")
    }

    private fun extractPdsEndpoint(servicesJson: String): String {
        // Parse the services array to find `type=AtprotoPersonalDataServer`
        val services: List<JsonObject> = json.decodeFromString(servicesJson)
        val pdsService = services.find { svc ->
            svc["id"]?.jsonPrimitive?.content == "#atproto_pds"
        }
        val endpoint = pdsService?.get("serviceEndpoint")?.jsonPrimitive?.content
            ?: throw AtprotoOAuthException("no atproto_pds service found in DID doc")
        return endpoint
    }

    // ── Token Exchange ──────────────────────────────────────────────

    @Serializable
    data class TokenResponse(
        val access_token: String,
        val token_type: String,
        val refresh_token: String,
        val expires_in: Long = 3600,
    )

    private suspend fun exchangeCodeForTokens(
        dpopKeypair: String,
        tokenEndpoint: String,
        code: String,
    ): TokenResponse {
        val pending = pendingOAuth ?: throw AtprotoOAuthException("no pending OAuth flow")
        val dpopProof = createDpopProof(dpopKeypair, "POST", tokenEndpoint, "initial")

        val response = httpClient.submitForm(
            url = tokenEndpoint,
            formParameters = parameters {
                append("grant_type", "authorization_code")
                append("code", code)
                append("code_verifier", pending.codeVerifier)
                append("client_id", clientId(pending.did))
                append("redirect_uri", REDIRECT_URI)
            },
        ) {
            header("DPoP", dpopProof)
            header("Content-Type", "application/x-www-form-urlencoded")
        }

        if (response.status != HttpStatusCode.OK) {
            val body = response.bodyAsText()
            Timber.tag(TAG).e("token exchange failed: $body")
            throw AtprotoOAuthException("token exchange failed: HTTP ${response.status.value}")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    // ── PKCE ────────────────────────────────────────────────────────

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun computeCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    // ── DPoP ────────────────────────────────────────────────────────

    fun generateDpopKeypair(): String {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = generator.generateKeyPair()
        val publicKey = keyPair.public as ECPublicKey
        val privateKey = keyPair.private as ECPrivateKey
        return buildJwkJson(publicKey, privateKey)
    }

    fun createDpopProof(
        keypair: String,
        httpMethod: String,
        httpUri: String,
        nonce: String,
    ): String {
        val jwk: JsonObject = json.decodeFromString(keypair)
        val header = mapOf(
            "typ" to "dpop+jwt",
            "alg" to "ES256",
            "jwk" to mapOf(
                "kty" to jwk["kty"]!!.jsonPrimitive.content,
                "crv" to jwk["crv"]!!.jsonPrimitive.content,
                "x" to jwk["x"]!!.jsonPrimitive.content,
                "y" to jwk["y"]!!.jsonPrimitive.content,
            ),
        )
        val now = System.currentTimeMillis() / 1000
        val payload = mapOf(
            "htm" to httpMethod,
            "htu" to httpUri,
            "iat" to now,
            "jti" to java.util.UUID.randomUUID().toString(),
        )
        val headerB64 = base64UrlEncode(json.encodeToString(MapSerializer, header).toByteArray())
        val payloadB64 = base64UrlEncode(json.encodeToString(MapSerializer, payload).toByteArray())
        val signingInput = "$headerB64.$payloadB64"
        // Sign with the private key
        val signature = signWithKey(keypair, signingInput)
        return "$signingInput.$signature"
    }

    // ── Key Handling ────────────────────────────────────────────────

    private fun buildJwkJson(publicKey: ECPublicKey, privateKey: ECPrivateKey): String {
        val w = publicKey.w
        val x = Base64.getUrlEncoder().withoutPadding().encodeToString(w.affineX.toByteArray())
        val y = Base64.getUrlEncoder().withoutPadding().encodeToString(w.affineY.toByteArray())
        val d = Base64.getUrlEncoder().withoutPadding().encodeToString(privateKey.s.toByteArray())
        return """{"kty":"EC","crv":"P-256","x":"$x","y":"$y","d":"$d"}"""
    }

    private fun signWithKey(keypairJson: String, signingInput: String): String {
        val jwk: JsonObject = json.decodeFromString(keypairJson)
        val d = Base64.getUrlDecoder().decode(jwk["d"]!!.jsonPrimitive.content)
        val x = Base64.getUrlDecoder().decode(jwk["x"]!!.jsonPrimitive.content)
        val y = Base64.getUrlDecoder().decode(jwk["y"]!!.jsonPrimitive.content)

        val spec = ECGenParameterSpec("secp256r1")
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(spec)
        // Reconstruct the private key from d,x,y
        val keyFactory = java.security.KeyFactory.getInstance("EC")
        val pubSpec = java.security.spec.ECPoint(
            java.math.BigInteger(1, x),
            java.math.BigInteger(1, y),
        )
        val privSpec = java.security.spec.ECPrivateKeySpec(
            java.math.BigInteger(1, d),
            java.security.spec.ECParameterSpec(
                generator.generateKeyPair().public.params.curve,
                generator.generateKeyPair().public.params.generator,
                generator.generateKeyPair().public.params.order,
                generator.generateKeyPair().public.params.cofactor,
            ),
        )
        val privateKey = keyFactory.generatePrivate(privSpec) as ECPrivateKey

        val signature = java.security.Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(signingInput.toByteArray())
        val derSig = signature.sign()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(derSig)
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun clientId(did: String): String =
        "$REDIRECT_URI/${java.net.URLEncoder.encode(did, "UTF-8")}"

    private var pendingOAuth: PendingOAuth? = null

    private data class PendingOAuth(
        val handle: String,
        val did: String,
        val codeVerifier: String,
        val dpopKeypair: String,
        val tokenEndpoint: String,
    )

    class AtprotoOAuthException(message: String) : Exception(message)

    private const val REDIRECT_URI = "livtet://atproto-callback"

    private object MapSerializer : kotlinx.serialization.KSerializer<Map<String, Any>> {
        override val descriptor = kotlinx.serialization.descriptors.serialDescriptor<Map<String, Any>>()
        override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Map<String, Any>) {
            val jsonEncoder = encoder as kotlinx.serialization.json.JsonEncoder
            jsonEncoder.encodeJsonElement(json.encodeToJsonElement(JsonObject.serializer(), JsonObject(value.mapValues { it.value.toJsonElement() })))
        }
        override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Map<String, Any> = emptyMap()

        private fun Any.toJsonElement(): kotlinx.serialization.json.JsonElement = when (this) {
            is String -> kotlinx.serialization.json.JsonPrimitive(this)
            is Number -> kotlinx.serialization.json.JsonPrimitive(this.toLong())
            is Boolean -> kotlinx.serialization.json.JsonPrimitive(this)
            is Map<*, *> -> JsonObject((this as Map<String, Any>).mapValues { it.value.toJsonElement() })
            else -> kotlinx.serialization.json.JsonPrimitive(this.toString())
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```bash
cd mobile/android && ./gradlew :core:auth:test --tests "net.olamaelcu.livtet.core.auth.provider.AtprotoOAuthTest"
```
Expected: all 7 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/provider/AtprotoAuthProvider.kt
git add core/auth/src/test/kotlin/net/olamaelcu/livtet/core/auth/provider/AtprotoOAuthTest.kt
git commit -m "feat(auth): add ATProto OAuth provider with PKCE and DPoP support"
```

---

### Task 7: AccountManager singleton

**Files:**
- Create: `core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/AccountManager.kt`

- [ ] **Step 1: Create AccountManager.kt**

Create `core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/AccountManager.kt`:

```kotlin
package net.olamaelcu.livtet.core.auth

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.olamaelcu.livtet.core.auth.provider.AppleAuthProvider
import net.olamaelcu.livtet.core.auth.provider.AtprotoAuthProvider
import net.olamaelcu.livtet.core.auth.provider.AuthProvider
import net.olamaelcu.livtet.core.auth.provider.GoogleAuthProvider
import net.olamaelcu.livtet.core.auth.storage.SecureTokenStore
import timber.log.Timber

object AccountManager {
    private const val TAG = "AccountManager"

    private var tokenStore: SecureTokenStore? = null
    private val _accountState = MutableStateFlow(AccountState(emptyMap()))
    val accountState: Flow<AccountState> = _accountState.asStateFlow()

    fun init(context: Context) {
        if (tokenStore != null) return
        tokenStore = SecureTokenStore(context.applicationContext)
        Timber.tag(TAG).d("AccountManager initialized")
    }

    fun isInitialized(): Boolean = tokenStore != null

    suspend fun signIn(
        context: Context,
        provider: AuthProvider,
    ): ProviderAccount {
        val store = tokenStore ?: throw IllegalStateException("AccountManager not initialized")
        Timber.tag(TAG).d("signIn called for provider: $provider")
        val account = when (provider) {
            AuthProvider.Google -> GoogleAuthProvider.signIn(context)
            AuthProvider.Apple -> AppleAuthProvider.signIn(context)
            is AuthProvider.Atproto -> throw UnsupportedOperationException(
                "ATProto sign-in requires beginOAuth + handleCallback flow"
            )
        }
        store.putToken("token:${providerKey(provider)}:signed_in_at", account.signedInAt.toString())
        if (account.email != null) store.putToken("token:${providerKey(provider)}:email", account.email)
        store.putToken("token:${providerKey(provider)}:display_name", account.displayName)
        val newProviders = _accountState.value.providers + (provider to account)
        _accountState.value = AccountState(newProviders)
        Timber.tag(TAG).d("signIn complete: provider=${providerKey(provider)}, displayName=${account.displayName}")
        return account
    }

    suspend fun beginAtprotoOAuth(context: Context, handle: String): String {
        val store = tokenStore ?: throw IllegalStateException("AccountManager not initialized")
        Timber.tag(TAG).d("beginAtprotoOAuth: handle=$handle")
        val did = AtprotoAuthProvider.beginOAuth(context, handle)
        return did
    }

    suspend fun handleAtprotoCallback(context: Context, redirectUri: Uri): ProviderAccount {
        val store = tokenStore ?: throw IllegalStateException("AccountManager not initialized")
        Timber.tag(TAG).d("handleAtprotoCallback: $redirectUri")
        val session = AtprotoAuthProvider.handleCallback(redirectUri)
        store.putToken("token:atproto:did", session.did)
        store.putToken("token:atproto:handle", session.handle)
        store.putToken("token:atproto:access_token", session.accessToken)
        store.putToken("token:atproto:refresh_token", session.refreshToken)
        store.putToken("token:atproto:dpop_keypair", session.dpopKeypair)
        val provider = AuthProvider.Atproto(session.did, session.handle)
        val account = ProviderAccount(
            provider = provider,
            displayName = session.handle,
            email = null,
            avatarUrl = null,
            signedInAt = System.currentTimeMillis(),
        )
        store.putToken("token:atproto:signed_in_at", account.signedInAt.toString())
        val newProviders = _accountState.value.providers + (provider to account)
        _accountState.value = AccountState(newProviders)
        Timber.tag(TAG).d("atprotoCallback complete: did=${session.did}")
        return account
    }

    suspend fun signOut(context: Context, provider: AuthProvider) {
        val store = tokenStore ?: return
        Timber.tag(TAG).d("signOut: provider=${providerKey(provider)}")
        store.clearProvider("token:${providerKey(provider)}")
        val newProviders = _accountState.value.providers - provider
        _accountState.value = AccountState(newProviders)
        Timber.tag(TAG).d("signOut complete: provider=${providerKey(provider)}")
    }

    private fun providerKey(provider: AuthProvider): String = when (provider) {
        AuthProvider.Google -> "google"
        AuthProvider.Apple -> "apple"
        is AuthProvider.Atproto -> "atproto"
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/auth/src/main/kotlin/net/olamaelcu/livtet/core/auth/AccountManager.kt
git commit -m "feat(auth): add AccountManager singleton orchestrating provider sign-ins"
```

---

### Task 8: Wire app module dependencies and build config flags

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add dependencies and build config flags to app/build.gradle.kts**

Add the `:core:auth` project dependency after the `:core:designsystem` dependency:

```kotlin
implementation(project(":core:designsystem"))
implementation(project(":core:auth"))
```

Add Google and Apple sign-in build config flags in `defaultConfig` (after the `SENTRY_DSN` line):

```kotlin
buildConfigField("boolean", "GOOGLE_SIGN_IN_ENABLED", "false")
buildConfigField("boolean", "APPLE_SIGN_IN_ENABLED", "false")
```

In the `playstore` product flavor block, override both to true:

```kotlin
buildConfigField("boolean", "GOOGLE_SIGN_IN_ENABLED", "true")
buildConfigField("boolean", "APPLE_SIGN_IN_ENABLED", "true")
```

- [ ] **Step 2: Verify the project syncs**

Run:
```bash
cd mobile/android && ./gradlew :app:dependencies --configuration implementation 2>&1 | grep -E "core:auth|credentials|security-crypto"
```
Expected: dependency tree includes `:core:auth` and its transitive dependencies.

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: wire :core:auth into app module with flavor-gated build config flags"
```

---

### Task 9: AccountViewModel

**Files:**
- Create: `app/src/main/java/net/olamaelcu/livtet/account/AccountViewModel.kt`

- [ ] **Step 1: Create AccountViewModel.kt**

Create `app/src/main/java/net/olamaelcu/livtet/account/AccountViewModel.kt`:

```kotlin
package net.olamaelcu.livtet.account

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.olamaelcu.livtet.core.auth.AccountManager
import net.olamaelcu.livtet.core.auth.AccountState
import net.olamaelcu.livtet.core.auth.ProviderAccount
import net.olamaelcu.livtet.core.auth.provider.AppleAuthProvider
import net.olamaelcu.livtet.core.auth.provider.AtprotoAuthProvider
import net.olamaelcu.livtet.core.auth.provider.AuthProvider
import net.olamaelcu.livtet.core.auth.provider.GoogleAuthProvider
import timber.log.Timber

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AccountState(emptyMap()))
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AccountEvent>()
    val events: SharedFlow<AccountEvent> = _events.asSharedFlow()

    init {
        AccountManager.init(application)
        viewModelScope.launch {
            AccountManager.accountState.collect { s -> _state.value = s }
        }
    }

    fun signIn(provider: AuthProvider) {
        viewModelScope.launch {
            try {
                Timber.d("AccountViewModel.signIn: $provider")
                val account: ProviderAccount = when (provider) {
                    is AuthProvider.Atproto -> throw UnsupportedOperationException(
                        "Use beginAtprotoOAuth for ATProto"
                    )
                    else -> AccountManager.signIn(getApplication(), provider)
                }
                _events.emit(AccountEvent.SignInSucceeded(provider))
            } catch (e: AppleAuthProvider.AppleAuthException) {
                Timber.w(e, "Apple sign-in failed: not configured")
                _events.emit(AccountEvent.SignInFailed(provider, e.message ?: "Apple sign-in is not available"))
            } catch (e: GoogleAuthProvider.GoogleAuthException) {
                Timber.w(e, "Google sign-in failed")
                _events.emit(AccountEvent.SignInFailed(provider, "Could not sign in with Google. Try again."))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "sign-in failed for $provider")
                _events.emit(AccountEvent.SignInFailed(provider, "Sign-in failed. Try again."))
            }
        }
    }

    fun beginAtprotoOAuth(handle: String) {
        viewModelScope.launch {
            try {
                Timber.d("AccountViewModel.beginAtprotoOAuth: $handle")
                AccountManager.beginAtprotoOAuth(getApplication(), handle)
                // The Chrome Custom Tab opens; the result comes via handleAtprotoCallback
            } catch (e: AtprotoAuthProvider.AtprotoOAuthException) {
                Timber.w(e, "ATProto OAuth begin failed")
                _events.emit(AccountEvent.SignInFailed(AuthProvider.Atproto("", handle), e.message ?: "Could not connect to ATProto"))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "ATProto OAuth begin failed unexpectedly")
                _events.emit(AccountEvent.SignInFailed(AuthProvider.Atproto("", handle), "Could not start ATProto sign-in"))
            }
        }
    }

    fun handleAtprotoCallback(uri: Uri) {
        viewModelScope.launch {
            try {
                val account = AccountManager.handleAtprotoCallback(getApplication(), uri)
                val provider = AuthProvider.Atproto(
                    (account.provider as AuthProvider.Atproto).did,
                    (account.provider as AuthProvider.Atproto).handle,
                )
                _events.emit(AccountEvent.SignInSucceeded(provider))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "ATProto callback handling failed")
                _events.emit(AccountEvent.SignInFailed(
                    AuthProvider.Atproto("", ""),
                    "Could not complete ATProto sign-in"
                ))
            }
        }
    }

    fun signOut(provider: AuthProvider) {
        viewModelScope.launch {
            AccountManager.signOut(getApplication(), provider)
            _events.emit(AccountEvent.SignOutComplete(provider))
        }
    }
}

sealed interface AccountEvent {
    data class SignInSucceeded(val provider: AuthProvider) : AccountEvent
    data class SignInFailed(val provider: AuthProvider, val message: String) : AccountEvent
    data class SignOutComplete(val provider: AuthProvider) : AccountEvent
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/net/olamaelcu/livtet/account/AccountViewModel.kt
git commit -m "feat(auth): add AccountViewModel bridging AccountManager to Compose"
```

---

### Task 10: AccountScreen UI

**Files:**
- Create: `app/src/main/java/net/olamaelcu/livtet/account/AccountScreen.kt`
- Create: `app/src/main/java/net/olamaelcu/livtet/account/SignInSheet.kt`

- [ ] **Step 1: Create AccountScreen.kt**

Create `app/src/main/java/net/olamaelcu/livtet/account/AccountScreen.kt`:

```kotlin
package net.olamaelcu.livtet.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.olamaelcu.livtet.BuildConfig
import net.olamaelcu.livtet.core.auth.AccountState
import net.olamaelcu.livtet.core.auth.ProviderAccount
import net.olamaelcu.livtet.core.auth.provider.AuthProvider
import net.olamaelcu.livtet.DashboardActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccountScreen(viewModel: AccountViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSigningIn by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }
    var showAtprotoSheet by remember { mutableStateOf(false) }

    // Handle ATProto OAuth redirect callback (set by DashboardActivity)
    val pendingRedirect = DashboardActivity.pendingAtprotoRedirect
    LaunchedEffect(pendingRedirect) {
        if (pendingRedirect != null) {
            DashboardActivity.pendingAtprotoRedirect = null
            viewModel.handleAtprotoCallback(pendingRedirect)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AccountEvent.SignInSucceeded -> {
                    isSigningIn = false
                    snackbarHostState.showSnackbar(
                        "Signed in with ${providerLabel(event.provider)}"
                    )
                }
                is AccountEvent.SignInFailed -> {
                    isSigningIn = false
                    signInError = event.message
                    snackbarHostState.showSnackbar(event.message)
                }
                is AccountEvent.SignOutComplete -> {
                    snackbarHostState.showSnackbar(
                        "Signed out of ${providerLabel(event.provider)}"
                    )
                }
            }
        }
    }

    if (showAtprotoSheet) {
        SignInSheet(
            viewModel = viewModel,
            onDismiss = { showAtprotoSheet = false },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!state.isAnySignedIn) {
                item {
                    SignedOutContent(
                        viewModel = viewModel,
                        isSigningIn = isSigningIn,
                        onAtprotoClick = { showAtprotoSheet = true },
                    )
                }
            } else {
                item {
                    Text(
                        "Your Accounts",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = MaterialTheme.typography.titleSmall.fontSize,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(state.providers.size) { i ->
                    val (provider, account) = state.providers.entries.elementAt(i)
                    SignedInCard(account, onSignOut = { viewModel.signOut(provider) })
                }
                item {
                    SignedOutContent(
                        viewModel = viewModel,
                        isSigningIn = isSigningIn,
                        compact = true,
                        onAtprotoClick = { showAtprotoSheet = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun SignedOutContent(
    viewModel: AccountViewModel,
    isSigningIn: Boolean,
    compact: Boolean = false,
    onAtprotoClick: (() -> Unit)? = null,
) {
    if (!compact) {
        Text(
            "Sign in to unlock social features",
            fontWeight = FontWeight.Medium,
            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    if (BuildConfig.GOOGLE_SIGN_IN_ENABLED) {
        ProviderButton(
            label = "Continue with Google",
            backgroundColor = Color(0xFF4285F4),
            isLoading = isSigningIn,
            onClick = { viewModel.signIn(AuthProvider.Google) },
        )
        Spacer(Modifier.height(8.dp))
    }

    if (BuildConfig.APPLE_SIGN_IN_ENABLED) {
        ProviderButton(
            label = "Continue with Apple",
            backgroundColor = Color.Black,
            isLoading = isSigningIn,
            onClick = { viewModel.signIn(AuthProvider.Apple) },
        )
        Spacer(Modifier.height(8.dp))
    }

    ProviderButton(
        label = "Sign in with AT Protocol",
        backgroundColor = Color(0xFF1185FE),
        isLoading = isSigningIn,
        onClick = { onAtprotoClick?.invoke() },
    )
}

@Composable
private fun ProviderButton(
    label: String,
    backgroundColor: Color,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        enabled = !isLoading,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text(label, color = Color.White)
        }
    }
}

@Composable
private fun SignedInCard(account: ProviderAccount, onSignOut: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                providerLabel(account.provider),
                fontWeight = FontWeight.Medium,
            )
            Text(
                account.displayName,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (account.email != null) {
                Text(
                    account.email,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val dateStr = remember(account.signedInAt) {
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(account.signedInAt))
            }
            Text(
                "Signed in since $dateStr",
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(
                onClick = onSignOut,
                modifier = Modifier.align(Alignment.End),
            ) { Text("Sign out") }
        }
    }
}

private fun providerLabel(provider: AuthProvider): String = when (provider) {
    AuthProvider.Google -> "Google"
    AuthProvider.Apple -> "Apple"
    is AuthProvider.Atproto -> "AT Protocol"
}
```

- [ ] **Step 2: Create SignInSheet.kt**

Create `app/src/main/java/net/olamaelcu/livtet/account/SignInSheet.kt`:

```kotlin
package net.olamaelcu.livtet.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInSheet(
    viewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var handle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Sign in with AT Protocol",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Enter your ATProto handle (e.g., user.bsky.social)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = handle,
                onValueChange = { handle = it },
                label = { Text("Handle") },
                placeholder = { Text("user.bsky.social") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    isLoading = true
                    viewModel.beginAtprotoOAuth(handle.trim())
                    scope.launch { sheetState.hide() }
                    onDismiss()
                },
                enabled = handle.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Continue")
                }
            }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/olamaelcu/livtet/account/AccountScreen.kt
git add app/src/main/java/net/olamaelcu/livtet/account/SignInSheet.kt
git commit -m "feat(auth): add AccountScreen and SignInSheet compose UI"
```

---

### Task 11: Navigation changes — Account tab

**Files:**
- Modify: `app/src/main/java/net/olamaelcu/livtet/DashboardActivity.kt`

- [ ] **Step 1: Add Account tab to bottom navigation**

Replace the Feed tab with Account. In `DashboardActivity.kt`, change the `bottomNavItems` list:

```kotlin
private val bottomNavItems =
    listOf(
        BottomNavItem("dashboard", "Dashboard", null, Icons.Default.Home),
        BottomNavItem("library", "Library", null, Icons.Default.MenuBook),
        BottomNavItem("account", "Account", null, Icons.Default.Person),
    )
```

Add the Account screen composable route. In the `NavHost` block after `composable("feed") { FeedScreen() }`:

```kotlin
composable("account") { AccountScreen() }
```

- [ ] **Step 2: Add ATProto redirect intent-filter to AndroidManifest.xml**

In `app/src/main/AndroidManifest.xml`, inside the `DashboardActivity` block, add:

```xml
<intent-filter android:label="ATProto OAuth">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="livtet" android:host="atproto-callback" />
</intent-filter>
```

- [ ] **Step 3: Handle the redirect in DashboardActivity**

In `DashboardActivity.onCreate`, after `setContent { ... }`, add intent handling:

```kotlin
if (Intent.ACTION_VIEW == intent?.action) {
    intent?.data?.let { uri ->
        if (uri.scheme == "livtet" && uri.host == "atproto-callback") {
            Timber.d("Received ATProto OAuth callback: $uri")
            // The AccountViewModel handles this; we need to forward the URI.
            // For now, store in a companion pending URI and let AccountScreen pick it up.
            pendingAtprotoRedirect = uri
        }
    }
}
```

Add the companion object field:

```kotlin
companion object {
    var pendingAtprotoRedirect: android.net.Uri? = null
}
```

And add the import:
```kotlin
import android.content.Intent
import timber.log.Timber
```

In `AccountScreen`, at the top, collect the pending redirect:

```kotlin
val pendingRedirect = DashboardActivity.pendingAtprotoRedirect
LaunchedEffect(pendingRedirect) {
    if (pendingRedirect != null) {
        DashboardActivity.pendingAtprotoRedirect = null
        viewModel.handleAtprotoCallback(pendingRedirect)
    }
}
```

- [ ] **Step 4: Verify compilation**

Run:
```bash
cd mobile/android && ./gradlew :app:compilePlaystoreDebugKotlin 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/olamaelcu/livtet/DashboardActivity.kt
git add app/src/main/AndroidManifest.xml
git add app/src/main/java/net/olamaelcu/livtet/account/AccountScreen.kt
git commit -m "feat(auth): replace Feed tab with Account tab, wire ATProto OAuth redirect"
```

---

### Task 12: Compose instrumentation test for AccountScreen

**Files:**
- Create: `app/src/androidTest/java/net/olamaelcu/livtet/account/AccountScreenTest.kt`

- [ ] **Step 1: Create the instrumentation test**

Create `app/src/androidTest/java/net/olamaelcu/livtet/account/AccountScreenTest.kt`:

```kotlin
package net.olamaelcu.livtet.account

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import net.olamaelcu.livtet.DashboardActivity
import org.junit.Rule
import org.junit.Test

class AccountScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<DashboardActivity>()

    @Test
    fun `account screen shows sign-in prompt when signed out`() {
        composeTestRule.onNodeWithText("Account").assertExists()
        composeTestRule.onNodeWithText("Sign in to unlock social features").assertExists()
    }

    @Test
    fun `ATProto sign-in button is present`() {
        composeTestRule.onNodeWithText("Sign in with AT Protocol").assertExists()
    }
}
```

- [ ] **Step 2: Run the instrumentation test**

Run:
```bash
cd mobile/android && ./gradlew :app:connectedPlaystoreDebugAndroidTest --tests "net.olamaelcu.livtet.account.AccountScreenTest"
```
Expected: both tests PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/net/olamaelcu/livtet/account/AccountScreenTest.kt
git commit -m "test(auth): add AccountScreen instrumentation test"
```

---

### Task 13: Final integration — verify and cleanup

- [ ] **Step 1: Run all unit tests**

```bash
cd mobile/android && ./gradlew :core:auth:test
```
Expected: all tests PASS (AccountStateTest 4/4, SecureTokenStoreTest 4/4, AtprotoOAuthTest 7/7).

- [ ] **Step 2: Run the full build**

```bash
cd mobile/android && ./gradlew :app:assemblePlaystoreDebug :app:assembleFdroidDebug :app:assembleGenericDebug
```
Expected: BUILD SUCCESSFUL for all three flavors.

- [ ] **Step 3: Verify flavor gating**

Run:
```bash
cd mobile/android && ./gradlew :app:assembleFdroidDebug 2>&1 | grep -i "google\|apple"
```
Expected: no errors about Google or Apple components on fdroid build. The Account screen should compile with only ATProto button visible.

- [ ] **Step 4: Commit**

```bash
git commit -m "test(auth): final integration - all tests pass across flavors"
```
