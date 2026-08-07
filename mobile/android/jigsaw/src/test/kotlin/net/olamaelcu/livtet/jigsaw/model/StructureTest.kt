package net.olamaelcu.livtet.jigsaw.model

import org.junit.Assert.*
import org.junit.Test

class StructureTest {
    @Test fun `serialize all tabs`() {
        assertEquals("TTTT", serialize(Structure(Insert.Tab, Insert.Tab, Insert.Tab, Insert.Tab)))
    }
    @Test fun `serialize mixed`() {
        assertEquals("TST-", serialize(Structure(Insert.Tab, Insert.Slot, Insert.None, Insert.Tab)))
    }
    @Test fun `serialize deserialize round-trip`() {
        val original = Structure(Insert.Tab, Insert.Slot, Insert.None, Insert.Tab)
        assertEquals(original, deserialize(serialize(original)))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `deserialize rejects wrong length`() { deserialize("TS") }
}
