package net.olamaelcu.livtet.jigsaw.model
import org.junit.Assert.*; import org.junit.Test
class PieceTest {
    @Test fun locateAt() { val p = Piece(); p.locateAt(10f, 20f); assertEquals(10f, p.centralAnchor?.x); assertEquals(20f, p.centralAnchor?.y) }
    @Test fun translate() { val p = Piece(); p.locateAt(0f, 0f); var f=false; p.onTranslate{_,_,_ -> f=true}; p.translate(5f,3f); assertEquals(5f,p.centralAnchor?.x); assertTrue(f) }
    @Test fun connectedFalse() { assertFalse(Piece().connected) }
    @Test fun connectedTrue() { val a=Piece(); a.rightConnection=Piece(); assertTrue(a.connected) }
    @Test fun disconnect() { val puz=Puzzle(Size(Vector(50f,50f))); val a=puz.newPiece(right=Insert.Tab); val b=puz.newPiece(left=Insert.Slot); a.locateAt(0f,0f); b.locateAt(95f,0f); a.tryConnectWith(b); assertTrue(a.connected); a.disconnect(); assertFalse(a.connected); assertNull(a.rightConnection) }
    @Test(expected=IllegalArgumentException::class) fun centerAroundTwice() { val p=Piece(); p.centerAround(Anchor(0f,0f)); p.centerAround(Anchor(1f,1f)) }
    @Test fun recenterAround() { val p=Piece(); p.locateAt(0f,0f); p.recenterAround(Anchor(50f,50f)); assertEquals(50f,p.centralAnchor?.x) }
    @Test fun annotate() { val p=Piece(); p.annotate(mapOf("color" to "#ff0000","id" to "1")); assertEquals("#ff0000",p.metadata["color"]); assertEquals("1",p.metadata["id"]) }
    @Test fun tryConnect() { val puz=Puzzle(Size(Vector(50f,50f)),20f); val a=puz.newPiece(right=Insert.Tab); val b=puz.newPiece(left=Insert.Slot); a.locateAt(0f,0f); b.locateAt(95f,0f); a.tryConnectWith(b); assertTrue(a.connected) }
}
