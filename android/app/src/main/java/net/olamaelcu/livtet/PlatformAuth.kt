package net.olamaelcu.livtet

import android.content.Context
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Signing in to the platform, as an OAuth client.
 *
 * `:core:auth` signs a reader into their *PDS* (ATProto). This is the other leg: the platform's own
 * authorization server issues a token addressed to the feed, and the phone presents it on `GET
 * /api/graph/feed`, which a browser route guards with a cookie the app does not have.
 *
 * The client is public (no secret), so PKCE is what protects the code: the verifier never leaves
 * this device, and only its S256 digest travels in the authorize URL.
 */
object PlatformAuth {
    private const val TAG = "PlatformAuth"

    /** Public origin of the platform and its authorization server. */
    const val PLATFORM_ORIGIN = "https://livtet.olamaelcu.net"

    /** The native client the authorization server registers by default. */
    const val CLIENT_ID = "livtet-android"

    /** Must equal the AS's `AS_MOBILE_REDIRECT_URI` and the manifest filter. */
    const val REDIRECT_URI = "livtet://oauth/callback"

    /** The feed resource, named as the AS expects it (RFC 8707). */
    val RESOURCE = "$PLATFORM_ORIGIN/api/graph/feed"

    private const val SCOPE = "graph:readFeed offline_access"
    private const val PREFS = "livtet.platform"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_VERIFIER = "pending_verifier"
    private const val KEY_STATE = "pending_state"

    /** Refresh this long before expiry, so a page load does not race the clock. */
    private const val REFRESH_SKEW_MS = 60_000L

    /** What a sign-in in progress needs to remember until the browser returns. */
    data class Pending(val verifier: String, val state: String)

    /** One sign-in that the browser is being sent to complete. */
    data class SignIn(val url: String, val pending: Pending)

    /** The tokens the platform issued, as stored. */
    data class Tokens(val access: String, val refresh: String?, val expiresAt: Long) {
        fun isExpiring(now: Long = System.currentTimeMillis()) = now >= expiresAt - REFRESH_SKEW_MS
    }

    /** Whether a usable refresh or access token is on the device. */
    fun isSignedIn(context: Context): Boolean =
        read(context)?.let { it.refresh != null || !it.isExpiring() } == true

    /**
     * Build the authorize URL and remember what the callback will need.
     *
     * The pending verifier is written before the browser opens: the app may be killed while the
     * reader is signing in, and a callback with no verifier cannot be exchanged.
     */
    fun beginSignIn(context: Context, now: Long = System.currentTimeMillis()): SignIn {
        val pending = Pending(verifier = newVerifier(), state = newState())
        val url = authorizeUrl(pending)
        prefs(context)
            .edit()
            .putString(KEY_VERIFIER, pending.verifier)
            .putString(KEY_STATE, pending.state)
            .apply()
        return SignIn(url = url, pending = pending)
    }

