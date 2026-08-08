package net.olamaelcu.livtet.jigsaw.model

typealias InsertsGenerator = (Int) -> Insert

val fixedGenerate: InsertsGenerator = { Insert.Tab }
val flipflopGenerate: InsertsGenerator = { n -> if (n % 2 == 0) Insert.Tab else Insert.Slot }
val twoAndTwoGenerate: InsertsGenerator = { n -> if (n % 4 < 2) Insert.Tab else Insert.Slot }
val randomGenerate: InsertsGenerator = { if (Math.random() < 0.5) Insert.Tab else Insert.Slot }

class InsertSequence(private val generator: InsertsGenerator) {
    private var n: Int = 0
    private var previous: Insert = Insert.None
    private var current: Insert = Insert.None

    fun previousComplement(): Insert = previous.complement()
    fun current(max: Int): Insert = if (n == max) Insert.None else current
    fun next(): Insert { previous = current; current = generator(n++); return current }
}
