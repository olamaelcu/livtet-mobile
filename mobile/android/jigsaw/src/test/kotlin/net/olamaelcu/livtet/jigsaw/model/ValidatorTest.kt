package net.olamaelcu.livtet.jigsaw.model

import org.junit.Assert.*
import org.junit.Test

class ValidatorTest {
    @Test fun `nullValidator is always invalid`() {
        val v = NullValidator(); assertTrue(v.isNull); assertFalse(v.isValid(Puzzle()))
    }
    @Test fun `puzzleValidator checks connected state`() {
        val puzzle = Puzzle(Size(Vector(50f, 50f)), 20f)
        val a = puzzle.newPiece(right = Insert.Tab); val b = puzzle.newPiece(left = Insert.Slot)
        a.locateAt(0f, 0f); b.locateAt(95f, 0f)
        puzzle.autoconnect()
        assertTrue(PuzzleValidator { p -> p.connected }.isValid(puzzle))
    }
    @Test fun `onValid fires when transitioning to valid`() {
        val puzzle = Puzzle(Size(Vector(50f, 50f)), 20f)
        val a = puzzle.newPiece(right = Insert.Tab); val b = puzzle.newPiece(left = Insert.Slot)
        a.locateAt(0f, 0f); b.locateAt(95f, 0f)
        var fired = false
        val v = PuzzleValidator { p -> p.connected }; v.onValid { fired = true }
        puzzle.attachValidator(v); puzzle.autoconnect(); puzzle.validate()
        assertTrue(fired)
    }
}
