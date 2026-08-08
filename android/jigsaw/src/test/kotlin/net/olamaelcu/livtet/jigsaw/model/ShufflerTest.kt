package net.olamaelcu.livtet.jigsaw.model

import org.junit.Assert.*
import org.junit.Test

class ShufflerTest {
    @Test fun `randomShuffler returns same count`() {
        val piece = Piece(); piece.locateAt(10f, 10f)
        assertEquals(1, randomShuffler(100f, 100f)(listOf(piece)).size)
    }
    @Test fun `randomShuffler positions within bounds`() {
        val pieces = (0..9).map { val p = Piece(); p.locateAt(50f, 50f); p }
        randomShuffler(100f, 100f)(pieces).forEach { v ->
            assertTrue(v.x in 0f..100f); assertTrue(v.y in 0f..100f)
        }
    }
    @Test fun `noopShuffler returns same positions`() {
        val p = Piece(); p.locateAt(42f, 73f)
        assertEquals(Vector(42f, 73f), noopShuffler(listOf(p))[0])
    }
    @Test fun `noiseShuffler offsets slightly`() {
        val p = Piece(); p.locateAt(100f, 100f)
        val result = noiseShuffler(Vector(10f, 10f))(listOf(p))
        assertTrue(result[0].x in 90f..110f)
    }
}
