package net.olamaelcu.livtet.jigsaw.model

class Manufacturer {
    var insertsGenerator: InsertsGenerator = twoAndTwoGenerate
    var metadata: List<Map<String, Any>> = emptyList()
    var headAnchor: Anchor? = null
    var width: Int = 4
    var height: Int = 4
    var pieceSize: Size = Size(Vector(50f, 50f))
    var proximity: Float = 10f

    fun withStructure(size: Size, proximity: Float) { pieceSize = size; this.proximity = proximity }
    fun withMetadata(m: List<Map<String, Any>>) { metadata = m }
    fun withInsertsGenerator(g: InsertsGenerator) { insertsGenerator = g }
    fun withHeadAt(a: Anchor) { headAnchor = a }
    fun withDimensions(w: Int, h: Int) { width = w; height = h }

    fun build(): Puzzle {
        val puzzle = Puzzle(pieceSize = pieceSize, proximity = proximity)
        val positioner = Positioner(puzzle, headAnchor)
        var verticalSequence = InsertSequence(insertsGenerator)

        for (y in 0 until height) {
            val horizontalSequence = InsertSequence(insertsGenerator)
            verticalSequence.next()
            for (x in 0 until width) {
                horizontalSequence.next()
                val piece = puzzle.newPiece(
                    left = horizontalSequence.previousComplement(),
                    up = verticalSequence.previousComplement(),
                    right = horizontalSequence.current(width),
                    down = verticalSequence.current(height),
                )
                piece.centerAround(positioner.naturalAnchor(x, y))
            }
        }
        annotateAll(puzzle.pieces)
        return puzzle
    }

    private fun annotateAll(pieces: List<Piece>) {
        pieces.forEachIndexed { index, piece ->
            val base = metadata.getOrNull(index) ?: emptyMap()
            val data = base.toMutableMap()
            if (!data.containsKey("id")) data["id"] = (index + 1).toString()
            piece.annotate(data)
        }
    }
}

internal class Positioner(private val puzzle: Puzzle, headAnchor: Anchor?) {
    private val offset: Vector = headAnchor?.asVector() ?: puzzle.pieceSize.radius

    fun naturalAnchor(x: Int, y: Int): Anchor =
        Anchor.anchor(
            x * puzzle.pieceSize.diameter.x + offset.x,
            y * puzzle.pieceSize.diameter.y + offset.y,
        )
}
