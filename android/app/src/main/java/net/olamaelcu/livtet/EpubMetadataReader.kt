package net.olamaelcu.livtet

import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * What an EPUB says about itself, read without opening it in the toolkit.
 *
 * An imported book has no catalog record behind it, so the package document is the only thing that
 * knows its real title and author. Reading it here keeps the import instant and offline: a handful
 * of bytes out of the zip, no rendering, no network.
 */
object EpubMetadataReader {
    /** Title, first creator and first identifier, any of which may be absent. */
    data class Metadata(val title: String?, val author: String?, val identifier: String?)

    /**
     * Read the package document behind `META-INF/container.xml`.
     *
     * Returns null when the file is not a package this reader understands. A book with a file-name
     * title is still a book, so a malformed package must not fail the import; it only means there
     * is nothing better to call it by.
     */
    fun read(file: File): Metadata? =
        runCatching {
                ZipFile(file).use { zip ->
                    val containerEntry = zip.getEntry("META-INF/container.xml")
                    if (containerEntry == null) {
                        null
                    } else {
                        val rootPath =
                            zip.getInputStream(containerEntry).use { stream ->
                                parse(stream)
                                    .getElementsByTagNameNS("*", "rootfile")
                                    .item(0)
                                    ?.let { it as? Element }
                                    ?.getAttribute("full-path")
                            }
                        val opfEntry =
                            rootPath?.takeIf { it.isNotBlank() }?.let { zip.getEntry(it) }
                        if (opfEntry == null) {
                            null
                        } else {
                            val opf = zip.getInputStream(opfEntry).use { parse(it) }

                            fun firstText(tag: String): String? =
                                opf.getElementsByTagNameNS("*", tag)
                                    .item(0)
                                    ?.textContent
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() }

                            Metadata(
                                title = firstText("title"),
                                author = firstText("creator"),
                                identifier = firstText("identifier"),
                            )
                        }
                    }
                }
            }
            .getOrNull()

    /** Parse a small XML document, with DTDs and external entities refused. */
    private fun parse(stream: InputStream): org.w3c.dom.Document =
        DocumentBuilderFactory.newInstance()
            .apply {
                isNamespaceAware = true
                // A book is untrusted input, so the entity-expanding features are turned
                // off — but one at a time: Android's parser does not implement every
                // feature the JDK's does, and a rejected feature there must not cost the
                // whole document.
                trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                trySetFeature("http://xml.org/sax/features/external-general-entities", false)
                trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
                trySetFeature(
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false,
                )
            }
            .newDocumentBuilder()
            .parse(stream)

    /** Apply a parser feature when the implementation knows it. */
    private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }
}
