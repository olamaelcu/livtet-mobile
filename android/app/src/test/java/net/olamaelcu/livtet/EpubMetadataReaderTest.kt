package net.olamaelcu.livtet

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading an EPUB's package document.
 *
 * This is the difference between a shelf of "on-the-phone" and one of book titles: an imported file
 * has no catalog record, so whatever is in the package is all the library will ever know about it.
 */
class EpubMetadataReaderTest {
    @Test
    fun readsTitleCreatorAndIdentifier() {
        val file =
            epub("On The Phone", author = "Jacky Alcine", identifier = "urn:isbn:9780306406157")

        val metadata = EpubMetadataReader.read(file)

        assertEquals("On The Phone", metadata?.title)
        assertEquals("Jacky Alcine", metadata?.author)
        assertEquals("urn:isbn:9780306406157", metadata?.identifier)
    }

    @Test
    fun aBookWithNoCreatorStillHasATitle() {
        val metadata = EpubMetadataReader.read(epub("Anonymous Work"))

        assertEquals("Anonymous Work", metadata?.title)
        assertNull(metadata?.author)
    }

    @Test
    fun somethingThatIsNotAPackageIsNotABook() {
        val file = File.createTempFile("not-a-book", ".epub").apply { writeText("plain text") }

        assertNull(EpubMetadataReader.read(file))
    }

    /** A minimal package; the same shape `TestEpub` writes for the device tests. */
    private fun epub(
        title: String,
        author: String? = null,
        identifier: String = "urn:uuid:9d1c8f3e-1111-4a2b-9c3d-000000000001",
    ): File {
        val target = File.createTempFile(title.replace(' ', '-'), ".epub")
        ZipOutputStream(target.outputStream()).use { zip ->
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
                    <dc:identifier id="uid">$identifier</dc:identifier>
                    <dc:title>$title</dc:title>
                    <dc:language>en</dc:language>
                    ${author?.let { "<dc:creator>$it</dc:creator>" }.orEmpty()}
                  </metadata>
                  <manifest>
                    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="ch1"/></spine>
                </package>
                """
                    .trimIndent()
                    .toByteArray()
            )
        }
        assertTrue(target.length() > 0)
        return target
    }
}
