package net.olamaelcu.livtet.jigsaw

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import net.olamaelcu.livtet.jigsaw.hint.HintController
import net.olamaelcu.livtet.jigsaw.hint.HintOverlay
import net.olamaelcu.livtet.jigsaw.sound.PuzzleSound
import net.olamaelcu.livtet.jigsaw.sound.PuzzleSoundPlayer
import net.olamaelcu.livtet.jigsaw.ui.JigsawScreen
import net.olamaelcu.livtet.jigsaw.ui.PuzzleConfig
import net.olamaelcu.livtet.jigsaw.ui.PuzzleState
import kotlinx.coroutines.delay

@Composable
fun JigsawPuzzle(
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
    config: PuzzleConfig = PuzzleConfig(),
    soundPlayer: PuzzleSoundPlayer? = null,
    onSolved: () -> Unit = {},
) {
    val state = remember { PuzzleState() }
    val hintController = remember { HintController() }
    var dragThrottle by remember { mutableLongStateOf(0L) }

    Box(modifier = modifier.fillMaxSize()) {
        JigsawScreen(
            imageBitmap = image,
            config = config,
            state = state,
            onPieceDragStart = { soundPlayer?.play(PuzzleSound.CLICK) },
            onPieceDrag = {
                val now = System.currentTimeMillis()
                if (now - dragThrottle > 100) {
                    dragThrottle = now
                    soundPlayer?.play(PuzzleSound.DRAG)
                }
            },
            onPieceSnap = { soundPlayer?.play(PuzzleSound.SNAP) },
            onSolved = {
                soundPlayer?.play(PuzzleSound.COMPLETE)
                onSolved()
            },
        )

        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            HintOverlay(
                hintsRemaining = state.hintsRemaining,
                onHighlight = {
                    if (state.useHint()) {
                        val piece = state.puzzle.pieces.firstOrNull() ?: return@HintOverlay
                        hintController.startHighlight(piece, state.puzzle)
                    }
                },
                onReveal = {
                    if (state.useHint()) {
                        hintController.revealActive = true
                        hintController.revealPieceIds = state.puzzle.pieces.map { it.id }
                        hintController.revealCurrentIndex = 0
                    }
                },
            )
        }
    }

    LaunchedEffect(hintController.revealActive) {
        if (!hintController.revealActive) return@LaunchedEffect
        val pieces = state.puzzle.pieces
        for (i in pieces.indices) {
            hintController.revealCurrentIndex = i
            delay(75)
        }
        delay(500)
        hintController.revealActive = false
        hintController.revealCurrentIndex = -1
    }

    LaunchedEffect(hintController.highlightedPieceId) {
        if (hintController.highlightedPieceId != null) {
            delay(4000)
            hintController.dismissHighlight()
        }
    }
}
