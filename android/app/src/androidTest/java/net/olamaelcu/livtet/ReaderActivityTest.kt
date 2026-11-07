package net.olamaelcu.livtet

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment

/**
 * The reader screen, started the way the launcher starts it: an intent naming an edition, and
 * nothing else.
 *
 * The activity has to resolve that edition's `digital_inventory` row for the file to point at, so
 * this seeds a real edition, attaches a real EPUB, opens it, and checks both the navigator and the
 * position it writes back — the row the peer pulls. Those are the three steps that could silently
 * break: the store lookup, the toolkit's parse, and the progress write.
 */
@RunWith(AndroidJUnit4::class)
class ReaderActivityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databasePath = File(context.filesDir, "reader-activity-test.db").absolutePath
    private var scenario: ActivityScenario<ReaderActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun anEditionWithAFileOpensAndRecordsItsPosition() = runBlocking {
        resetDatabase()
        Bridge.init(databasePath)

        val editionId = Bridge.importEdition("The Reverse Direction", "a seeded edition")
        val epub = writeEpub()
        Bridge.attachFile(
            editionId = editionId,
            path = epub.absolutePath,
            format = "epub",
            sizeBytes = epub.length(),
        )

        scenario = ActivityScenario.launch(ReaderActivity.intent(context, editionId))

        val navigator = awaitNavigator()
        assertNotNull("the reader screen attached Readium's navigator", navigator)
        val locator = awaitLocator(navigator!!)
        assertNotNull("the navigator reports a position once it renders", locator)
        assertTrue(
            "the position is inside the chapter: ${locator?.href}",
            locator?.href?.toString()?.contains("chapter1.xhtml") == true,
        )

        // The nav emits a position once the publication renders; that is the
        // row a peer picks up, so it has to arrive without any user gesture.
        val progress = awaitProgress(editionId)
        assertNotNull("a position is recorded after opening", progress)
        assertEquals(net.olamaelcu.livtet.types.ProgressUnit.PERCENTAGE, progress?.progressUnit)
        assertTrue(
            "the stored Locator points into the book: ${progress?.lastLocation}",
            progress?.lastLocation?.contains("chapter1.xhtml") == true,
        )
    }

    @Test
    fun anIntentWithoutAnEditionFinishesInsteadOfRenderingNothing() {
        resetDatabase()
        runBlocking { Bridge.init(databasePath) }

        scenario =
            ActivityScenario.launch(android.content.Intent(context, ReaderActivity::class.java))

        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (scenario!!.state == androidx.lifecycle.Lifecycle.State.DESTROYED) return
            Thread.sleep(100)
        }
        assertTrue(
            "the screen must not stay open without a book",
            scenario!!.state == androidx.lifecycle.Lifecycle.State.DESTROYED,
        )
    }

    private fun resetDatabase() {
        listOf("", "-wal", "-shm").forEach { suffix -> File(databasePath + suffix).delete() }
    }

    private fun awaitNavigator(): EpubNavigatorFragment? {
        val deadline = System.currentTimeMillis() + 40_000
        while (System.currentTimeMillis() < deadline) {
            var navigator: EpubNavigatorFragment? = null
            scenario?.onActivity { activity ->
                navigator =
                    activity.supportFragmentManager.findFragmentById(R.id.reader_host)
                        as? EpubNavigatorFragment
            }
            if (navigator != null) return navigator
            Thread.sleep(250)
        }
        return null
    }

    private fun awaitLocator(
        navigator: EpubNavigatorFragment
    ): org.readium.r2.shared.publication.Locator? {
        val deadline = System.currentTimeMillis() + 40_000
        while (System.currentTimeMillis() < deadline) {
            val locator = navigator.currentLocator.value
            if (locator != null) return locator
            Thread.sleep(250)
        }
        return null
    }

    private fun awaitProgress(editionId: String): net.olamaelcu.livtet.ffi.ReadingProgress? {
        val deadline = System.currentTimeMillis() + 40_000
        while (System.currentTimeMillis() < deadline) {
            val progress = runBlocking { Bridge.readingProgress(editionId) }
            if (progress != null) return progress
            Thread.sleep(250)
        }
        return null
    }

    /** A one-chapter EPUB 3, written at test time. */
    private fun writeEpub(): File {
        val file = File(context.cacheDir, "reverse-direction-reader.epub")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            val mimetype = ZipEntry("mimetype")
            zip.putNextEntry(mimetype)
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()
            zip.entry("META-INF/container.xml", CONTAINER)
            zip.entry("OEBPS/content.opf", PACKAGE)
            zip.entry("OEBPS/chapter1.xhtml", CHAPTER)
        }
        return file
    }

    private fun ZipOutputStream.entry(name: String, body: String) {
        putNextEntry(ZipEntry(name))
        write(body.toByteArray())
        closeEntry()
    }

    private companion object {
        const val CONTAINER =
            """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

        const val PACKAGE =
            """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="pub-id">urn:uuid:0b8f1f62-1f2f-4a44-9a1e-6f0b2b8a7c11</dc:identifier>
    <dc:title>The Reverse Direction</dc:title>
    <dc:language>en</dc:language>
    <meta property="dcterms:modified">2024-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="ch1"/>
  </spine>
</package>"""

        const val CHAPTER =
            """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
  <head><title>One</title></head>
  <body><p>Opened from the inventory row the peer wrote.</p></body>
</html>"""
    }
}
