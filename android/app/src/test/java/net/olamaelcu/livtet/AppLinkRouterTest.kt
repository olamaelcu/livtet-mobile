package net.olamaelcu.livtet

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppLinkRouterTest {
    private fun parse(raw: String?) = AppLinkRouter.parse(raw)

    @Test
    fun `parses a canonical book permalink`() {
        assertEquals(
            AppLink.Book(AppLinkRouter.BIBLIOGRAPH_DID, "ol.OL3425796M"),
            parse(
                "https://livtet.olamaelcu.net/book/did:web:biblio.livtet.olamaelcu.net/ol.OL3425796M",
            ),
        )
    }

    @Test
    fun `resolves the catalog alias to its did`() {
        assertEquals(
            AppLink.Book(AppLinkRouter.BIBLIOGRAPH_DID, "ol.OL32848840M"),
            parse("https://livtet.olamaelcu.net/book/bibliograph/ol.OL32848840M"),
        )
    }

    @Test
    fun `tolerates a trailing slash`() {
        assertEquals(
            AppLink.Book("did:plc:abc", "3abc"),
            parse("https://livtet.olamaelcu.net/book/did:plc:abc/3abc/"),
        )
    }

    @Test
    fun `parses a review permalink`() {
        assertEquals(
            AppLink.Review("did:plc:e2ctbutx6kya6si4if5ngjmm", "3myy5wjqiydz7"),
            parse(
                "https://livtet.olamaelcu.net/review/did:plc:e2ctbutx6kya6si4if5ngjmm/3myy5wjqiydz7",
            ),
        )
    }

    @Test
    fun `parses a profile handle and defaults to the reviews tab`() {
        assertEquals(
            AppLink.Profile("jacky.wtf", AppLinkRouter.TAB_REVIEWS, null),
            parse("https://livtet.olamaelcu.net/profile/jacky.wtf"),
        )
    }

    @Test
    fun `parses the tab query value`() {
        assertEquals(
            AppLink.Profile("jacky.wtf", AppLinkRouter.TAB_TO_READ, null),
            parse("https://livtet.olamaelcu.net/profile/jacky.wtf?tab=to-read"),
        )
    }

    @Test
    fun `falls back to reviews for an unknown tab`() {
        assertEquals(
            AppLink.Profile("jacky.wtf", AppLinkRouter.TAB_REVIEWS, null),
            parse("https://livtet.olamaelcu.net/profile/jacky.wtf?tab=whatever"),
        )
    }

    @Test
    fun `maps the shelf index to the shelves tab`() {
        assertEquals(
            AppLink.Profile("did:plc:e2ctbutx6kya6si4if5ngjmm", AppLinkRouter.TAB_SHELVES, null),
            parse("https://livtet.olamaelcu.net/profile/did:plc:e2ctbutx6kya6si4if5ngjmm/shelf"),
        )
    }

    @Test
    fun `maps one shelf to the shelves tab with the shelf selected`() {
        assertEquals(
            AppLink.Profile("did:plc:e2ctbutx6kya6si4if5ngjmm", AppLinkRouter.TAB_SHELVES, "read"),
            parse(
                "https://livtet.olamaelcu.net/profile/did:plc:e2ctbutx6kya6si4if5ngjmm/shelf/read",
            ),
        )
    }

    @Test
    fun `parses the u alias of a profile`() {
        assertEquals(
            AppLink.Profile("did:plc:abc", AppLinkRouter.TAB_REVIEWS, null),
            parse("https://livtet.olamaelcu.net/u/did:plc:abc"),
        )
    }

    @Test
    fun `parses the custom scheme with the host as prefix`() {
        assertEquals(
            AppLink.Book(AppLinkRouter.BIBLIOGRAPH_DID, "ol.OL32848840M"),
            parse("livtet://book/bibliograph/ol.OL32848840M"),
        )
        assertEquals(
            AppLink.Review("did:plc:abc", "3abc"),
            parse("livtet://review/did:plc:abc/3abc"),
        )
        assertEquals(
            AppLink.Profile("jacky.wtf", AppLinkRouter.TAB_FINISHED, null),
            parse("livtet://profile/jacky.wtf?tab=finished"),
        )
    }

    @Test
    fun `builds the navigation route for each link`() {
        assertEquals("book/did:plc:abc/3abc", AppLink.Book("did:plc:abc", "3abc").navigationRoute)
        assertEquals(
            "review/did:plc:abc/3abc",
            AppLink.Review("did:plc:abc", "3abc").navigationRoute,
        )
        assertEquals(
            "profile/jacky.wtf?tab=reviews",
            AppLink.Profile("jacky.wtf", AppLinkRouter.TAB_REVIEWS, null).navigationRoute,
        )
        assertEquals(
            "profile/jacky.wtf?tab=shelves&shelf=read",
            AppLink.Profile("jacky.wtf", AppLinkRouter.TAB_SHELVES, "read").navigationRoute,
        )
    }

    @Test
    fun `rejects unhandled input`() {
        val rejected =
            listOf(
                null,
                "",
                "   ",
                "not a url",
                "livtet",
                // Wrong scheme or host.
                "http://livtet.olamaelcu.net/book/did:plc:abc/3abc",
                "https://example.com/book/did:plc:abc/3abc",
                "livtet://unknown/did:plc:abc/3abc",
                // Wrong segment counts.
                "https://livtet.olamaelcu.net/book/did:plc:abc",
                "https://livtet.olamaelcu.net/book/did:plc:abc/3abc/extra",
                "https://livtet.olamaelcu.net/review",
                "https://livtet.olamaelcu.net/profile",
                "https://livtet.olamaelcu.net/profile/jacky.wtf/shelf/read/extra",
                "https://livtet.olamaelcu.net/profile/jacky.wtf/books",
                "https://livtet.olamaelcu.net/u",
                // Invalid did, rkey, and shelf values.
                "https://livtet.olamaelcu.net/book/notadid/3abc",
                "https://livtet.olamaelcu.net/book/did:plc:abc/",
                "https://livtet.olamaelcu.net/book/did:plc:abc/%20",
                "https://livtet.olamaelcu.net/review/did:plc:abc/a/b",
                "https://livtet.olamaelcu.net/profile/jacky.wtf/shelf/read%2Fmore",
                "https://livtet.olamaelcu.net/profile/not%20a%20handle",
                "livtet://book/did:plc:abc/${"x".repeat(513)}",
            )
        for (raw in rejected) {
            assertNull(parse(raw), "expected null for $raw")
        }
    }

    @Test
    fun `handle publishes only handled links`() {
        AppLinkRouter.consume()
        assertEquals(false, AppLinkRouter.handle("https://example.com/book/did:plc:abc/3abc"))
        assertNull(AppLinkRouter.pending.value)
        assertEquals(true, AppLinkRouter.handle("livtet://book/did:plc:abc/3abc"))
        assertEquals(AppLink.Book("did:plc:abc", "3abc"), AppLinkRouter.pending.value)
        AppLinkRouter.consume()
        assertNull(AppLinkRouter.pending.value)
        assertEquals(true, AppLinkRouter.canHandle("livtet://profile/jacky.wtf"))
        assertEquals(false, AppLinkRouter.canHandle("livtet://profile"))
    }
}
