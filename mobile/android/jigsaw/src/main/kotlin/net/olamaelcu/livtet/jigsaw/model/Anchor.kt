package net.olamaelcu.livtet.jigsaw.model

import kotlin.math.abs
import kotlin.random.Random

class Anchor(var x: Float, var y: Float) {

    fun equal(other: Anchor): Boolean = isAt(other.x, other.y)

    fun isAt(x: Float, y: Float): Boolean = this.x == x && this.y == y

    fun translated(dx: Float, dy: Float): Anchor = copy().also { it.translate(dx, dy) }

    fun translate(dx: Float, dy: Float): Anchor {
        x += dx
        y += dy
        return this
    }

    fun closeTo(other: Anchor, tolerance: Float): Boolean =
        abs(x - other.x) <= tolerance && abs(y - other.y) <= tolerance

    fun copy(): Anchor = Anchor(x, y)

    fun diff(other: Anchor): Pair<Float, Float> =
        (x - other.x) to (y - other.y)

    fun asVector(): Vector = Vector(x, y)

    fun export(): Vector = asVector()

    companion object {
        fun anchor(x: Float, y: Float): Anchor = Anchor(x, y)

        fun atRandom(maxX: Float, maxY: Float): Anchor =
            Anchor(Random.nextFloat() * maxX, Random.nextFloat() * maxY)

        fun import(vector: Vector): Anchor = anchor(vector.x, vector.y)
    }
}
