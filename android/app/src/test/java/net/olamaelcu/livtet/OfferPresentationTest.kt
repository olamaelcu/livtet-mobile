package net.olamaelcu.livtet

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule that decides which inbox shape a count gets.
 *
 * It is a one-line function, but it is the whole design decision: nine books can be considered one
 * at a time, a queue cannot. Pinning it means a later tweak to the constant is a deliberate act
 * rather than a drift.
 */
class OfferPresentationTest {
    @Test
    fun aFewOffersGetTheDeck() {
        for (count in 0..9) {
            assertEquals("$count offers", OfferPresentation.DECK, presentationFor(count))
        }
    }

    @Test
    fun tenOrMoreGetTheList() {
        assertEquals(
            "the boundary belongs to the list",
            OfferPresentation.LIST,
            presentationFor(10),
        )
        for (count in 11..100) {
            assertEquals("$count offers", OfferPresentation.LIST, presentationFor(count))
        }
    }
}
