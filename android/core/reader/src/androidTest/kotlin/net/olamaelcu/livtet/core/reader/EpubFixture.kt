package net.olamaelcu.livtet.core.reader

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A tiny, valid EPUB 3 written on demand.
 *
 * Generated rather than checked in: the file is a container format, so a
 * binary fixture hides what the parser is actually being fed, and a diff cannot
 * show whether the test still covers the spine it claims to.
 */
internal object EpubFixture {
    const val TITLE = "The Reverse Direction"

    /** Write a one-chapter EPUB to `directory` and return it. */
    fun write(directory: File): File {
        val file = File(directory, "reverse-direction.epub")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            // `mimetype` must be the first entry and must be stored, not
            // deflated, or readers refuse the file before looking further.
            val mimetype = ZipEntry("mimetype")
            zip.putNextEntry(mimetype)
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()

            zip.writeEntry("META-INF/container.xml", containerDocument())
            zip.writeEntry("OEBPS/content.opf", packageDocument())
            zip.writeEntry("OEBPS/nav.xhtml", navDocument())
            zip.writeEntry("OEBPS/chapter1.xhtml", chapter())
        }
        return file
    }

    private fun ZipOutputStream.writeEntry(name: String, body: String) {
        putNextEntry(ZipEntry(name))
        write(body.toByteArray())
        closeEntry()
    }

    private fun packageDocument() =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:identifier id="pub-id">urn:uuid:8fbf2e80-6f1f-4f7b-9d5e-2b6f0b9a1c33</dc:identifier>
            <dc:title>$TITLE</dc:title>
            <dc:language>en</dc:language>
            <dc:creator>Jacky Alcine</dc:creator>
            <meta property="dcterms:modified">2024-01-01T00:00:00Z</meta>
          </metadata>
          <manifest>
            <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine>
            <itemref idref="ch1"/>
          </spine>
        </package>
        """.trimIndent()

    private fun navDocument() =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
          <head><title>Contents</title></head>
          <body>
            <nav epub:type="toc" id="toc">
              <ol><li><a href="chapter1.xhtml">One</a></li></ol>
            </nav>
          </body>
        </html>
        """.trimIndent()

    private fun chapter() =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml">
          <head><title>One</title></head>
          <body><p>The book a peer offered, opened on the phone that received it.</p></body>
        </html>
        """.trimIndent()

    private fun containerDocument() =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
        """.trimIndent()
}
