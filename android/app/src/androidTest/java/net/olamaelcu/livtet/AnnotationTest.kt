package net.olamaelcu.livtet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Notes pinned to a passage.
 *
 * The location is the toolkit's `Locator` JSON, so what matters is that it survives the round trip
 * unchanged — that is what lets a note lead back to the page it was written on.
 */
@RunWith(AndroidJUnit4::class)
class AnnotationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aNoteKeepsItsPassageAndCanBeRemoved() = runBlocking {
        Bridge.init(File(context.filesDir, "annotation-test.db").absolutePath)

        val editionId = Bridge.importEdition("Noted ${System.nanoTime()}", null)
        val location = """{"href":"ch1.xhtml","locations":{"progression":0.25}}"""

        val id = Bridge.addAnnotation(editionId, "the good bit", location)

        val notes = Bridge.listAnnotations(editionId)
        assertEquals(1, notes.size)
        assertEquals("the good bit", notes.first().content)
        assertEquals(location, notes.first().location)

        assertTrue(Bridge.deleteAnnotation(id))
        assertFalse("deleting twice is a no-op", Bridge.deleteAnnotation(id))
        assertTrue(Bridge.listAnnotations(editionId).isEmpty())
    }
}
