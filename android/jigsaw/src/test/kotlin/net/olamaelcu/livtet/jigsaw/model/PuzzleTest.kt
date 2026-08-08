package net.olamaelcu.livtet.jigsaw.model
import org.junit.Assert.*; import org.junit.Test
class PuzzleTest {
    @Test fun newPiece() { val puz=Puzzle(); val p=puz.newPiece(); assertEquals(1,puz.pieces.size); assertEquals(puz,p.puzzle) }
    @Test fun autoconnect() { val puz=Puzzle(Size(Vector(50f,50f)),20f); val a=puz.newPiece(right=Insert.Tab); val b=puz.newPiece(left=Insert.Slot); a.locateAt(0f,0f); b.locateAt(95f,0f); puz.autoconnect(); assertTrue(a.connected) }
    @Test fun translate() { val puz=Puzzle(); val a=puz.newPiece(); a.locateAt(0f,0f); puz.translate(10f,20f); assertEquals(10f,a.centralAnchor?.x) }
    @Test fun connectionRequirement() { val puz=Puzzle(Size(Vector(50f,50f)),20f); val a=puz.newPiece(right=Insert.Tab); a.annotate(mapOf("color" to "red")); val b=puz.newPiece(left=Insert.Slot); b.annotate(mapOf("color" to "blue")); a.locateAt(0f,0f); b.locateAt(95f,0f); puz.attachConnectionRequirement{one,other->one.metadata["color"]==other.metadata["color"]}; puz.autoconnect(); assertFalse(a.connected) }
}
