package net.olamaelcu.livtet

import android.content.Context
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A minimal, valid EPUB 3, written where a picker or an import can find it.
 *
 * Shared by the tests that need a real book on disk: the toolkit parses this file, so it has to be
 * a genuine package rather than a placeholder with the right name.
 */
object TestEpub {
    fun write(context: Context, title: String, directory: File = context.cacheDir): File {
        val target = File(directory, "$title.epub")
        ZipOutputStream(target.outputStream()).use { zip ->
            // The mimetype entry must come first and be stored, not deflated.
            val mimetype = "application/epub+zip".toByteArray()
            zip.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimetype.size.toLong()
                    crc = CRC32().apply { update(mimetype) }.value
                }
            )
            zip.write(mimetype)

            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """
                    .trimIndent()
                    .toByteArray()
            )

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="uid">urn:uuid:9d1c8f3e-1111-4a2b-9c3d-000000000001</dc:identifier>
                    <dc:title>$title</dc:title>
                    <dc:language>en</dc:language>
                    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="ch1"/></spine>
                </package>
                """
                    .trimIndent()
                    .toByteArray()
            )

            zip.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                  <head><title>Contents</title></head>
                  <body><nav epub:type="toc"><ol><li><a href="ch1.xhtml">Start</a></li></ol></nav></body>
                </html>
                """
                    .trimIndent()
                    .toByteArray()
            )

            zip.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title>Start</title></head>
                  <body><h1>Start</h1><p>Imported on the phone.</p></body>
                </html>
                """
                    .trimIndent()
                    .toByteArray()
            )
        }
        return target
    }
}
