package net.olamaelcu.livtet.sync

import java.net.URI
import java.net.URLDecoder

/**
 * What a scanned QR code or a pasted string has to look like to name a peer.
 *
 * Three shapes are accepted, because a peer's node id reaches a phone in different ways depending
 * on how the desktop chose to show it:
 *
 * - a bare node id, which is what copying the id out of the desktop gives;
 * - `livtet://pair?node=<id>`, the form a pairing QR code carries;
 * - `https://livtet.olamaelcu.net/pair?node=<id>`, for a code rendered in a browser or printed on a
 *   page.
 *
 * Nothing here decides whether the id is *real*: only the iroh dial can tell, and it reports what
 * it rejected. This parser only strips the wrappers and refuses values that cannot be an id at all,
 * so the dialog can say "that is not a node id" without a network round trip.
 *
 * Parsing uses `java.net.URI`, not `android.net.Uri`, so the rules are testable as plain JVM code.
 */
object PairPayload {
    private const val QUERY_KEY = "node"

    /** Route and host the pairing links use. */
    private const val PAIR_SCHEME = "livtet"
    private const val PAIR_HOST = "pair"
    private const val PAIR_WEB_HOST = "livtet.olamaelcu.net"
    private const val PAIR_WEB_PATH = "/pair"

    private const val MIN_LENGTH = 16
    private const val MAX_LENGTH = 200

    /**
     * The node id in [raw], or `null` when the value carries none.
     *
     * Blank input is a miss, not an error: the field is empty until the reader scans or pastes.
     */
    fun nodeId(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val candidate =
            if (trimmed.contains("://") || trimmed.startsWith("$PAIR_SCHEME:")) {
                fromUri(trimmed) ?: return null
            } else {
                trimmed
            }

        return candidate.trim().takeIf(::looksLikeNodeId)
    }

    private fun fromUri(raw: String): String? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        val routed =
            when (scheme) {
                PAIR_SCHEME -> host == PAIR_HOST
                "https" -> host == PAIR_WEB_HOST && uri.path.orEmpty().trimEnd('/') == PAIR_WEB_PATH
                else -> false
            }
        if (!routed) return null
        return queryValue(uri.rawQuery)
    }

    /** Pull [QUERY_KEY] out of `a=1&node=2`, percent-decoding the value. */
    private fun queryValue(rawQuery: String?): String? =
        rawQuery?.split('&')?.firstNotNullOfOrNull { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) return@firstNotNullOfOrNull null
            if (!pair.substring(0, separator).equals(QUERY_KEY, ignoreCase = true)) {
                return@firstNotNullOfOrNull null
            }
            val value = pair.substring(separator + 1)
            runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
        }

    /**
     * Cheap shape check before the network is involved.
     *
     * iroh prints node ids as an unpadded, case-insensitive base32 string, but the exact alphabet
     * and length are the crate's business, so this only rejects values that clearly are not one:
     * too short, too long, or holding characters that never appear in an id (spaces, punctuation,
     * the `=` padding a copied base32 blob sometimes keeps).
     */
    private fun looksLikeNodeId(value: String): Boolean =
        value.length in MIN_LENGTH..MAX_LENGTH &&
            value.all { it.isLetterOrDigit() || it == '-' || it == '_' }
}
