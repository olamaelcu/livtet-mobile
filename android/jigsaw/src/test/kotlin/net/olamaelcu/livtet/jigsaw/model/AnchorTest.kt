package net.olamaelcu.livtet.jigsaw.model

import org.junit.Assert.*
import org.junit.Test

class AnchorTest {
    @Test
    fun `translate moves anchor`() {
        val a = Anchor(0f, 0f)
        a.translate(5f, 3f)
        assertEquals(5f, a.x)
        assertEquals(3f, a.y)
    }

    @Test
    fun `translated returns new anchor`() {
        val a = Anchor(0f, 0f)
        val b = a.translated(5f, 3f)
        assertEquals(0f, a.x) // original unchanged
        assertEquals(5f, b.x)
        assertEquals(3f, b.y)
    }

    @Test
    fun `closeTo within tolerance`() {
        val a = Anchor(10f, 10f)
        val b = Anchor(12f, 12f)
        assertTrue(a.closeTo(b, 3f))
        assertFalse(a.closeTo(b, 1f))
    }

    @Test
    fun `closeTo handles negative difference`() {
        val a = Anchor(10f, 10f)
        val b = Anchor(8f, 8f)
        assertTrue(a.closeTo(b, 3f))
    }

    @Test
    fun `diff returns coordinate differences`() {
        val a = Anchor(10f, 20f)
        val b = Anchor(3f, 5f)
        val (dx, dy) = a.diff(b)
        assertEquals(7f, dx)
        assertEquals(15f, dy)
    }

    @Test
    fun `isAt checks coordinates`() {
        val a = Anchor(5f, 5f)
        assertTrue(a.isAt(5f, 5f))
        assertFalse(a.isAt(5f, 6f))
    }

    @Test
    fun `asVector converts to vector`() {
        val a = Anchor(3f, 7f)
        assertEquals(Vector(3f, 7f), a.asVector())
    }

    @Test
    fun `import creates anchor from vector`() {
        val v = Vector(4f, 9f)
        val a = Anchor.import(v)
        assertEquals(4f, a.x)
        assertEquals(9f, a.y)
    }
}
