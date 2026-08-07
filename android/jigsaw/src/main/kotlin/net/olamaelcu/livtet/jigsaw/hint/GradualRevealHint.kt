package net.olamaelcu.livtet.jigsaw.hint

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

fun Modifier.revealGlow(isRevealing: Boolean, pieceSizePx: Float, borderFillPx: Float): Modifier {
    if (!isRevealing) return this
    return this.drawBehind {
        val totalSize = pieceSizePx + borderFillPx * 2
        drawRect(color = Color.White.copy(alpha = 0.3f),
            size = Size(totalSize, totalSize), topLeft = Offset.Zero)
    }
}
