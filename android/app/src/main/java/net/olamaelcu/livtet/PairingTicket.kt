package net.olamaelcu.livtet

/**
 * What a reader may paste into the sync screen to reach a device discovery did not find.
 *
 * Two shapes exist and the reader should not have to know which they hold: a bare node id (64 hex
 * characters, what the desktop shows under "Copy"), and a `livtet://sync?…` or `livtet://pair?…`
 * link (what a QR carries, and what the platform's pairing panel accepts). The link is the ffi's to
 * decode; this only decides which one a string is, so a typo is refused before a dial is made.
 */
object PairingTicket {
    /** An iroh endpoint id: 64 hexadecimal characters, no separators. */
    private val NODE_ID = Regex("^[0-9a-fA-F]{64}$")

    /** Whether the input is a pairing link rather than a node id. */
    fun isLink(input: String): Boolean = input.trim().startsWith("livtet://", ignoreCase = true)

    /**
     * The node id in canonical form, or null when this is not one.
     *
     * Lowercase, because the id is hexadecimal and a pasted uppercase id names the same endpoint;
     * the FFI compares ids as strings.
     */
    fun nodeId(input: String): String? = input.trim().takeIf(NODE_ID::matches)?.lowercase()
}
