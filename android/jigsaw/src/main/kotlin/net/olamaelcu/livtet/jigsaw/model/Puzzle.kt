package net.olamaelcu.livtet.jigsaw.model

data class Size(val radius: Vector) {
    val diameter: Vector get() = Vector.multiply(radius, 2f)
}

class Puzzle(
    val pieceSize: Size = Size(Vector(50f, 50f)),
    val proximity: Float = 10f,
) {
    val pieces: MutableList<Piece> = mutableListOf()
    val horizontalConnector = Connector(ConnectorKind.Horizontal)
    val verticalConnector = Connector(ConnectorKind.Vertical)
    var dragMode: DragMode = TryDisconnection
    var validator: Validator = NullValidator()
    val connected get() = pieces.all { it.connected }

    fun newPiece(up: Insert = Insert.None, down: Insert = Insert.None, left: Insert = Insert.None, right: Insert = Insert.None) =
        Piece(up, down, left, right).also { addPiece(it) }
    fun addPiece(piece: Piece) { pieces.add(piece); piece.belongTo(this) }
    fun addPieces(newPieces: List<Piece>) { newPieces.forEach { addPiece(it) } }
    fun annotate(metadata: List<Map<String, Any>>) { pieces.forEachIndexed { i, p -> p.annotate(metadata[i]) } }
    fun autoconnect() { pieces.forEach { autoconnectWith(it) } }
    fun autoconnectWith(piece: Piece) { pieces.filter { it !== piece }.forEach { piece.tryConnectWith(it); it.tryConnectWith(piece, true) } }
    fun disassemble() { pieces.forEach { it.disconnect() } }
    fun shuffle(maxX: Float, maxY: Float) { disassemble(); pieces.forEach { it.relocateTo((Math.random()*maxX).toFloat(), (Math.random()*maxY).toFloat()) }; autoconnect() }
    fun translate(dx: Float, dy: Float) { pieces.forEach { it.translate(dx, dy) } }
    fun onTranslate(f: TranslationListener) { pieces.forEach { it.onTranslate(f) } }
    fun onConnect(f: ConnectionListener) { pieces.forEach { it.onConnect(f) } }
    fun onDisconnect(f: ConnectionListener) { pieces.forEach { it.onDisconnect(f) } }
    fun onValid(f: (Puzzle) -> Unit) { validator.onValid(f) }
    fun isValid() = validator.isValid(this)
    val valid get() = validator.valid
    fun validate() { validator.validate(this) }
    fun updateValidity() { validator.updateValidity(this) }
    fun attachConnectionRequirement(r: ConnectionRequirement) { horizontalConnector.requirement = r; verticalConnector.requirement = r }
    fun clearConnectionRequirements() { horizontalConnector.requirement = noConnectionRequirements; verticalConnector.requirement = noConnectionRequirements }
    fun attachValidator(v: Validator) { validator = v }
    fun dragShouldDisconnect(piece: Piece, dx: Float, dy: Float) = dragMode.dragShouldDisconnect(piece, dx, dy)
    fun forceConnectionWhileDragging() { dragMode = ForceConnection }
    fun forceDisconnectionWhileDragging() { dragMode = ForceDisconnection }
    fun tryDisconnectionWhileDragging() { dragMode = TryDisconnection }
}
