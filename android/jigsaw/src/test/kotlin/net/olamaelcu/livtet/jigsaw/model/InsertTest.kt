package net.olamaelcu.livtet.jigsaw.model

import org.junit.Assert.*
import org.junit.Test

class InsertTest {
    @Test fun `Tab matches Slot`() { assertTrue(Insert.Tab.match(Insert.Slot)) }
    @Test fun `Slot matches Tab`() { assertTrue(Insert.Slot.match(Insert.Tab)) }
    @Test fun `Tab does not match Tab`() { assertFalse(Insert.Tab.match(Insert.Tab)) }
    @Test fun `None matches nothing`() { assertFalse(Insert.None.match(Insert.Tab)) }
    @Test fun `Tab complement is Slot`() { assertEquals(Insert.Slot, Insert.Tab.complement()) }
    @Test fun `Slot complement is Tab`() { assertEquals(Insert.Tab, Insert.Slot.complement()) }
    @Test fun `None complement is None`() { assertEquals(Insert.None, Insert.None.complement()) }
    @Test fun `serialize produces correct chars`() {
        assertEquals('T', Insert.Tab.serialize())
        assertEquals('S', Insert.Slot.serialize())
        assertEquals('-', Insert.None.serialize())
    }
}
