package net.olamaelcu.livtet.jigsaw.model

typealias ConnectionRequirement = (Piece, Piece) -> Boolean
val noConnectionRequirements: ConnectionRequirement = { _, _ -> true }

enum class ConnectorKind { Horizontal, Vertical }

class Connector internal constructor(kind: ConnectorKind) {
    val axis = if (kind == ConnectorKind.Horizontal) 'x' else 'y'
    val forward = if (kind == ConnectorKind.Horizontal) "right" else "down"
    val backward = if (kind == ConnectorKind.Horizontal) "left" else "up"
    val forwardAnchor get() = "${forward}Anchor"
    val backwardAnchor get() = "${backward}Anchor"
    val forwardConnection get() = "${forward}Connection"
    val backwardConnection get() = "${backward}Connection"
    var requirement: ConnectionRequirement = noConnectionRequirements

    fun attract(one: Piece, other: Piece, back: Boolean = false) {
        val (iron, magnet) = if (back) one to other else other to one
        val mc = magnet.centralAnchor ?: return
        val ic = iron.centralAnchor ?: return
        val ma: Anchor
        val ia: Anchor
        if ((axis == 'x' && mc.x > ic.x) || (axis == 'y' && mc.y > ic.y)) {
            ma = magnet.getAnchor(backwardAnchor) ?: return
            ia = iron.getAnchor(forwardAnchor) ?: return
        } else {
            ma = magnet.getAnchor(forwardAnchor) ?: return
            ia = iron.getAnchor(backwardAnchor) ?: return
        }
        val (dx, dy) = ma.diff(ia)
        iron.push(dx, dy)
    }

    fun openMovement(one: Piece, delta: Float) =
        (delta > 0 && one.getConnection(forwardConnection) == null) ||
        (delta < 0 && one.getConnection(backwardConnection) == null) || delta == 0f

    fun canConnectWith(one: Piece, other: Piece, proximity: Float) =
        closeTo(one, other, proximity) && match(one, other) && requirement(one, other)

    fun closeTo(one: Piece, other: Piece, proximity: Float): Boolean {
        val f = one.getAnchor(forwardAnchor) ?: return false
        val b = other.getAnchor(backwardAnchor) ?: return false
        return f.closeTo(b, proximity)
    }

    fun match(one: Piece, other: Piece) =
        one.getInsert(forward).match(other.getInsert(backward))

    fun connectWith(one: Piece, other: Piece, proximity: Float, back: Boolean = false) {
        require(canConnectWith(one, other, proximity)) { "can not connect $forward!" }
        if (one.getConnection(forwardConnection) !== other) {
            attract(other, one, back)
            one.setConnection(forwardConnection, other)
            other.setConnection(backwardConnection, one)
            one.fireConnect(other)
        }
    }
}
