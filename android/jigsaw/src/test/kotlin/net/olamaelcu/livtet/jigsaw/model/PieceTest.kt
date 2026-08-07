package net.olamaelcu.livtet.jigsaw.model

import org.junit.Assert.*
import org.junit.Test

class PieceTest {
    @Test fun `locateAt sets central anchor`() {
        val p = Piece(); p.locateAt(10f, 20f)
        assertEquals(10f, p.centralAnchor?.x); assertEquals(20f, p.centralAnchor?.y)
    }
    @Test fun `translate moves piece and fires listener`() {
        val p = Piece(); p.locateAt(0f, 0f)
        var fired = false; p.onTranslate { _, _, _ -> fired = true }
        p.translate(5f, 3f)
        assertEquals(5f, p.centralAnchor?.x); assertEquals(3f, p.centralAnchor?.y); assertTrue(fired)
    }
    @Test fun `connected returns false when no connections`() { assertFalse(Piece().connected) }
    @Test fun `connected returns true with any connection`() {
        val a = Piece(); a.rightConnection = Piece(); assertTrue(a.connected)
    }
    @Test fun `disconnect clears all connections`() {
        val puzzle = Puzzle(Size(Vector(50f, 50f)))
        val a = puzzle.newPiece(right = Insert.Tab); val b = puzzle.newPiece(left = Insert.Slot)
        a.locateAt(0f, 0f); b.locateAt(100f, 0f)
        a.tryConnectWith(b); assertTrue(a.connected)
        a.disconnect(); assertFalse(a.connected); assertNull(a.rightConnection); assertNull(b.leftConnection)
    }
    @Test fun `tryConnectWith connects matching inserts when close`() {
        val puzzle = Puzzle(Size(Vector(50f, 50f)), 20f)
        val a = puzzle.newPiece(right = Insert.Tab); val b = puzzle.newPiece(left = Insert.Slot)
        a.locateAt(0f, 0f); b.locateAt(95f, 0f)
        a.tryConnectWith(b); assertTrue(a.connected)
    }
    @Test(expected = IllegalStateException::class)
    fun `centerAround twice throws`() { val p = Piece(); p.centerAround(Anchor(0f, 0f)); p.centerAround(Anchor(1f, 1f)) }
    @Test fun `recenterAround repositions piece`() {
        val p = Piece(); p.locateAt(0f, 0f); p.recenterAround(Anchor(50f, 50f))
        assertEquals(50f, p.centralAnchor?.x); assertEquals(50f, p.centralAnchor?.y)
    }
    @Test fun `annotate merges metadata`() {
        val p = Piece(); p.annotate(mapOf("color" to "#ff0000", "id" to "1"))
        assertEquals("#ff0000", p.metadata["color"]); assertEquals("1", p.metadata["id"])
    }
}
