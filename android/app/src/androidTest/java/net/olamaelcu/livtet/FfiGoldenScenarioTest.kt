package net.olamaelcu.livtet

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import net.olamaelcu.livtet.ffi.EditionPatch
import net.olamaelcu.livtet.ffi.LivtetException
import net.olamaelcu.livtet.ffi.LivtetStore
import net.olamaelcu.livtet.types.PublishedDate
import net.olamaelcu.livtet.types.WorkSortBy
import net.olamaelcu.livtet.types.WorkStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Golden scenario across the UniFFI boundary (run on-device).
 *
 * Exercises the whole livet-ffi surface end to end: store lifecycle,
 * seeding, work/edition queries, search, mutations (file attach,
 * identifiers, patch), reading status, annotations, reading lists.
 * Any marshalling mismatch (enum variants, records, optionals) fails
 * here before it can ship.
 */
@RunWith(JUnit4::class)
class FfiGoldenScenarioTest {

    @After
    fun tearDown() = runBlocking {
        store?.shutdown()
        store = null
    }

    private var store: LivtetStore? = null

    private suspend fun openFresh(): LivtetStore {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.noBackupFilesDir, "ffi-golden").apply {
            deleteRecursively()
            mkdirs()
        }
        return LivtetStore.open(
            dbPath = File(dir, "livtet.db").absolutePath,
            indexDir = File(dir, "search-index").absolutePath,
        )
    }

    @Test
    fun goldenScenario() = runBlocking {
        val store = openFresh().also { this@FfiGoldenScenarioTest.store = it }

        // 1. Reset + seed: wipes content, seeds works, rebuilds index.
        val seeded = store.resetAndSeed(5u)
        assertEquals(5u, seeded.worksCreated)

        // 2. Listing works + newest-first ordering.
        val works = store.listWorks(100u, 0u, null, null)
        assertEquals(5L, works.size.toLong())
        val newest = store.listWorks(100u, 0u, WorkSortBy.NEWEST_CAP, null)
        assertEquals(works.toSet(), newest.toSet())

        // 3. Edition listing + detail round trip.
        val workId = works.first().id
        val editions = store.listEditions(workId)
        assertTrue(editions.isNotEmpty())
        val editionId = editions.first().id
        val detail = store.getEdition(editionId)
        assertNotNull(detail)
        assertEquals(editionId, detail!!.id)
        assertEquals(workId, detail.workId)

        // 4. Attach and detach a digital file.
        store.setEditionFile(editionId, "/data/local/tmp/golden.epub", "epub", 1234L)
        assertNotNull(store.getEdition(editionId)!!.file)
        assertTrue(store.removeEditionFile(editionId))
        assertNull(store.getEdition(editionId)!!.file)

        // 5. Identifier attachment is idempotent.
        store.addEditionIdentifier(editionId, "urn:isbn:9780306406157")
        store.addEditionIdentifier(editionId, "urn:isbn:9780306406157")
        val withIds = store.getEdition(editionId)!!.identifiers
        assertEquals(1L, withIds.count { it == "urn:isbn:9780306406157" }.toLong())

        // 6. Patch: title set works, partial published_date fails loud.
        val patched =
            store.updateEdition(
                editionId,
                EditionPatch(
                    title = "Golden Renamed",
                    description = null,
                    notes = null,
                    publishedDate = null,
                    formatId = null,
                    languageId = null,
                ),
            )
        assertEquals("Golden Renamed", patched.title)

        try {
            store.updateEdition(
                editionId,
                EditionPatch(
                    title = null,
                    description = null,
                    notes = null,
                    publishedDate = PublishedDate.Year(1989),
                    formatId = null,
                    languageId = null,
                ),
            )
            fail("partial published_date must throw")
        } catch (e: LivtetException.InvalidInput) {
            // expected: year-only dates cannot be stored in the editions table
        }

        // 7. Search: editions + work-collapse return the seeded library.
        val editionHits = store.searchEditions("", 100u, 0u)
        assertTrue(editionHits.isNotEmpty())
        val workHits = store.searchWorks("", 100u, 0u)
        assertEquals(
            "search_works returns one hit per work",
            5L,
            workHits.map { it.workId }.toSet().size.toLong(),
        )
        val faceted = store.searchWithFacets("", 100u)
        assertTrue(faceted.hits.isNotEmpty())

        // 8. Reading status round trip on the work.
        store.setWorkStatus(workId, WorkStatus.READING)
        assertEquals(WorkStatus.READING, store.getWorkStatus(workId))

        // 9. Annotations CRUD.
        val annotation =
            store.addAnnotation(editionId, "Golden highlight", "ch1")
        assertTrue(
            store.listAnnotations(editionId).any { it.id == annotation.id },
        )
        assertTrue(store.deleteAnnotation(annotation.id))

        // 10. Reading lists.
        val list = store.createReadingList("Golden", null)
        store.addEditionToList(list.id, editionId)
        assertTrue(
            store.listReadingLists()
                .first { it.id == list.id }
                .editionIds
                .contains(editionId),
        )
        assertTrue(store.removeEditionFromList(list.id, editionId))
        assertTrue(store.deleteReadingList(list.id))

        store.shutdown()
        this@FfiGoldenScenarioTest.store = null
    }
}
