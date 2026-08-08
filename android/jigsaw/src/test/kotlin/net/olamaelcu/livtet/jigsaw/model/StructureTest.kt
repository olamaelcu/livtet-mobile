package net.olamaelcu.livtet.jigsaw.model
import org.junit.Assert.*; import org.junit.Test
class StructureTest {
    @Test fun serializeAllTabs() { assertEquals("TTTT", serialize(Structure(Insert.Tab, Insert.Tab, Insert.Tab, Insert.Tab))) }
    @Test fun serializeMixed() { assertEquals("TS-T", serialize(Structure(Insert.Tab, Insert.Slot, Insert.None, Insert.Tab))) }
    @Test fun roundTrip() { val o = Structure(Insert.Tab, Insert.Slot, Insert.None, Insert.Tab); assertEquals(o, deserialize(serialize(o))) }
    @Test(expected = IllegalArgumentException::class) fun rejectsWrongLength() { deserialize("TS") }
}
