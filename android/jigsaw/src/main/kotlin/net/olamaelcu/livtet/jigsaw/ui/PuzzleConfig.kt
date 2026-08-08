package net.olamaelcu.livtet.jigsaw.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class PuzzleConfig(
    val horizontalPieces: Int = 4,
    val verticalPieces: Int = 4,
    val pieceSize: Dp = 100.dp,
    val proximity: Dp = 20.dp,
    val borderFill: Dp = 4.dp,
    val strokeWidth: Dp = 1.dp,
    val shapeStyle: ShapeStyle = ShapeStyle.Rounded,
    val startingHints: Int = 3,
)

enum class ShapeStyle { Squared, Rounded }
