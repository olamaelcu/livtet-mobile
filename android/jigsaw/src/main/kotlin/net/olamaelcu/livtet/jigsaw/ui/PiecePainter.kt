package net.olamaelcu.livtet.jigsaw.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import net.olamaelcu.livtet.jigsaw.model.OutlineStyle
import net.olamaelcu.livtet.jigsaw.model.Piece

object PiecePainter {

    fun buildPath(piece: Piece, pieceSizePx: Float, borderFillPx: Float, style: ShapeStyle): Path {
        val outline = when (style) {
            ShapeStyle.Squared -> OutlineStyle.Squared
            ShapeStyle.Rounded -> OutlineStyle.Rounded
        }
        val points = outline.draw(piece, pieceSizePx, borderFillPx)
        return Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points[0], points[1])
                var i = 2
                while (i < points.size) {
                    lineTo(points[i], points[i + 1])
                    i += 2
                }
                close()
            }
        }
    }

    fun DrawScope.drawPiece(
        @Suppress("UNUSED_PARAMETER") piece: Piece,
        path: Path,
        pieceSize: Size,
        imageBitmap: ImageBitmap? = null,
        color: Color? = null,
        strokeColor: Color = Color.Black,
        strokeWidth: Float = 2f,
    ) {
        val fillColor = color ?: Color.Gray
        withTransform({
            translate(left = pieceSize.width / 2, top = pieceSize.height / 2)
        }) {
            clipPath(path) {
                if (imageBitmap != null) {
                    drawImage(
                        image = imageBitmap,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(pieceSize.width.toInt(), pieceSize.height.toInt()),
                    )
                } else {
                    drawPath(path = path, color = fillColor)
                }
            }
            drawPath(path = path, color = strokeColor, style = Stroke(width = strokeWidth))
        }
    }
}
