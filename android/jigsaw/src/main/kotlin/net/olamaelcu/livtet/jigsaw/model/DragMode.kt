package net.olamaelcu.livtet.jigsaw.model

interface DragMode { fun dragShouldDisconnect(piece: Piece, dx: Float, dy: Float): Boolean }
object TryDisconnection : DragMode {
    override fun dragShouldDisconnect(piece: Piece, dx: Float, dy: Float) =
        piece.horizontalConnector.openMovement(piece, dx) && piece.verticalConnector.openMovement(piece, dy)
}
object ForceDisconnection : DragMode {
    override fun dragShouldDisconnect(piece: Piece, dx: Float, dy: Float) = true
}
object ForceConnection : DragMode {
    override fun dragShouldDisconnect(piece: Piece, dx: Float, dy: Float) = false
}
