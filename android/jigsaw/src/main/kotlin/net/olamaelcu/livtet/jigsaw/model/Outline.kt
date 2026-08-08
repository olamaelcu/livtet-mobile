package net.olamaelcu.livtet.jigsaw.model

import kotlin.math.min

sealed class OutlineStyle {
    abstract fun draw(piece: Piece, size: Float, borderFill: Float): List<Float>
    abstract fun isBezier(): Boolean

    data object Squared : OutlineStyle() {
        override fun draw(piece: Piece, size: Float, borderFill: Float): List<Float> {
            val offset = borderFill * 5 / size
            val pts = mutableListOf<Float>()
            val steps = listOf(
                0 - offset, 0 - offset,
                1f, 0 - offset,
                2f, select(piece.up, -1 - offset, 1 - offset, 0 - offset),
                3f, 0 - offset,
                4 + offset, 0 - offset,
                4 + offset, 1f,
                select(piece.right, 5 + offset, 3 + offset, 4 + offset), 2f,
                4 + offset, 3f,
                4 + offset, 4 + offset,
                3f, 4 + offset,
                2f, select(piece.down, 5 + offset, 3 + offset, 4 + offset),
                1f, 4 + offset,
                0 - offset, 4 + offset,
                0 - offset, 3f,
                select(piece.left, -1 - offset, 1 - offset, 0 - offset), 2f,
                0 - offset, 1f,
            )
            for (i in steps.indices) { pts.add(steps[i] * (if (i % 2 == 0) size else size) / 5) }
            return pts
        }
        override fun isBezier() = false
    }

    data object Rounded : OutlineStyle() {
        override fun draw(piece: Piece, fullSize: Float, borderFill: Float): List<Float> {
            val fs = Vector(fullSize, fullSize)
            val r = (min(fs.x, fs.y) * (1 - 2f * 0.333f))
            val s = Vector.divide(Vector.minus(fs, Vector(r, r)), 2f)
            val o = Vector.multiply(Vector(r, r), 0.8f)
            val result = mutableListOf<Float>()
            result.addAll(listOf(0f, 0f, 0f, s.y))
            when {
                piece.left.isSlot() -> result.addAll(listOf(-o.x, s.y, -o.x, r + s.y, 0f, s.y, 0f, r + s.y))
                piece.left.isTab() -> result.addAll(listOf(o.x, s.y, o.x, r + s.y, 0f, s.y, 0f, r + s.y))
                else -> result.addAll(listOf(0f, s.y, 0f, r + s.y))
            }
            result.addAll(listOf(0f, r + s.y, 0f, r + 2 * s.y))
            result.addAll(listOf(s.x, r + 2 * s.y, r + s.x, r + 2 * s.y))
            when {
                piece.down.isSlot() -> result.addAll(listOf(r + s.x, r + 2 * s.y - o.y, r + s.x, r + s.y - o.y))
                piece.down.isTab() -> result.addAll(listOf(r + s.x, r + 2 * s.y + o.y, r + s.x, r + s.y + o.y))
                else -> result.addAll(listOf(r + s.x, r + 2 * s.y, r + s.x, r + s.y))
            }
            result.addAll(listOf(r + s.x, r + 2 * s.y, r + 2 * s.x, r + 2 * s.y))
            result.addAll(listOf(r + 2 * s.x, r + s.y, r + 2 * s.x, s.y))
            when {
                piece.right.isSlot() -> result.addAll(listOf(r + 2 * s.x - o.x, s.y, r + 2 * s.x - o.x, 0f, r + 2 * s.x, s.y, r + 2 * s.x, 0f))
                piece.right.isTab() -> result.addAll(listOf(r + 2 * s.x + o.x, s.y, r + 2 * s.x + o.x, 0f, r + 2 * s.x, s.y, r + 2 * s.x, 0f))
                else -> result.addAll(listOf(r + 2 * s.x, s.y, r + 2 * s.x, 0f))
            }
            result.addAll(listOf(r + 2 * s.x, s.y, r + s.x, s.y))
            when {
                piece.up.isSlot() -> result.addAll(listOf(r + s.x, s.y - o.y, s.x, 0f - o.y))
                piece.up.isTab() -> result.addAll(listOf(r + s.x, s.y + o.y, s.x, 0f + o.y))
                else -> result.addAll(listOf(r + s.x, s.y, s.x, s.y))
            }
            result.addAll(listOf(s.x, s.y, 0f, s.y, 0f, 0f))
            return result
        }
        override fun isBezier() = false
    }

    companion object {
        fun select(insert: Insert, tab: Float, slot: Float, none: Float): Float =
            when (insert) { Insert.Tab -> tab; Insert.Slot -> slot; Insert.None -> none }
    }
}
