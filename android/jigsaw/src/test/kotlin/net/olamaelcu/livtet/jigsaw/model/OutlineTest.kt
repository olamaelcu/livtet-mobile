package net.olamaelcu.livtet.jigsaw.model

import org.junit.Assert.*
import org.junit.Test

class OutlineTest {
    @Test fun `Squared outline produces non-empty point list`() {
        val piece = Piece(up = Insert.Tab, down = Insert.Slot, left = Insert.None, right = Insert.None)
        val points = OutlineStyle.Squared.draw(piece, 100f, 4f)
        assertTrue(points.isNotEmpty()); assertEquals(32, points.size)
    }
    @Test fun `Rounded outline produces non-empty point list`() {
        val piece = Piece(up = Insert.Tab, down = Insert.Slot, left = Insert.None, right = Insert.Tab)
        assertTrue(OutlineStyle.Rounded.draw(piece, 150f, 0f).isNotEmpty())
    }
    @Test fun `Squared is not bezier`() { assertFalse(OutlineStyle.Squared.isBezier()) }
    @Test fun `Rounded is not bezier`() { assertFalse(OutlineStyle.Rounded.isBezier()) }
}
