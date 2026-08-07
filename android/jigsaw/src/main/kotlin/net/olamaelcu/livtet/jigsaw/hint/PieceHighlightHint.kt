package net.olamaelcu.livtet.jigsaw.hint

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun Modifier.highlightPiece(
    isHighlighted: Boolean,
    pieceSizePx: Float,
    borderFillPx: Float,
): Modifier {
    if (!isHighlighted) return this
    val transition = rememberInfiniteTransition(label = "highlight")
    val alpha by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
        label = "highlightAlpha",
    )
    return this.drawBehind {
        val glowColor = Color.Yellow.copy(alpha = alpha)
        val totalSize = pieceSizePx + borderFillPx * 2
        drawCircle(color = glowColor, radius = totalSize / 2,
            center = Offset(totalSize / 2, totalSize / 2), style = Stroke(width = 6f))
    }
}
