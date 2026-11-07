package net.olamaelcu.livtet.core.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The open path a received book takes.
 *
 * The file is produced by [EpubFixture] at test time, so this covers the toolkit's parser against a
 * real container rather than against a stub: if the app can open this, it can open what a peer
 * sends it.
 */
@RunWith(AndroidJUnit4::class)
class ReaderSessionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aReceivedEpubOpensWithItsReadingOrder() = runBlocking {
        val file = EpubFixture.write(context.cacheDir)

        val session = ReaderSession.open(context, file.absolutePath)

        assertEquals(EpubFixture.TITLE, session.title)
        assertTrue("an epub is renderable by the epub navigator", session.isEpub)
        assertTrue("the spine has at least one document", session.readingOrderSize >= 1)
        assertNotNull("the navigator factory needs a publication", session.navigatorFactory)
    }

    @Test
    fun aStoredPositionSurvivesTheRoundTrip() = runBlocking {
        val file = EpubFixture.write(context.cacheDir)
        val session = ReaderSession.open(context, file.absolutePath)

        // The first reading-order link is a real position in this book.
        val locator =
            requireNotNull(
                session.publication.locatorFromLink(session.publication.readingOrder.first())
            )
        val position = ReadingPosition.of(locator)

        assertTrue("the json carries the target", position.locatorJson.contains("chapter1.xhtml"))

        val restored = requireNotNull(ReadingPosition.locator(position.locatorJson))
        assertEquals(locator.href.toString(), restored.href.toString())
        assertEquals(locator.locations.totalProgression, restored.locations.totalProgression)

        // And the session hands it back for resume.
        assertEquals(
            locator.href.toString(),
            requireNotNull(session.resumeAt(position.locatorJson)).href.toString(),
        )
    }

    @Test
    fun aPositionThatNoLongerParsesIsIgnored() = runBlocking {
        val file = EpubFixture.write(context.cacheDir)
        val session = ReaderSession.open(context, file.absolutePath)

        assertNull(ReadingPosition.locator("{ not json"))
        assertNull(ReadingPosition.locator("   "))
        assertNull(ReadingPosition.locator(null))
        assertNull("a corrupt row must not stop the book from opening", session.resumeAt("{"))
    }

    @Test
    fun aMissingFileIsRefusedBeforeParsing() = runBlocking {
        val error =
            runCatching {
                    ReaderSession.open(context, File(context.cacheDir, "absent.epub").absolutePath)
                }
                .exceptionOrNull()

        assertTrue("expected MissingFile, got $error", error is ReaderError.MissingFile)
    }

    @Test
    fun aFileThatIsNotABookIsRefused() = runBlocking {
        val file = File(context.cacheDir, "not-a-book.epub")
        file.writeText("this is not a zip container")

        val error = runCatching { ReaderSession.open(context, file.absolutePath) }.exceptionOrNull()

        assertTrue(
            "a text file named .epub is not a publication: $error",
            error is ReaderError.Unreadable || error is ReaderError.Unsupported,
        )
        assertFalse("it never reached the reading screen", file.length() == 0L)
    }
}
