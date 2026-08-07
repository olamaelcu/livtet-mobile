package net.olamaelcu.livtet.jigsaw.model

typealias ValidationListener = (Puzzle) -> Unit

abstract class Validator {
    private val _validListeners: MutableList<ValidationListener> = mutableListOf()
    internal var _valid: Boolean? = null
    val valid: Boolean get() = _valid ?: false
    open val isNull: Boolean get() = false
    abstract fun isValid(puzzle: Puzzle): Boolean

    fun validate(puzzle: Puzzle) {
        val wasValid = _valid
        updateValidity(puzzle)
        if (_valid == true && wasValid != true) fireValid(puzzle)
    }

    fun updateValidity(puzzle: Puzzle) { _valid = isValid(puzzle) }
    fun fireValid(puzzle: Puzzle) { _validListeners.forEach { it(puzzle) } }
    fun onValid(f: ValidationListener) { _validListeners.add(f) }
}

class NullValidator : Validator() {
    override val isNull: Boolean get() = true
    override fun isValid(puzzle: Puzzle) = false
}

class PieceValidator(val condition: (Piece) -> Boolean) : Validator() {
    override fun isValid(puzzle: Puzzle): Boolean = puzzle.pieces.all { condition(it) }
}

class PuzzleValidator(val condition: (Puzzle) -> Boolean) : Validator() {
    override fun isValid(puzzle: Puzzle): Boolean = condition(puzzle)
}
