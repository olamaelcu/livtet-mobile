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
        val parts = proof.split(".")
        assertEquals(3, parts.size)
        val header = String(java.util.Base64.getUrlDecoder().decode(parts[0]))
        assertTrue(header.contains("\"typ\""))
        assertTrue(header.contains("dpop+jwt"))
        assertTrue(header.contains("\"alg\""))
        assertTrue(header.contains("ES256"))
        assertTrue(header.contains("\"jwk\""))
        val payload = String(java.util.Base64.getUrlDecoder().decode(parts[1]))
        assertTrue(payload.contains("\"htm\""))
        assertTrue(payload.contains("POST"))
        assertTrue(payload.contains("\"htu\""))
        assertTrue(payload.contains("https://bsky.social/oauth/token"))
        assertTrue(payload.contains("\"iat\""))
        assertTrue(payload.contains("\"jti\""))
    }
}
