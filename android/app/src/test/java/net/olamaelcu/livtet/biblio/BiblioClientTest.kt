package net.olamaelcu.livtet.biblio

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BiblioClientTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): BiblioClient {
        val engine = MockEngine { request -> handler(request) }
        val http =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
            }
        return BiblioClient(http)
    }

    @Test
    fun `getEdition decodes a hydrated edition view`() =
        runBlocking {
            val payload =
                """
            {
              "uri": "at://did:web:biblio.livtet.olamaelcu.net/community.lexicon.book.edition/ol.OL3425796M",
              "cid": "bafyedition",
              "value": {
                "uri": "at://did:web:biblio.livtet.olamaelcu.net/community.lexicon.book.edition/ol.OL3425796M",
                "title": "Dune",
                "description": "A desert planet.",
                "coverImageUrl": "https://covers.example/dune.jpg",
                "publishedYear": 1965,
                "publishDate": "1965-08-01",
                "language": "en",
                "pages": 512,
                "format": { "description": "Hardcover", "emoji": "📕", "unit": "book" },
                "genres": [{ "name": "Science fiction", "emoji": "🚀" }],
                "identifiers": [{ "resource": "isbn", "uri": "isbn:9780441172719" }],
                "contributors": [
                  { "contributor": { "uri": "at://did:plc:c1/community.lexicon.book.contributor/herbert", "name": "Frank Herbert" }, "role": "author" },
                  { "contributor": { "uri": "at://did:plc:c2/community.lexicon.book.contributor/x", "name": "Someone Else" }, "role": "editor" }
                ]
              }
            }
            """
            val client =
                client { request ->
                    assertEquals("/xrpc/com.atproto.repo.getRecord", request.url.encodedPath)
                    assertEquals("community.lexicon.book.edition", request.url.parameters["collection"])
                    assertEquals("ol.OL3425796M", request.url.parameters["rkey"])
                    respond(payload, HttpStatusCode.OK, jsonHeaders)
                }

            val book = client.getEdition("did:web:biblio.livtet.olamaelcu.net", "ol.OL3425796M")

            assertEquals("Dune", book.title)
            assertEquals("Frank Herbert", book.authorLine())
            assertEquals("9780441172719", book.isbn())
            assertEquals("Hardcover", book.format?.description)
            assertEquals(1, book.genres?.size)
        }

    @Test
    fun `a non-2xx edition read surfaces a BiblioException`() =
        runBlocking {
            val client = client { respond("nope", HttpStatusCode.NotFound) }
            assertFailsWith<BiblioException> {
                client.getEdition("did:web:biblio.livtet.olamaelcu.net", "missing")
            }
            Unit
        }

    @Test
    fun `getBookReviews prefers the AppView list`() =
        runBlocking {
            val payload =
                """
            {
              "reviews": [
                {
                  "uri": "at://did:plc:r1/net.olamaelcu.livtet.biblio.bookReview/3rk1",
                  "did": "did:plc:r1",
                  "record": {
                    "text": "Still the best.",
                    "book": "at://did:web:biblio.livtet.olamaelcu.net/community.lexicon.book.edition/ol.OL3425796M",
                    "rating": 9.5,
                    "createdAt": "2025-02-03T04:05:06.000Z"
                  }
                }
              ]
            }
            """
            val client =
                client { request ->
                    assertTrue(
                        request.url.encodedPath.endsWith(".getReviewsByBook"),
                        "unexpected call ${request.url}",
                    )
                    respond(payload, HttpStatusCode.OK, jsonHeaders)
                }

            val reviews = client.getBookReviews(BOOK_URI)

            assertEquals(1, reviews.size)
            assertEquals("did:plc:r1", reviews.single().did)
            assertEquals(9.5, reviews.single().record.rating)
            assertEquals(BOOK_URI, reviews.single().record.bookUri())
        }

    @Test
    fun `an empty AppView list falls back to constellation backlinks and PDS records`() =
        runBlocking {
            val backlinks =
                """
            { "records": [ { "did": "did:plc:r1", "collection": "net.olamaelcu.livtet.biblio.bookReview", "rkey": "3rk1" } ] }
            """
            val didDocument =
                """
            {
              "service": [
                { "id": "#atproto_pds", "type": "AtprotoPersonalDataServer", "serviceEndpoint": "https://pds.test" }
              ]
            }
            """
            val record =
                """
            {
              "uri": "at://did:plc:r1/net.olamaelcu.livtet.biblio.bookReview/3rk1",
              "cid": "bafyreview",
              "value": {
                "text": "Still the best.",
                "book": { "uri": "$BOOK_URI", "cid": "bafybook" },
                "createdAt": "2025-02-03T04:05:06.000Z"
              }
            }
            """
            val calls = mutableListOf<String>()
            val client =
                client { request ->
                    calls.add("${request.url.host}${request.url.encodedPath}")
                    when {
                        request.url.encodedPath.endsWith(".getReviewsByBook") ->
                            respond("""{"reviews":[]}""", HttpStatusCode.OK, jsonHeaders)
                        request.url.encodedPath.endsWith(".getBacklinks") ->
                            respond(backlinks, HttpStatusCode.OK, jsonHeaders)
                        request.url.host == "plc.directory" ->
                            respond(didDocument, HttpStatusCode.OK, jsonHeaders)
                        request.url.host == "pds.test" ->
                            respond(record, HttpStatusCode.OK, jsonHeaders)
                        else -> respond("unexpected", HttpStatusCode.NotFound)
                    }
                }

            val reviews = client.getBookReviews(BOOK_URI)

            assertEquals(1, reviews.size)
            assertEquals("Still the best.", reviews.single().record.text)
            assertEquals(BOOK_URI, reviews.single().record.bookUri())
            assertTrue(calls.any { it.endsWith(".getBacklinks") })
            assertTrue(calls.contains("pds.test/xrpc/com.atproto.repo.getRecord"))
        }

    @Test
    fun `a review record with a string book still yields its edition uri`() =
        runBlocking {
            val client =
                client { request ->
                    when {
                        request.url.host == "plc.directory" ->
                            respond(
                                """{"service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"pds.test"}]}""",
                                HttpStatusCode.OK,
                                jsonHeaders,
                            )
                        else ->
                            respond(
                                """{"uri":"at://did:plc:r1/net.olamaelcu.livtet.biblio.bookReview/3rk1","value":{"text":"Fine.","book":"$BOOK_URI","createdAt":"2025-02-03T04:05:06.000Z"}}""",
                                HttpStatusCode.OK,
                                jsonHeaders,
                            )
                    }
                }

            val review = client.getReview("did:plc:r1", "3rk1")

            assertEquals("Fine.", review?.record?.text)
            assertEquals(BOOK_URI, review?.record?.bookUri())
        }

    @Test
    fun `an unresolvable did yields null instead of throwing`() =
        runBlocking {
            val client = client { respond("nope", HttpStatusCode.NotFound) }
            assertNull(client.getReview("did:plc:missing", "3rk1"))
            assertTrue(client.listActorReviews("did:plc:missing").isEmpty())
            assertNull(client.getActor("nobody.invalid"))
        }

    @Test
    fun `listActorShelving follows cursors and stops at the last page`() =
        runBlocking {
            var page = 0
            val client =
                client { request ->
                    when {
                        request.url.host == "plc.directory" ->
                            respond(
                                """{"service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"https://pds.test"}]}""",
                                HttpStatusCode.OK,
                                jsonHeaders,
                            )
                        request.url.parameters["collection"] == BiblioClient.SHELF_COLLECTION ->
                            respond(
                                """{"records":[{"uri":"at://did:plc:r1/net.olamaelcu.livtet.biblio.shelf/read","value":{"name":"Finished","createdAt":"2025-01-01T00:00:00Z","updatedAt":"2025-01-01T00:00:00Z"}}]}""",
                                HttpStatusCode.OK,
                                jsonHeaders,
                            )
                        else -> {
                            page += 1
                            if (page == 1) {
                                respond(
                                    """{"records":[{"uri":"at://did:plc:r1/net.olamaelcu.livtet.biblio.bookShelving/a","value":{"shelf":"at://did:plc:r1/net.olamaelcu.livtet.biblio.shelf/read","book":{"ref":"$BOOK_URI","title":"Apex hides the hurt"},"metadata":{"status":"read"}}}],"cursor":"next"}""",
                                    HttpStatusCode.OK,
                                    jsonHeaders,
                                )
                            } else {
                                respond(
                                    """{"records":[{"uri":"at://did:plc:r1/net.olamaelcu.livtet.biblio.bookShelving/b","value":{"shelf":"at://did:plc:r1/net.olamaelcu.livtet.biblio.shelf/read","book":{"uri":"$BOOK_URI","cid":"bafy"},"metadata":{"status":"reading"}}}]}""",
                                    HttpStatusCode.OK,
                                    jsonHeaders,
                                )
                            }
                        }
                    }
                }

            val shelving = client.listActorShelving("did:plc:r1")
            val shelves = client.listActorShelves("did:plc:r1")

            assertEquals(2, shelving.size)
            // Reader-app records carry {"ref","title"}; livtet-service writes {"uri","cid"}.
            assertEquals(BOOK_URI, shelving.first().value.book.atUri)
            assertEquals("Apex hides the hurt", shelving.first().value.book.title)
            assertEquals(BOOK_URI, shelving.last().value.book.atUri)
            assertEquals("read", shelving.first().value.metadata?.status)
            assertEquals("reading", shelving.last().value.metadata?.status)
            assertEquals(mapOf("read" to "Finished"), shelves)
            assertEquals(ProfileStats(total = 2, reading = 1, finished = 1), shelvingStats(shelving))
        }

    @Test
    fun `did document urls follow the did method`() {
        val client = client { respond("{}", HttpStatusCode.OK, jsonHeaders) }
        assertEquals("https://plc.directory/did:plc:abc", client.didDocumentUrl("did:plc:abc"))
        assertEquals(
            "https://biblio.livtet.olamaelcu.net/.well-known/did.json",
            client.didDocumentUrl("did:web:biblio.livtet.olamaelcu.net"),
        )
        assertEquals(
            "https://example.com:8443/users/alice/did.json",
            client.didDocumentUrl("did:web:example.com%3A8443:users:alice"),
        )
        assertNull(client.didDocumentUrl("did:key:zabc"))
    }

    @Test
    fun `searchEditions asks the catalog query and decodes editions`() =
        runBlocking {
            val payload =
                """
            {
              "items": [
                {
                  "uri": "at://did:web:biblio.livtet.olamaelcu.net/community.lexicon.book.edition/ol.OL1M",
                  "title": "Cool Machine",
                  "coverImageUrl": "https://covers.example/cool.jpg",
                  "publishedYear": 2026,
                  "contributors": [
                    {
                      "subject": { "uri": "at://did:plc:c/community.lexicon.book.contributor/x" },
                      "role": "author",
                      "name": "Colson Whitehead"
                    }
                  ],
                  "identifiers": [
                    { "resource": "isbn13", "uri": "isbn:9780385698573" },
                    { "resource": "openlibrary", "uri": "https://openlibrary.org/books/OL1M" }
                  ]
                }
              ]
            }
            """
            val client =
                client { request ->
                    assertEquals("/xrpc/community.lexicon.book.searchEditions", request.url.encodedPath)
                    assertEquals("cool machine", request.url.parameters["q"])
                    assertEquals("25", request.url.parameters["limit"])
                    respond(payload, HttpStatusCode.OK, jsonHeaders)
                }

            val result = client.searchEditions("cool machine")

            assertEquals(1, result.items.size)
            val edition = result.items.first()
            assertEquals("Cool Machine", edition.title)
            assertEquals("Colson Whitehead", edition.authorLine())
            assertEquals(
                listOf("urn:isbn:9780385698573", "urn:openlibrary:https://openlibrary.org/books/OL1M"),
                edition.identifiers.orEmpty().mapNotNull { it.toUrn() },
            )
        }

    @Test
    fun `identifier resources become storage urns`() {
        assertEquals(
            "urn:isbn:9780385698573",
            BiblioIdentifier("isbn13", "isbn:9780385698573").toUrn(),
        )
        assertEquals("urn:isbn:0385698577", BiblioIdentifier("isbn10", "isbn:0385698577").toUrn())
        assertEquals(
            "urn:openlibrary:https://openlibrary.org/books/OL27277435M",
            BiblioIdentifier("openlibrary", "https://openlibrary.org/books/OL27277435M").toUrn(),
        )
        assertNull(BiblioIdentifier("isbn13", "  ").toUrn())
        assertNull(BiblioIdentifier("", "isbn:1").toUrn())
    }

    private companion object {
        const val BOOK_URI =
            "at://did:web:biblio.livtet.olamaelcu.net/community.lexicon.book.edition/ol.OL3425796M"
    }
}
