package net.olamaelcu.livtet.jigsaw.model

typealias ValidationListener = (Puzzle) -> Unit

abstract class Validator {
    private val listeners = mutableListOf<ValidationListener>()
    internal var _valid: Boolean? = null
    open val valid get() = _valid ?: false
    open val isNull get() = false
    abstract fun isValid(puzzle: Puzzle): Boolean
    fun validate(puzzle: Puzzle) { val was = _valid; updateValidity(puzzle); if(_valid == true && was != true) fireValid(puzzle) }
    fun updateValidity(puzzle: Puzzle) { _valid = isValid(puzzle) }
    fun fireValid(puzzle: Puzzle) { listeners.forEach { it(puzzle) } }
    fun onValid(f: ValidationListener) { listeners.add(f) }
}

class NullValidator : Validator() { override val isNull=true; override fun isValid(p: Puzzle)=false }
class PieceValidator(val condition: (Piece) -> Boolean) : Validator() { override fun isValid(p: Puzzle) = p.pieces.all { condition(it) } }
class PuzzleValidator(val condition: (Puzzle) -> Boolean) : Validator() { override fun isValid(p: Puzzle) = condition(p) }
