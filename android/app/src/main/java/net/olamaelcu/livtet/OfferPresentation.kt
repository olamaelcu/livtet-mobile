package net.olamaelcu.livtet

/** The two shapes the offer inbox takes. */
enum class OfferPresentation {
    /** One book at a time, decided deliberately. */
    DECK,

    /** A queue to drain, with per-row actions. */
    LIST,
}

/**
 * How many offers turn the deck into a list.
 *
 * Ten is the line because that is where reading a card at a time stops being a decision and starts
 * being a chore: nine books can be considered, thirty cannot. At exactly ten the list wins — a
 * threshold that sends the boundary case to the shape that scales is the safer half of the choice.
 */
const val OFFER_LIST_THRESHOLD: Int = 10

/** Which shape the inbox takes for [count] waiting offers. */
fun presentationFor(count: Int): OfferPresentation =
    when {
        count >= OFFER_LIST_THRESHOLD -> OfferPresentation.LIST
        else -> OfferPresentation.DECK
    }
