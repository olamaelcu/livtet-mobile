package net.olamaelcu.livtet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Named reading lists, end to end through the store.
 *
 * The screen renders whatever these calls return, so the interesting part is the lifecycle: a shelf
 * exists, a book can be filed on it once, a rename keeps the members, and deleting the shelf does
 * not touch the book.
 */
@RunWith(AndroidJUnit4::class)
class ShelfTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aShelfCanBeMadeFilledRenamedAndDeleted() = runBlocking {
        Bridge.init(File(context.filesDir, "shelf-test.db").absolutePath)

        val editionId = Bridge.importEdition("Shelf Book ${System.nanoTime()}", null)
        val shelf = Bridge.createShelf("Winter ${System.nanoTime()}")

        assertEquals(0, shelf.editionIds.size)

        Bridge.addToShelf(shelf.id, editionId)
        Bridge.addToShelf(shelf.id, editionId) // no-op, not a duplicate row

        val afterAdd = Bridge.listShelves().first { it.id == shelf.id }
        assertEquals(listOf(editionId), afterAdd.editionIds)

        val renamed = Bridge.renameShelf(shelf.id, "Snow")
        assertEquals("Snow", renamed.name)
        assertEquals("a rename keeps its books", listOf(editionId), renamed.editionIds)

        assertTrue(Bridge.removeFromShelf(shelf.id, editionId))
        assertFalse("removing twice is a no-op", Bridge.removeFromShelf(shelf.id, editionId))

        val empty = Bridge.listShelves().first { it.id == shelf.id }
        assertTrue(empty.editionIds.isEmpty())

        assertTrue(Bridge.deleteShelf(shelf.id))
        assertFalse(Bridge.deleteShelf(shelf.id))
        assertTrue(Bridge.listShelves().none { it.id == shelf.id })
        // The book outlives the shelf it was filed on.
        assertNotNull(Bridge.editionDetail(editionId))
    }
}
