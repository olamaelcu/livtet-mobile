package net.olamaelcu.livtet.jigsaw.hint

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.olamaelcu.livtet.jigsaw.model.Piece
import net.olamaelcu.livtet.jigsaw.model.Puzzle

class HintController {
    var showOverlay by mutableStateOf(true)
    var highlightedPieceId by mutableStateOf<String?>(null)
    var highlightedNeighborIds by mutableStateOf<List<String>>(emptyList())
    var revealActive by mutableStateOf(false)
    var revealPieceIds by mutableStateOf<List<String>>(emptyList())
    var revealCurrentIndex by mutableStateOf(0)

    fun startHighlight(selectedPiece: Piece, puzzle: Puzzle) {
        highlightedPieceId = selectedPiece.id
        val neighbors = mutableListOf<Piece>()
        puzzle.pieces.filter { it !== selectedPiece }.forEach { other ->
            if (selectedPiece.right.match(other.left)) neighbors.add(other)
            if (selectedPiece.left.match(other.right)) neighbors.add(other)
            if (selectedPiece.down.match(other.up)) neighbors.add(other)
            if (selectedPiece.up.match(other.down)) neighbors.add(other)
        }
        highlightedNeighborIds = neighbors.map { it.id }
    }

    fun dismissHighlight() {
        highlightedPieceId = null
        highlightedNeighborIds = emptyList()
    }
}
