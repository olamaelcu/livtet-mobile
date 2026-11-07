package net.olamaelcu.livtet

/** What an empty shelf should be asking for, given what is already possible. */
enum class HomePromptKind {
    /** A peer offered books: the shelf slot becomes a way to review them. */
    REVIEW_OFFERS,

    /** A device is paired, so books can be asked for rather than hunted for. */
    RECEIVE_FROM_PEER,

    /** Nothing paired: the only way in is to connect a device. */
    PAIR_DEVICE,
}

/**
 * The card that stands where an empty shelf would be.
 *
 * An empty library has different causes and each wants a different verb: asking someone to pair a
 * device while three books sit unaccepted from one they already paired is the sort of prompt that
 * teaches a reader to ignore prompts. The order is therefore by what is already closest to
 * arriving, not by what is easiest to offer.
 */
data class HomePrompt(
    val kind: HomePromptKind,
    val title: String,
    val detail: String,
    val primaryLabel: String,
) {
    companion object {
        /** Which prompt an empty library gets. */
        fun forState(offersWaiting: Int, pairedDevices: Int, pairedName: String?): HomePrompt =
            when {
                offersWaiting > 0 ->
                    HomePrompt(
                        kind = HomePromptKind.REVIEW_OFFERS,
                        title =
                            if (offersWaiting == 1) {
                                "1 book is waiting"
                            } else {
                                "$offersWaiting books are waiting"
                            },
                        detail =
                            if (pairedName != null) {
                                "Offered by $pairedName. Accepting copies it here."
                            } else {
                                "A paired device is offering books. Accepting copies one here."
                            },
                        primaryLabel = "Review",
                    )

                pairedDevices > 0 ->
                    HomePrompt(
                        kind = HomePromptKind.RECEIVE_FROM_PEER,
                        title =
                            if (pairedDevices == 1 && pairedName != null) {
                                "Receive from $pairedName"
                            } else {
                                "Receive from your devices"
                            },
                        detail = "Ask a paired device to send a book to this phone.",
                        primaryLabel = "Open devices",
                    )

                else ->
                    HomePrompt(
                        kind = HomePromptKind.PAIR_DEVICE,
                        title = "Add your first book",
                        detail =
                            "Books arrive from your desktop, or from a file already on this phone.",
                        primaryLabel = "Pair a device",
                    )
            }
    }
}
