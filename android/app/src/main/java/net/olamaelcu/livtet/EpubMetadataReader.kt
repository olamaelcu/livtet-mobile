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
    /**
     * What the package says about itself. Every field may be absent; a book with a file-name title
     * is still a book.
     *
     * [coverHref] is the zip entry holding the cover image, already resolved against the package
     * document's directory — the caller reads the bytes.
     */
    data class Metadata(
        val title: String?,
        val author: String?,
        val identifier: String?,
        val coverHref: String? = null,
    )

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
                                coverHref = coverHref(opf, rootPath),
                            )
                        }
                    }
                }
            }
            .getOrNull()

    /**
     * The zip entry the package names as its cover, if any.
     *
     * EPUB 3 marks it with `properties="cover-image"`; EPUB 2 names an item in `<meta name="cover"
     * content="id">`. Both are common enough that neither can be skipped, and a package with
     * neither still often has a file whose name says what it is — which is the last resort, not the
     * first.
     */
    private fun coverHref(opf: org.w3c.dom.Document, packagePath: String): String? {
        val items = opf.getElementsByTagNameNS("*", "item")
        val manifest = (0 until items.length).mapNotNull { index -> items.item(index) as? Element }

        fun resolve(href: String?): String? {
            val clean = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val directory = packagePath.substringBeforeLast('/', "")
            val joined = if (directory.isEmpty()) clean else "$directory/$clean"
            // Normalise `..` and `.` the way a zip reader expects.
            val parts = ArrayDeque<String>()
            joined.split('/').forEach { part ->
                when (part) {
                    "",
                    "." -> Unit
                    ".." -> parts.removeLastOrNull()
                    else -> parts.addLast(part)
                }
            }
            return parts.joinToString("/").takeIf { it.isNotEmpty() }
        }

        val propertyCover = manifest.firstOrNull {
            it.getAttribute("properties").split(' ').contains("cover-image")
        }
        propertyCover?.let {
            return resolve(it.getAttribute("href"))
        }

        val metaId =
            opf.getElementsByTagNameNS("*", "meta").let { metas ->
                (0 until metas.length)
                    .mapNotNull { metas.item(it) as? Element }
                    .firstOrNull { it.getAttribute("name").equals("cover", ignoreCase = true) }
                    ?.getAttribute("content")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
        metaId?.let { id ->
            manifest
                .firstOrNull { it.getAttribute("id") == id }
                ?.let {
                    return resolve(it.getAttribute("href"))
                }
        }

        return manifest
            .firstOrNull {
                it.getAttribute("media-type").startsWith("image/") &&
                    (it.getAttribute("id").contains("cover", ignoreCase = true) ||
                        it.getAttribute("href").contains("cover", ignoreCase = true))
            }
            ?.let { resolve(it.getAttribute("href")) }
    }

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
