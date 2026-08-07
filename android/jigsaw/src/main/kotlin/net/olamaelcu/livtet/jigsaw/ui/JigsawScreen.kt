package net.olamaelcu.livtet.jigsaw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import net.olamaelcu.livtet.jigsaw.model.Manufacturer
import net.olamaelcu.livtet.jigsaw.model.Piece
import net.olamaelcu.livtet.jigsaw.model.PuzzleValidator
import net.olamaelcu.livtet.jigsaw.model.Size as ModelSize
import net.olamaelcu.livtet.jigsaw.model.Vector
import kotlin.math.roundToInt

@Composable
fun JigsawScreen(
    imageBitmap: ImageBitmap?,
    config: PuzzleConfig = PuzzleConfig(),
    state: PuzzleState = remember { PuzzleState() },
    onPieceDragStart: (() -> Unit)? = null,
    onPieceDrag: (() -> Unit)? = null,
    onPieceSnap: (() -> Unit)? = null,
    onSolved: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val pieceSizePx = with(density) { config.pieceSize.toPx() }
    val proximityPx = with(density) { config.proximity.toPx() }
    val borderFillPx = with(density) { config.borderFill.toPx() }
    val strokeWidthPx = with(density) { config.strokeWidth.toPx() }

    LaunchedEffect(Unit) {
        val pieceSize = ModelSize(Vector(pieceSizePx / 2, pieceSizePx / 2))
        val manufacturer = Manufacturer().apply {
            withDimensions(config.horizontalPieces, config.verticalPieces)
            withStructure(pieceSize, proximityPx)
        }
        val puzzle = manufacturer.build()
        puzzle.clearConnectionRequirements()
        puzzle.attachValidator(PuzzleValidator { p -> p.connected })
        puzzle.onValid {
            state.onSolved()
            onSolved?.invoke()
        }
        state.init(puzzle, config.startingHints)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        for (piece in state.puzzle.pieces) {
            PieceView(
                piece = piece,
                pieceSizePx = pieceSizePx,
                borderFillPx = borderFillPx,
                strokeWidthPx = strokeWidthPx,
                shapeStyle = config.shapeStyle,
                imageBitmap = imageBitmap,
                offset = state.pieceOffsets[piece.id] ?: Offset.Zero,
                zIndex = state.zIndices[piece.id] ?: 0f,
                onDragStart = {
                    state.bringToFront(piece.id)
                    onPieceDragStart?.invoke()
                },
                onDrag = { dx, dy ->
                    val current = state.pieceOffsets[piece.id] ?: Offset.Zero
                    val newOffset = Offset(current.x + dx, current.y + dy)
                    piece.drag(dx, dy, quiet = true)
                    state.onPieceMoved(piece.id, newOffset)
                    onPieceDrag?.invoke()
                },
                onDragEnd = {
                    piece.drop()
                    piece.puzzle?.validate()
                    state.rebuildOffsets()
                    onPieceSnap?.invoke()
                },
            )
        }
    }
}

@Composable
private fun PieceView(
    piece: Piece,
    pieceSizePx: Float,
    borderFillPx: Float,
    strokeWidthPx: Float,
    shapeStyle: ShapeStyle,
    imageBitmap: ImageBitmap?,
    offset: Offset,
    zIndex: Float,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val path = remember(piece.id, shapeStyle) {
        PiecePainter.buildPath(piece, pieceSizePx, borderFillPx, shapeStyle)
    }
    val sizePx = pieceSizePx + borderFillPx * 2 + strokeWidthPx

    var dragOffsetX by remember { mutableFloatStateOf(offset.x) }
    var dragOffsetY by remember { mutableFloatStateOf(offset.y) }

    LaunchedEffect(offset) {
        dragOffsetX = offset.x
        dragOffsetY = offset.y
    }

    Box(
        modifier = Modifier
            .size(with(LocalDensity.current) { (sizePx).toDp() })
            .zIndex(zIndex)
            .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
            .pointerInput(piece.id) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount.x
                        dragOffsetY += dragAmount.y
                        onDrag(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pieceSize = Size(pieceSizePx, pieceSizePx)
            with(PiecePainter) {
                drawPiece(
                    piece = piece,
                    path = path,
                    pieceSize = pieceSize,
                    imageBitmap = imageBitmap,
                    strokeWidth = strokeWidthPx,
                )
            }
        }
    }
}
