package net.olamaelcu.livtet

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The wording after "Accept all", including the case that loses books quietly.
 *
 * A run that adds nine of ten and says only "Added 9" is the failure this covers: the reader has no
 * way to know one did not arrive.
 */
class AcceptAllResultTest {
    @Test
    fun a_clean_run_counts_only_what_arrived() {
        assertEquals("Added 4", AcceptAllResult(added = 4, failed = 0).summary)
    }

    @Test
    fun a_partial_run_admits_what_did_not() {
        assertEquals(
            "Added 9 · 1 could not be added",
            AcceptAllResult(added = 9, failed = 1).summary,
        )
    }

    @Test
    fun a_total_failure_does_not_claim_success() {
        assertEquals("Nothing could be added", AcceptAllResult(added = 0, failed = 3).summary)
    }

    @Test
    fun an_empty_inbox_says_so() {
        assertEquals("Nothing to add", AcceptAllResult(added = 0, failed = 0).summary)
    }
}
