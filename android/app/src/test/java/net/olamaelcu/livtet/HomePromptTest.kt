package net.olamaelcu.livtet

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An empty library is not one state. Which prompt it shows decides what a reader's first action in
 * the app is, so the ordering is pinned here rather than being an accident of whichever check the
 * composable happens to make first.
 */
class HomePromptTest {
    @Test
    fun `offers waiting outrank pairing`() {
        val prompt =
            HomePrompt.forState(offersWaiting = 2, pairedDevices = 1, pairedName = "Studio")

        assertEquals(HomePromptKind.REVIEW_OFFERS, prompt.kind)
        assertEquals("Review", prompt.primaryLabel)
        assertEquals("2 books are waiting", prompt.title)
    }

    @Test
    fun `a single waiting book is not plural`() {
        val prompt = HomePrompt.forState(offersWaiting = 1, pairedDevices = 0, pairedName = null)

        assertEquals("1 book is waiting", prompt.title)
    }

    @Test
    fun `a paired device asks for a book before asking to pair`() {
        val prompt =
            HomePrompt.forState(offersWaiting = 0, pairedDevices = 1, pairedName = "Studio")

        assertEquals(HomePromptKind.RECEIVE_FROM_PEER, prompt.kind)
        assertEquals("Receive from Studio", prompt.title)
        assertEquals("Open devices", prompt.primaryLabel)
    }

    @Test
    fun `several paired devices do not name one of them`() {
        val prompt =
            HomePrompt.forState(offersWaiting = 0, pairedDevices = 3, pairedName = "Studio")

        assertEquals("Receive from your devices", prompt.title)
    }

    @Test
    fun `nothing paired falls back to pairing`() {
        val prompt = HomePrompt.forState(offersWaiting = 0, pairedDevices = 0, pairedName = null)

        assertEquals(HomePromptKind.PAIR_DEVICE, prompt.kind)
        assertEquals("Add your first book", prompt.title)
        assertEquals("Pair a device", prompt.primaryLabel)
    }
}
