package net.olamaelcu.livtet.core.auth.provider

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec
import java.util.Base64
import java.util.UUID

object AtprotoAuthProvider {

    private val secureRandom = SecureRandom()
    private val base64Encoder = Base64.getUrlEncoder().withoutPadding()
    private val base64Decoder = Base64.getUrlDecoder()

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return base64Encoder.encodeToString(bytes)
    }

    fun computeCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64Encoder.encodeToString(hashBytes)
    }

    fun generateDpopKeypair(): String {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec("secp256r1")
        keyPairGenerator.initialize(ecSpec, secureRandom)
        val keyPair = keyPairGenerator.generateKeyPair()

        val publicKey = keyPair.public as ECPublicKey
        val privateKey = keyPair.private as ECPrivateKey

        val w = publicKey.w
        val x = w.affineX
        val y = w.affineY
        val d = privateKey.s

        val xEncoded = base64Encoder.encodeToString(toFixedUnsignedBytes(x, 32))
        val yEncoded = base64Encoder.encodeToString(toFixedUnsignedBytes(y, 32))
        val dEncoded = base64Encoder.encodeToString(toFixedUnsignedBytes(d, 32))

        return """{"kty":"EC","crv":"P-256","x":"$xEncoded","y":"$yEncoded","d":"$dEncoded"}"""
    }

    fun createDpopProof(keypair: String, httpMethod: String, httpUri: String, nonce: String): String {
        val xStart = keypair.indexOf("\"x\":\"") + 5
        val xEnd = keypair.indexOf("\"", xStart)
        val yStart = keypair.indexOf("\"y\":\"") + 5
        val yEnd = keypair.indexOf("\"", yStart)
        val dStart = keypair.indexOf("\"d\":\"") + 5
        val dEnd = keypair.indexOf("\"", dStart)

        val x = keypair.substring(xStart, xEnd)
        val y = keypair.substring(yStart, yEnd)
        val d = keypair.substring(dStart, dEnd)

        val publicJwk = """{"kty":"EC","crv":"P-256","x":"$x","y":"$y"}"""

        val headerJson = """{"typ":"dpop+jwt","alg":"ES256","jwk":$publicJwk}"""
        val headerB64 = base64Encoder.encodeToString(headerJson.toByteArray(Charsets.UTF_8))

        val now = System.currentTimeMillis() / 1000
        val jti = UUID.randomUUID().toString()
        val payloadJson = """{"htm":"$httpMethod","htu":"$httpUri","iat":$now,"jti":"$jti"}"""
        val payloadB64 = base64Encoder.encodeToString(payloadJson.toByteArray(Charsets.UTF_8))

        val signingInput = "$headerB64.$payloadB64"

        val keyFactory = KeyFactory.getInstance("EC")
        val ecParams = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }.getParameterSpec(ECParameterSpec::class.java)

        val dBytes = base64Decoder.decode(d)
        val dBigInt = BigInteger(1, dBytes)
        val privateKeySpec = ECPrivateKeySpec(dBigInt, ecParams)
        val privateKey = keyFactory.generatePrivate(privateKeySpec)

        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(signingInput.toByteArray(Charsets.US_ASCII))
        val sigBytes = signature.sign()
        val sigB64 = base64Encoder.encodeToString(sigBytes)

        return "$headerB64.$payloadB64.$sigB64"
    }

    private fun toFixedUnsignedBytes(value: BigInteger, length: Int): ByteArray {
        val bytes = value.toByteArray()
        if (bytes.size == length) return bytes
        if (bytes.size == length + 1 && bytes[0] == 0.toByte()) return bytes.copyOfRange(1, bytes.size)
        val padded = ByteArray(length)
        val offset = if (bytes.size > length) bytes.size - length else 0
        val destOffset = if (bytes.size > length) 0 else length - bytes.size
        val copyLen = if (bytes.size > length) length else bytes.size
        System.arraycopy(bytes, offset, padded, destOffset, copyLen)
        return padded
    }

    class AtprotoOAuthException(message: String) : Exception(message)
}
