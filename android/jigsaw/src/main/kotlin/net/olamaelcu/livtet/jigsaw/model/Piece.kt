package net.olamaelcu.livtet.jigsaw.model

typealias TranslationListener = (Piece, Float, Float) -> Unit
typealias ConnectionListener = (Piece, Piece) -> Unit

class Piece(
    val up: Insert = Insert.None,
    val down: Insert = Insert.None,
    val left: Insert = Insert.None,
    val right: Insert = Insert.None,
) {
    var centralAnchor: Anchor? = null
    var metadata: MutableMap<String, Any> = mutableMapOf()
    var _size: Size? = null
    internal var _horizontalConnector: Connector? = null
    internal var _verticalConnector: Connector? = null
    var puzzle: Puzzle? = null
    var upConnection: Piece? = null
    var downConnection: Piece? = null
    var leftConnection: Piece? = null
    var rightConnection: Piece? = null

    private val translateListeners = mutableListOf<TranslationListener>()
    private val connectListeners = mutableListOf<ConnectionListener>()
    private val disconnectListeners = mutableListOf<ConnectionListener>()

    val presentConnections get() = listOfNotNull(rightConnection, downConnection, leftConnection, upConnection)
    val connected get() = upConnection != null || downConnection != null || leftConnection != null || rightConnection != null
    val radius get() = size.radius
    val diameter get() = size.diameter
    val size get() = _size ?: puzzle?.pieceSize ?: Size(Vector(50f, 50f))
    val id get() = metadata["id"] as? String ?: ""
    val horizontalConnector get() = _horizontalConnector ?: puzzle?.horizontalConnector ?: Connector(ConnectorKind.Horizontal).also { _horizontalConnector = it }
    val verticalConnector get() = _verticalConnector ?: puzzle?.verticalConnector ?: Connector(ConnectorKind.Vertical).also { _verticalConnector = it }

    fun getAnchor(name: String) = when(name) {
        "rightAnchor" -> centralAnchor?.translated(radius.x, 0f)
        "leftAnchor" -> centralAnchor?.translated(-radius.x, 0f)
        "downAnchor" -> centralAnchor?.translated(0f, radius.y)
        "upAnchor" -> centralAnchor?.translated(0f, -radius.y)
        else -> null
    }
    fun getInsert(name: String) = when(name) { "right"->right; "left"->left; "down"->down; "up"->up; else->Insert.None }
    fun getConnection(name: String) = when(name) { "rightConnection"->rightConnection; "leftConnection"->leftConnection; "downConnection"->downConnection; "upConnection"->upConnection; else->null }
    fun setConnection(name: String, piece: Piece?) { when(name) { "rightConnection"->rightConnection=piece; "leftConnection"->leftConnection=piece; "downConnection"->downConnection=piece; "upConnection"->upConnection=piece } }

    fun onTranslate(f: TranslationListener) { translateListeners.add(f) }
    fun onConnect(f: ConnectionListener) { connectListeners.add(f) }
    fun onDisconnect(f: ConnectionListener) { disconnectListeners.add(f) }
    internal fun fireTranslate(dx: Float, dy: Float) { translateListeners.forEach { it(this, dx, dy) } }
    internal fun fireConnect(other: Piece) { connectListeners.forEach { it(this, other) } }
    internal fun fireDisconnect(others: List<Piece>) { others.forEach { o -> disconnectListeners.forEach { it(this, o) } } }

    fun centerAround(anchor: Anchor) { require(centralAnchor == null) { "piece already centered" }; centralAnchor = anchor }
    fun locateAt(x: Float, y: Float) { centerAround(Anchor.anchor(x, y)) }
    fun recenterAround(anchor: Anchor, quiet: Boolean = false) { centralAnchor?.let { val (dx,dy)=anchor.diff(it); translate(dx,dy,quiet) } }
    fun relocateTo(x: Float, y: Float, quiet: Boolean = false) { recenterAround(Anchor.anchor(x, y), quiet) }
    fun translate(dx: Float, dy: Float, quiet: Boolean = false) { if(dx!=0f||dy!=0f) { centralAnchor?.translate(dx, dy); if(!quiet) fireTranslate(dx, dy) } }
    fun push(dx: Float, dy: Float, quiet: Boolean = false, pushed: MutableList<Piece> = mutableListOf(this)) {
        translate(dx, dy, quiet)
        presentConnections.filter { it !in pushed }.forEach { pushed.add(it); it.push(dx, dy, false, pushed) }
    }
    fun drag(dx: Float, dy: Float, quiet: Boolean = false) { if(dx==0f&&dy==0f) return; if(dragShouldDisconnect(dx,dy)) { disconnect(); translate(dx,dy,quiet) } else { push(dx,dy,quiet) } }
    fun disconnect() { if(!connected) return; val c=presentConnections; upConnection?.downConnection=null; upConnection=null; downConnection?.upConnection=null; downConnection=null; leftConnection?.rightConnection=null; leftConnection=null; rightConnection?.leftConnection=null; rightConnection=null; fireDisconnect(c) }
    fun drop() { puzzle?.autoconnectWith(this) }
    fun tryConnectWith(other: Piece, back: Boolean = false) {
        if(canConnectHorizontallyWith(other)) horizontalConnector.connectWith(this, other, proximity, back)
        if(canConnectVerticallyWith(other)) verticalConnector.connectWith(this, other, proximity, back)
    }
    fun canConnectHorizontallyWith(other: Piece) = horizontalConnector.canConnectWith(this, other, proximity)
    fun canConnectVerticallyWith(other: Piece) = verticalConnector.canConnectWith(this, other, proximity)
    val proximity get() = puzzle?.proximity ?: 1f
    fun dragShouldDisconnect(dx: Float, dy: Float) = puzzle?.dragShouldDisconnect(this, dx, dy) ?: true
    fun annotate(data: Map<String, Any>) { metadata.putAll(data) }
    fun reannotate(data: Map<String, Any>) { metadata = data.toMutableMap() }
    fun resize(size: Size) { _size = size }
    fun belongTo(p: Puzzle) { puzzle = p }
}
