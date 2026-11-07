package net.olamaelcu.livtet.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairPayloadTest {
    private val nodeId = "abcdefghijklmnopqrstuvwxyz234567"

    @Test
    fun `a bare node id is taken as is`() {
        assertEquals(nodeId, PairPayload.nodeId(nodeId))
        assertEquals(nodeId, PairPayload.nodeId("  $nodeId\n"), "surrounding whitespace is trimmed")
    }

    @Test
    fun `the livtet pairing link yields its node`() {
        assertEquals(nodeId, PairPayload.nodeId("livtet://pair?node=$nodeId"))
    }

    @Test
    fun `the web pairing link yields its node`() {
        assertEquals(nodeId, PairPayload.nodeId("https://livtet.olamaelcu.net/pair?node=$nodeId"))
        assertEquals(
            nodeId,
            PairPayload.nodeId("https://livtet.olamaelcu.net/pair/?node=$nodeId"),
            "a trailing slash is still the pairing route",
        )
    }

    @Test
    fun `a percent-encoded node is decoded`() {
        assertEquals(nodeId, PairPayload.nodeId("livtet://pair?node=${nodeId.replace("a", "%61")}"))
    }

    @Test
    fun `other links are refused rather than dialled`() {
        assertNull(
            PairPayload.nodeId("https://livtet.olamaelcu.net/book/01ARZ3NDEKTSV4RRFFQ69G5FAV"),
        )
        assertNull(PairPayload.nodeId("https://example.com/pair?node=$nodeId"))
        assertNull(PairPayload.nodeId("livtet://book?node=$nodeId"))
        assertNull(PairPayload.nodeId("livtet://pair?node="), "a link with no node names nobody")
        assertNull(
            PairPayload.nodeId("livtet://pair?token=$nodeId"),
            "a different parameter is not a node",
        )
    }

    @Test
    fun `values that cannot be a node id are refused`() {
        assertNull(PairPayload.nodeId(""), "empty input is a miss")
        assertNull(PairPayload.nodeId("   "))
        assertNull(PairPayload.nodeId("not a node id"), "spaces cannot appear in an id")
        assertNull(PairPayload.nodeId("short"), "too short to be an id")
        assertNull(PairPayload.nodeId("a".repeat(201)), "too long to be an id")
        assertNull(PairPayload.nodeId("$nodeId="), "base32 padding is not part of the id form")
    }
}
