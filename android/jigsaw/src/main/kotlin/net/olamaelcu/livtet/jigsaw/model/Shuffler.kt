package net.olamaelcu.livtet.jigsaw.model

typealias Shuffler = (List<Piece>) -> List<Vector>

fun randomShuffler(maxX: Float, maxY: Float): Shuffler = { pieces ->
    pieces.map { Anchor.atRandom(maxX, maxY).asVector() }
}

val gridShuffler: Shuffler = { pieces ->
    val destinations = pieces.map { it.centralAnchor!!.asVector() }.toMutableList()
    for (i in destinations.indices) {
        val j = (Math.random() * destinations.size).toInt()
        val temp = destinations[j]; destinations[j] = destinations[i]; destinations[i] = temp
    }
    destinations
}

val columnsShuffler: Shuffler = { pieces ->
    val destinations = pieces.map { it.centralAnchor!!.asVector() }.toMutableList()
    val columns = mutableMapOf<Float, MutableList<Vector>>()
    for (d in destinations) {
        columns.getOrPut(d.x) { destinations.filter { it.x == d.x }.toMutableList() }
        val column = columns[d.x]!!
        val j = (Math.random() * column.size).toInt()
        val temp = column[j].y; column[j] = Vector(column[j].x, d.y)
        val idx = destinations.indexOf(d); destinations[idx] = Vector(destinations[idx].x, temp)
    }
    destinations
}

fun padderShuffler(padding: Float, width: Int, height: Int): Shuffler = { pieces ->
    val destinations = pieces.map { it.centralAnchor!!.asVector() }.toMutableList()
    var dx = 0f; var dy = 0f
    for (j in 0 until height) {
        for (i in 0 until width) {
            val idx = i + width * j
            destinations[idx] = Vector(destinations[idx].x + dx, destinations[idx].y + dy)
            dx += padding
        }
        dx = 0f; dy += padding
    }
    destinations
}

fun noiseShuffler(maxDistance: Vector): Shuffler = { pieces ->
    pieces.map { piece ->
        val anchor = piece.centralAnchor ?: return@map Vector.ZERO
        Anchor.atRandom(2 * maxDistance.x, 2 * maxDistance.y)
            .translate(-maxDistance.x, -maxDistance.y)
            .translate(anchor.x, anchor.y).asVector()
    }
}

val noopShuffler: Shuffler = { pieces -> pieces.map { it.centralAnchor!!.asVector() } }
