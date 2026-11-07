package net.olamaelcu.livtet

import android.net.Uri
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
 * Importing a book from a file the phone already holds.
 *
 * This is the only door into the library that needs no peer, so it is the one an empty shelf
 * offers: the picker hands over a `content://` document, the bytes are copied into the app's own
 * storage, and the store learns about the book through the same `importEdition` + `attachFile` pair
 * the rest of the app uses.
 *
 * The picker itself is framework glue; what can break silently is everything after it, so the test
 * starts from a `Uri` and checks the three things the shelf and the reader depend on: the copy
 * exists, the edition points at it, and the book is listed.
 */
@RunWith(AndroidJUnit4::class)
class ImportBookTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun anEpubFromThePhoneBecomesReadableBook() = runBlocking {
        Bridge.init(File(context.filesDir, "import-book-test.db").absolutePath)

        val source = TestEpub.write(context, "On The Phone")
        val imported = ImportBook.from(context, Uri.fromFile(source))

        assertEquals("On The Phone", imported.title)

        val copy = File(imported.path)
        assertTrue("the bytes were copied into the app's own storage", copy.isFile)
        assertTrue(
            "the copy lives under files/books, not wherever the picker pointed",
            copy.absolutePath.contains("/books/"),
        )
        assertEquals(source.length(), copy.length())

        // The library lists works; the edition is what the import created and what
        // the shelf opens, so the check is on that link rather than on the book id.
        val listed =
            Bridge.listBooks(limit = 200).firstOrNull { it.primaryEditionId == imported.editionId }
        assertTrue("the imported edition is in the library", listed != null)
        assertEquals("On The Phone", listed?.title)
        assertEquals(
            "the edition points at the copy the reader will open",
            imported.path,
            Bridge.editionFilePath(imported.editionId),
        )
    }

    @Test
    fun aFileTheReaderCannotOpenIsRefusedByFormat() = runBlocking {
        Bridge.init(File(context.filesDir, "import-book-test.db").absolutePath)

        val source = File(context.cacheDir, "notes.txt").apply { writeText("not a book") }
        var message: String? = null
        try {
            ImportBook.from(context, Uri.fromFile(source))
        } catch (error: IllegalArgumentException) {
            message = error.message
        }

        assertTrue(
            "a text file is rejected with the format named: $message",
            message != null && message.contains("notes.txt"),
        )
        assertFalse(
            "the rejected file is not copied into the library",
            File(context.filesDir, "books").listFiles().orEmpty().any {
                it.name.endsWith("notes.txt")
            },
        )
    }
}
