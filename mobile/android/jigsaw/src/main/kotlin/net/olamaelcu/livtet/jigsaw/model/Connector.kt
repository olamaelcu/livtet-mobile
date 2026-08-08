package net.olamaelcu.livtet.jigsaw.model

typealias ConnectionRequirement = (Piece, Piece) -> Boolean

val noConnectionRequirements: ConnectionRequirement = { _, _ -> true }

enum class ConnectorKind { Horizontal, Vertical }

class Connector internal constructor(kind: ConnectorKind) {
    val axis: Char = if (kind == ConnectorKind.Horizontal) 'x' else 'y'
    val forward: String = if (kind == ConnectorKind.Horizontal) "right" else "down"
    val backward: String = if (kind == ConnectorKind.Horizontal) "left" else "up"

    internal val forwardAnchor: String = "${forward}Anchor"
    internal val backwardAnchor: String = "${backward}Anchor"

    internal val forwardConnection: String = "${forward}Connection"
    internal val backwardConnection: String = "${backward}Connection"

    var requirement: ConnectionRequirement = noConnectionRequirements

    fun attract(one: Piece, other: Piece, back: Boolean = false) {
        val (iron, magnet) = if (back) one to other else other to one
        val dx: Float
        val dy: Float
        val magnetCentral = magnet.centralAnchor ?: return
        val ironCentral = iron.centralAnchor ?: return

        if (if (axis == 'x') magnetCentral.x > ironCentral.x else magnetCentral.y > ironCentral.y) {
            val magnetAnchor = magnet.getAnchor(backwardAnchor) ?: return
            val ironAnchor = iron.getAnchor(forwardAnchor) ?: return
            val d = magnetAnchor.diff(ironAnchor)
            dx = d.first; dy = d.second
        } else {
            val magnetAnchor = magnet.getAnchor(forwardAnchor) ?: return
            val ironAnchor = iron.getAnchor(backwardAnchor) ?: return
            val d = magnetAnchor.diff(ironAnchor)
            dx = d.first; dy = d.second
        }
        iron.push(dx, dy)
    }

    fun openMovement(one: Piece, delta: Float): Boolean =
        (delta > 0 && one.getConnection(forwardConnection) == null) ||
        (delta < 0 && one.getConnection(backwardConnection) == null) ||
        delta == 0f

    fun canConnectWith(one: Piece, other: Piece, proximity: Float): Boolean =
        closeTo(one, other, proximity) && match(one, other) && requirement(one, other)

    fun closeTo(one: Piece, other: Piece, proximity: Float): Boolean {
        val fwd = one.getAnchor(forwardAnchor) ?: return false
        val bwd = other.getAnchor(backwardAnchor) ?: return false
        return fwd.closeTo(bwd, proximity)
    }

    fun match(one: Piece, other: Piece): Boolean {
        val fwd = one.getInsert(forward)
        val bwd = other.getInsert(backward)
        return fwd.match(bwd)
    }

    fun connectWith(one: Piece, other: Piece, proximity: Float, back: Boolean = false) {
        if (!canConnectWith(one, other, proximity))
            throw IllegalStateException("can not connect $forward!")
        if (one.getConnection(forwardConnection) !== other) {
            attract(other, one, back)
            one.setConnection(forwardConnection, other)
            other.setConnection(backwardConnection, one)
            one.fireConnect(other)
        }
    }
}
