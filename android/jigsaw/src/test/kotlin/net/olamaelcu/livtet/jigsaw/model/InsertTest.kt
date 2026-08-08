package net.olamaelcu.livtet.jigsaw.model
import org.junit.Assert.*; import org.junit.Test
class InsertTest {
    @Test fun tabMatchesSlot() { assertTrue(Insert.Tab.match(Insert.Slot)) }
    @Test fun slotMatchesTab() { assertTrue(Insert.Slot.match(Insert.Tab)) }
    @Test fun tabNotMatchTab() { assertFalse(Insert.Tab.match(Insert.Tab)) }
    @Test fun noneMatchesNothing() { assertFalse(Insert.None.match(Insert.Tab)) }
    @Test fun tabComplementSlot() { assertEquals(Insert.Slot, Insert.Tab.complement()) }
    @Test fun slotComplementTab() { assertEquals(Insert.Tab, Insert.Slot.complement()) }
    @Test fun noneComplementNone() { assertEquals(Insert.None, Insert.None.complement()) }
    @Test fun serialize() { assertEquals('T', Insert.Tab.serialize()); assertEquals('S', Insert.Slot.serialize()); assertEquals('-', Insert.None.serialize()) }
}
