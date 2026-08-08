package net.olamaelcu.livtet.jigsaw.model

import org.junit.Assert.*
import org.junit.Test

class ManufacturerTest {
    @Test fun `build produces correct piece count`() {
        val m = Manufacturer(); m.withDimensions(3, 2)
        assertEquals(6, m.build().pieces.size)
    }
    @Test fun `build assigns ids`() {
        val m = Manufacturer(); m.withDimensions(2, 2)
        val puzzle = m.build()
        assertEquals("1", puzzle.pieces[0].id); assertEquals("2", puzzle.pieces[1].id)
        assertEquals("3", puzzle.pieces[2].id); assertEquals("4", puzzle.pieces[3].id)
    }
    @Test fun `build with metadata`() {
        val m = Manufacturer(); m.withDimensions(2, 2)
        m.withMetadata(listOf(mapOf("color" to "red"), mapOf("color" to "blue"), mapOf("color" to "green"), mapOf("color" to "yellow")))
        val puzzle = m.build()
        assertEquals("red", puzzle.pieces[0].metadata["color"])
        assertEquals("blue", puzzle.pieces[1].metadata["color"])
    }
    @Test fun `build produces connectable puzzle`() {
        val m = Manufacturer(); m.withDimensions(2, 2)
        val puzzle = m.build(); puzzle.autoconnect()
        assertTrue(puzzle.connected)
    }
}
