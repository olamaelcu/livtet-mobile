package net.olamaelcu.livtet.jigsaw.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class Vector(val x: Float, val y: Float) {
    companion object {
        val ZERO = Vector(0f, 0f)

        fun cast(value: Float): Vector = Vector(value, value)

        fun cast(value: Vector): Vector = value

        fun copy(v: Vector): Vector = Vector(v.x, v.y)

        fun equal(one: Vector, other: Vector, delta: Float = 0f): Boolean =
            abs(one.x - other.x) <= delta && abs(one.y - other.y) <= delta

        fun diff(one: Vector, other: Vector): Pair<Float, Float> =
            (one.x - other.x) to (one.y - other.y)

        fun multiply(one: Float, other: Float): Vector = apply(one, other) { a, b -> a * b }
        fun multiply(v: Vector, scalar: Float): Vector = Vector(v.x * scalar, v.y * scalar)
        fun multiply(v: Vector, other: Vector): Vector = Vector(v.x * other.x, v.y * other.y)

        fun divide(v: Vector, other: Vector): Vector = Vector(v.x / other.x, v.y / other.y)
        fun divide(v: Vector, scalar: Float): Vector = Vector(v.x / scalar, v.y / scalar)

        fun plus(v: Vector, other: Vector): Vector = Vector(v.x + other.x, v.y + other.y)
        fun minus(v: Vector, other: Vector): Vector = Vector(v.x - other.x, v.y - other.y)

        fun min(v: Vector, other: Vector): Vector = Vector(min(v.x, other.x), min(v.y, other.y))
        fun max(v: Vector, other: Vector): Vector = Vector(max(v.x, other.x), max(v.y, other.y))

        private fun apply(one: Float, other: Float, f: (Float, Float) -> Float): Vector {
            val first = cast(one)
            val second = cast(other)
            return Vector(f(first.x, second.x), f(first.y, second.y))
        }

        fun innerMin(v: Vector): Float = min(v.x, v.y)
        fun innerMax(v: Vector): Float = max(v.x, v.y)
    }
}
