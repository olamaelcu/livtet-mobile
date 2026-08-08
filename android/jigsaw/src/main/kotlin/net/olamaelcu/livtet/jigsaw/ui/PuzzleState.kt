package net.olamaelcu.livtet.jigsaw.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import net.olamaelcu.livtet.jigsaw.model.Puzzle

class PuzzleState {
    var puzzle by mutableStateOf(Puzzle())
        private set

    var solved by mutableStateOf(false)
        private set

    var pieceOffsets: Map<String, Offset> by mutableStateOf(emptyMap())
        private set

    var zIndices: Map<String, Float> by mutableStateOf(emptyMap())
        private set

    var highlightedPieceId: String? by mutableStateOf(null)
    var showReveal: Boolean by mutableStateOf(false)
    var revealProgress: Float by mutableStateOf(0f)
    var hintsRemaining by mutableIntStateOf(3)

    fun init(p: Puzzle, startingHints: Int) {
        puzzle = p
        hintsRemaining = startingHints
        solved = false
        rebuildOffsets()
    }

    fun rebuildOffsets() {
        val offsets = mutableMapOf<String, Offset>()
        val indices = mutableMapOf<String, Float>()
        puzzle.pieces.forEachIndexed { i, piece ->
            val anchor = piece.centralAnchor
            if (anchor != null) {
                val id = piece.id
                offsets[id] = Offset(anchor.x, anchor.y)
                indices[id] = i.toFloat()
            }
        }
        pieceOffsets = offsets
        zIndices = indices
    }

    fun onPieceMoved(pieceId: String, offset: Offset) {
        pieceOffsets = pieceOffsets.toMutableMap().also { it[pieceId] = offset }
    }

    fun bringToFront(pieceId: String) {
        zIndices = zIndices.toMutableMap().also {
            it[pieceId] = (zIndices.values.maxOrNull() ?: 0f) + 1f
        }
    }

    fun onSolved() { solved = true }
    fun useHint(): Boolean {
        if (hintsRemaining <= 0) return false
        hintsRemaining--
        return true
    }

    fun shuffle(farness: Float = 1f) {
        val maxX = puzzle.pieces.maxOf { it.centralAnchor?.x ?: 0f } * farness
        val maxY = puzzle.pieces.maxOf { it.centralAnchor?.y ?: 0f } * farness
        puzzle.shuffle(maxX, maxY)
        rebuildOffsets()
        solved = false
    }

    fun reset() { rebuildOffsets(); solved = false }
}
