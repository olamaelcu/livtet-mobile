package net.olamaelcu.livtet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the sync screen accepts from the keyboard.
 *
 * A typo must be refused before the app dials: a claim that fails at the transport looks like the
 * other device not answering, which sends a reader looking at the wrong end of the problem.
 */
class PairingTicketTest {
    private val id = "0123456789ABCDEF".repeat(4)

    @Test
    fun aNodeIdIsAcceptedAndLowercased() {
        assertEquals(id.lowercase(), PairingTicket.nodeId(id))
        assertEquals(id.lowercase(), PairingTicket.nodeId("  ${id.lowercase()}\n"))
    }

    @Test
    fun anythingElseIsRefused() {
        assertNull("too short", PairingTicket.nodeId(id.drop(1)))
        assertNull("too long", PairingTicket.nodeId(id + "a"))
        assertNull("not hex", PairingTicket.nodeId("z".repeat(64)))
        assertNull("a link is not an id", PairingTicket.nodeId("livtet://sync?v=2&nodeId=$id"))
        assertNull("empty", PairingTicket.nodeId("   "))
    }

    @Test
    fun aLinkIsRecognisedByItsScheme() {
        assertTrue(PairingTicket.isLink("livtet://sync?v=2&nodeId=$id"))
        assertTrue(PairingTicket.isLink("  LIVTET://pair?node=$id"))
        assertFalse("a bare id is not a link", PairingTicket.isLink(id))
        assertFalse(PairingTicket.isLink("https://example.com/$id"))
    }
}
