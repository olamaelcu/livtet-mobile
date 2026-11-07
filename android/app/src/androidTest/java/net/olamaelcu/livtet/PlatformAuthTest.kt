package net.olamaelcu.livtet

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The half of the sign-in that can be checked without a browser.
 *
 * On-device because the encoding goes through `android.util.Base64`: the app supports API 24, where
 * `java.util.Base64` does not exist, and the JVM test runtime stubs the Android one.
 *
 * PKCE is the only thing protecting a public client's code, so the digest has to be exactly RFC
 * 7636's — a mismatch here costs a working redirect and looks like a server bug.
 */
@RunWith(AndroidJUnit4::class)
class PlatformAuthTest {
    @Test
    fun theChallengeMatchesTheRfcVector() {
        // RFC 7636 appendix B.
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            PlatformAuth.challenge(verifier),
        )
    }

    @Test
    fun aVerifierIsBase64UrlWithoutPadding() {
        repeat(20) {
            val verifier = PlatformAuth.newVerifier()

            assertTrue("length ${verifier.length} is within RFC 7636", verifier.length in 43..128)
            assertTrue(verifier.all { it.isLetterOrDigit() || it in "-._~" })
            assertFalse(verifier.contains('='))
            assertFalse(verifier.contains('+'))
            assertFalse(verifier.contains('/'))
        }
    }

    @Test
    fun twoSignInsGetDifferentVerifiersAndStates() {
        assertNotEquals(PlatformAuth.newVerifier(), PlatformAuth.newVerifier())
        assertNotEquals(PlatformAuth.newState(), PlatformAuth.newState())
    }

    @Test
    fun theAuthorizeUrlAsksForTheFeedWithPkceAndState() {
        val pending = PlatformAuth.Pending(verifier = "v".repeat(64), state = "state-123")

        val url = PlatformAuth.authorizeUrl(pending)

        assertTrue(url.startsWith("${PlatformAuth.PLATFORM_ORIGIN}/auth?"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("client_id=${PlatformAuth.CLIENT_ID}"))
        // The redirect must be the one the manifest filter and the AS client agree on.
        assertTrue(url.contains("redirect_uri=livtet%3A%2F%2Foauth%2Fcallback"))
        assertTrue(url.contains("scope=graph%3AreadFeed+offline_access"))
        assertTrue(url.contains("resource=${escaped(PlatformAuth.RESOURCE)}"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("code_challenge=${PlatformAuth.challenge(pending.verifier)}"))
        assertTrue(url.contains("state=${pending.state}"))
    }

    private fun escaped(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
}
