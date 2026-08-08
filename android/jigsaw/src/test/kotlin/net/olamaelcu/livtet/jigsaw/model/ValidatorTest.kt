package net.olamaelcu.livtet.jigsaw.model
import org.junit.Assert.*; import org.junit.Test
class ValidatorTest {
    @Test fun nullValidator() { assertFalse(NullValidator().isValid(Puzzle())) }
    @Test fun puzzleValidator() { val puz=Puzzle(Size(Vector(50f,50f)),20f); val a=puz.newPiece(right=Insert.Tab); val b=puz.newPiece(left=Insert.Slot); a.locateAt(0f,0f); b.locateAt(95f,0f); puz.autoconnect(); assertTrue(PuzzleValidator{p->p.connected}.isValid(puz)) }
    @Test fun onValid() { val puz=Puzzle(Size(Vector(50f,50f)),20f); val a=puz.newPiece(right=Insert.Tab); val b=puz.newPiece(left=Insert.Slot); a.locateAt(0f,0f); b.locateAt(95f,0f); var fired=false; val v=PuzzleValidator{p->p.connected}; v.onValid{fired=true}; puz.attachValidator(v); puz.autoconnect(); puz.validate(); assertTrue(fired) }
}
