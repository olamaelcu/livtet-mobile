package net.olamaelcu.livtet

/**
 * An exception as a line a reader can act on.
 *
 * UniFFI renders a thrown error as its enum encoding (`v1=…`), which is an implementation detail;
 * the sentence after it is the part worth showing.
 */
internal fun readable(error: Throwable): String =
    (error.message ?: error.toString()).replace(Regex("^v\\d+="), "")
