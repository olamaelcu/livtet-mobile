package net.olamaelcu.livtet.jigsaw.model

sealed class Insert {
    abstract fun isSlot(): Boolean
    abstract fun isTab(): Boolean
    abstract fun isNone(): Boolean
    abstract fun match(other: Insert): Boolean
    abstract fun complement(): Insert
    abstract fun serialize(): Char

    data object Tab : Insert() {
        override fun isSlot() = false
        override fun isTab() = true
        override fun isNone() = false
        override fun match(other: Insert) = other.isSlot()
        override fun complement() = Slot
        override fun serialize() = 'T'
    }

    data object Slot : Insert() {
        override fun isSlot() = true
        override fun isTab() = false
        override fun isNone() = false
        override fun match(other: Insert) = other.isTab()
        override fun complement() = Tab
        override fun serialize() = 'S'
    }

    data object None : Insert() {
        override fun isSlot() = false
        override fun isTab() = false
        override fun isNone() = true
        override fun match(other: Insert) = false
        override fun complement() = None
        override fun serialize() = '-'
    }
}
