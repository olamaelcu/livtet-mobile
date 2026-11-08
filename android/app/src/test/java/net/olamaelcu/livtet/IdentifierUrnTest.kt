package net.olamaelcu.livtet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The import API parses URNs only; an EPUB may name a bare ISBN, and handing that over verbatim
 * failed the whole import (a real book on the desktop, refused on the phone).
 */
class IdentifierUrnTest {
    @Test
    fun `a bare isbn becomes an isbn urn`() {
        assertEquals("urn:isbn:9780593714263", identifierUrn("9780593714263"))
        assertEquals("urn:isbn:9780593714263", identifierUrn("978-0-593-71426-3"))
        assertEquals("urn:isbn:9780593714263", identifierUrn("ISBN 978 0 593 71426 3"))
    }

    @Test
    fun `an isbn10 keeps its check digit`() {
        assertEquals("urn:isbn:0306406152", identifierUrn("0-306-40615-2"))
        assertEquals("urn:isbn:030640615X", identifierUrn("030640615x"))
    }

    @Test
    fun `an existing urn is left as written`() {
        assertEquals("urn:isbn:9780593714263", identifierUrn("urn:isbn:9780593714263"))
        assertEquals("urn:uuid:1234", identifierUrn("urn:uuid:1234"))
    }

    @Test
    fun `text the parser cannot take is dropped rather than failing the book`() {
        assertNull(identifierUrn(null))
        assertNull(identifierUrn("   "))
        assertNull(identifierUrn("https://example.com/book"))
        assertNull(identifierUrn("12345"))
    }
}
