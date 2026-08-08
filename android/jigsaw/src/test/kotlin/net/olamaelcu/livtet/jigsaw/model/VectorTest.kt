package net.olamaelcu.livtet.jigsaw.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorTest {
    @Test
    fun `cast scalar produces equal components`() {
        val v = Vector.cast(5f)
        assertEquals(5f, v.x)
        assertEquals(5f, v.y)
    }

    @Test
    fun `multiply by scalar`() {
        val v = Vector.multiply(Vector(2f, 3f), 2f)
        assertEquals(Vector(4f, 6f), v)
    }

    @Test
    fun `plus two vectors`() {
        val v = Vector.plus(Vector(1f, 2f), Vector(3f, 4f))
        assertEquals(Vector(4f, 6f), v)
    }

    @Test
    fun `minus two vectors`() {
        val v = Vector.minus(Vector(5f, 6f), Vector(1f, 2f))
        assertEquals(Vector(4f, 4f), v)
    }

    @Test
    fun `divide two vectors`() {
        val v = Vector.divide(Vector(6f, 9f), Vector(2f, 3f))
        assertEquals(Vector(3f, 3f), v)
    }

    @Test
    fun `equal with default delta`() {
        assertTrue(Vector.equal(Vector(1f, 1f), Vector(1f, 1f)))
    }

    @Test
    fun `equal within delta`() {
        assertTrue(Vector.equal(Vector(1f, 1f), Vector(1.005f, 1.005f), delta = 0.01f))
    }

    @Test
    fun `innerMin returns smaller component`() {
        assertEquals(2f, Vector.innerMin(Vector(3f, 2f)))
    }

    @Test
    fun `innerMax returns larger component`() {
        assertEquals(4f, Vector.innerMax(Vector(1f, 4f)))
    }

    @Test
    fun `diff returns coordinate differences`() {
        val result = Vector.diff(Vector(5f, 8f), Vector(2f, 3f))
        assertEquals(3f, result.first)
        assertEquals(5f, result.second)
    }
}