    /**
     * Finish the flow from the redirect the browser came back with.
     *
     * Returns null when this redirect is not ours (other `livtet://` links reach the same activity)
     * and throws when it is ours but cannot be trusted.
     */
    suspend fun completeSignIn(context: Context, uri: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val parsed = runCatching { android.net.Uri.parse(uri) }.getOrNull()
            if (parsed == null || parsed.scheme != "livtet" || parsed.host != "oauth") {
                return@withContext Result.failure(IllegalArgumentException("not a sign-in"))
            }
            val error = parsed.getQueryParameter("error")
            if (error != null) {
                clearPending(context)
                return@withContext Result.failure(
                    IllegalStateException(
                        error +
                            (parsed.getQueryParameter("error_description")?.let { ": $it" } ?: "")
                    )
                )
            }
            val code =
                parsed.getQueryParameter("code")
                    ?: return@withContext Result.failure(
                        IllegalStateException("no code in the redirect")
                    )
            val state = parsed.getQueryParameter("state")
            val prefs = prefs(context)
            val expected = prefs.getString(KEY_STATE, null)
            // A redirect that does not match the sign-in this app started is not
            // one to exchange: a state mismatch is the CSRF check.
            if (expected == null || state == null || expected != state) {
                // A state mismatch is the CSRF check failing, and it is otherwise
                // indistinguishable from a stale browser tab. The values are not
                // secrets — both travelled in URLs — so they are worth logging.
                android.util.Log.w(
                    TAG,
                    "sign-in state mismatch (expected=$expected received=$state)",
                )
                clearPending(context)
                return@withContext Result.failure(
                    IllegalStateException("the sign-in does not match the one this app started")
                )
            }
            val verifier = prefs.getString(KEY_VERIFIER, null)
            if (verifier == null) {
                return@withContext Result.failure(
                    IllegalStateException("the sign-in was lost; try again")
                )
            }
            try {
                val tokens =
                    exchange(
                        mapOf(
                            "grant_type" to "authorization_code",
                            "code" to code,
                            "redirect_uri" to REDIRECT_URI,
                            "client_id" to CLIENT_ID,
                            "code_verifier" to verifier,
                        )
                    )
                store(context, tokens)
                clearPending(context)
                Result.success(Unit)
            } catch (error: kotlinx.coroutines.CancellationException) {
                // The caller went away mid-exchange; that is not a refusal.
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    /**
     * A token to send on a request, refreshing first when it is about to expire.
     *
     * Returns null when the reader is not signed in, or when the refresh was refused — the caller
     * treats both as "ask them to sign in".
     */
    suspend fun accessToken(context: Context): String? =
        withContext(Dispatchers.IO) {
            val tokens = read(context) ?: return@withContext null
            if (!tokens.isExpiring()) return@withContext tokens.access
            val refresh =
                tokens.refresh ?: return@withContext tokens.access.takeIf { !tokens.isExpiring() }
            try {
                val refreshed =
                    exchange(
                        mapOf(
                            "grant_type" to "refresh_token",
                            "refresh_token" to refresh,
                            "client_id" to CLIENT_ID,
                        )
                    )
                store(context, refreshed)
                refreshed.access
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                // A refused refresh is a session that is over; keeping the dead
                // token would turn every screen into a 401.
                clear(context)
                null
            }
        }

    /** Forget the session. */
    fun signOut(context: Context) = clear(context)

    /** The authorize URL, with PKCE S256 and the feed named as its resource. */
    internal fun authorizeUrl(pending: Pending): String = buildString {
        append(PLATFORM_ORIGIN).append("/auth")
        append("?response_type=code")
        append("&client_id=").append(encode(CLIENT_ID))
        append("&redirect_uri=").append(encode(REDIRECT_URI))
        append("&scope=").append(encode(SCOPE))
        append("&resource=").append(encode(RESOURCE))
        append("&code_challenge=").append(encode(challenge(pending.verifier)))
        append("&code_challenge_method=S256")
        append("&state=").append(encode(pending.state))
    }

    /** RFC 7636 verifier: 43–128 characters from the unreserved set. */
    internal fun newVerifier(random: SecureRandom = SecureRandom()): String =
        urlSafe(ByteArray(32).also(random::nextBytes))

    /** A `state` that the callback must repeat, so a stray redirect is ignored. */
    internal fun newState(random: SecureRandom = SecureRandom()): String =
        urlSafe(ByteArray(16).also(random::nextBytes))

    /** S256: base64url(sha256(verifier)), unpadded. */
    internal fun challenge(verifier: String): String {
        val digest =
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return urlSafe(digest)
    }

    /**
     * base64url without padding.
     *
     * `java.util.Base64` needs API 26 and this app supports 24, so the platform encoder does the
     * work; `NO_PADDING` is what makes the digest match RFC 7636.
     */
    private fun urlSafe(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun exchange(form: Map<String, String>): Tokens {
        val body =
            form.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        val connection =
            (URL("$PLATFORM_ORIGIN/token").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("content-type", "application/x-www-form-urlencoded")
                setRequestProperty("accept", "application/json")
            }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val text =
                (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException(
                    "the authorization server refused the exchange ($status): $text"
                )
            }
            val json = JSONObject(text)
            val access =
                json.optString("access_token").takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException("no access token in the response")
            val expiresIn = json.optLong("expires_in", 3600L)
            return Tokens(
                access = access,
                refresh = json.optString("refresh_token").takeIf { it.isNotEmpty() },
                expiresAt = System.currentTimeMillis() + expiresIn * 1000,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun store(context: Context, tokens: Tokens) {
        prefs(context)
            .edit()
            .putString(KEY_ACCESS, tokens.access)
            .putString(KEY_REFRESH, tokens.refresh)
            .putLong(KEY_EXPIRES_AT, tokens.expiresAt)
            .apply()
    }

    private fun read(context: Context): Tokens? {
        val prefs = prefs(context)
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        return Tokens(
            access = access,
            refresh = prefs.getString(KEY_REFRESH, null),
            expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L),
        )
    }

    private fun clear(context: Context) {
        prefs(context).edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRES_AT).apply()
    }

    private fun clearPending(context: Context) {
        prefs(context).edit().remove(KEY_VERIFIER).remove(KEY_STATE).apply()
    }
}
