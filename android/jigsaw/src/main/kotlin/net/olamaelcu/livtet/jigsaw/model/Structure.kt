package net.olamaelcu.livtet.jigsaw.model

data class Structure(
    val up: Insert = Insert.None,
    val down: Insert = Insert.None,
    val left: Insert = Insert.None,
    val right: Insert = Insert.None,
)

fun serialize(structure: Structure): String =
    listOf(structure.right, structure.down, structure.left, structure.up)
        .joinToString("") { it.serialize().toString() }

fun deserialize(string: String): Structure {
    require(string.length == 4) { "structure string must be 4 chars long" }
    fun parseInsert(c: Char): Insert = when (c) {
        'S' -> Insert.Slot; 'T' -> Insert.Tab; else -> Insert.None
    }
    return Structure(
        right = parseInsert(string[0]), down = parseInsert(string[1]),
        left = parseInsert(string[2]), up = parseInsert(string[3]),
    )
}
