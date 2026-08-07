package net.olamaelcu.livtet.jigsaw.model

import org.junit.Assert.*
import org.junit.Test

class PuzzleTest {
    @Test fun `newPiece adds to puzzle`() {
        val puzzle = Puzzle()
        val piece = puzzle.newPiece()
        assertEquals(1, puzzle.pieces.size); assertEquals(puzzle, piece.puzzle)
    }
    @Test fun `autoconnect connects adjacent matching pieces`() {
        val puzzle = Puzzle(Size(Vector(50f, 50f)), 20f)
        val a = puzzle.newPiece(right = Insert.Tab); val b = puzzle.newPiece(left = Insert.Slot)
        a.locateAt(0f, 0f); b.locateAt(95f, 0f)
        puzzle.autoconnect()
        assertTrue(a.connected); assertTrue(b.connected)
    }
    @Test fun `translate moves all pieces`() {
        val puzzle = Puzzle()
        val a = puzzle.newPiece(); a.locateAt(0f, 0f)
        val b = puzzle.newPiece(); b.locateAt(100f, 0f)
        puzzle.translate(10f, 20f)
        assertEquals(10f, a.centralAnchor?.x); assertEquals(110f, b.centralAnchor?.x)
    }
    @Test fun `connection requirement blocks incompatible`() {
        val puzzle = Puzzle(Size(Vector(50f, 50f)), 20f)
        val a = puzzle.newPiece(right = Insert.Tab); a.annotate(mapOf("color" to "red"))
        val b = puzzle.newPiece(left = Insert.Slot); b.annotate(mapOf("color" to "blue"))
        a.locateAt(0f, 0f); b.locateAt(95f, 0f)
        puzzle.attachConnectionRequirement { one, other -> one.metadata["color"] == other.metadata["color"] }
        puzzle.autoconnect()
        assertFalse(a.connected)
    }
}
