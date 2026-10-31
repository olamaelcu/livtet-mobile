package net.olamaelcu.livtet.sync

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
import io.ktor.utils.io.toByteArray
import io.ktor.http.content.OutgoingContent
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class PairingClientTest {
    private val nodeId = "ab".repeat(32)
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val sseHeaders = headersOf(HttpHeaders.ContentType, "text/event-stream")

    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): PairingClient {
        val engine = MockEngine { request -> handler(request) }
        val http =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }
        return PairingClient(http)
    }

    private suspend fun OutgoingContent.asText(): String =
        when (this) {
            is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
            is OutgoingContent.ReadChannelContent -> readFrom().toByteArray().decodeToString()
            else -> ""
        }

    @Test
    fun `identity decodes the node and device ids`() = runBlocking {
        val client =
            client {
                respond(
                    """{"node_id":"$nodeId","device_id":"01ARZ3NDEKTSV4RRFFQ69G5FAV"}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
            }

        val identity = client.identity("10.0.0.5", 3120)

        assertEquals(nodeId, identity.nodeId)
        assertEquals("01ARZ3NDEKTSV4RRFFQ69G5FAV", identity.deviceId)
    }

    @Test
    fun `pair posts the node id and returns the claim`() = runBlocking {
        var path: String? = null
        var body: String? = null
        val client =
            client { request ->
                path = request.url.encodedPath
                body = request.body.asText()
                respond(
                    """{"status":"pending","claim":"claim-1"}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
            }

        val claim =
            client.pair(
                nodeId = nodeId,
                deviceId = "01ARZ3NDEKTSV4RRFFQ69G5FAV",
                name = "Phone",
                deviceType = "android",
                host = "10.0.0.5",
                port = 3120,
            )

        assertEquals("claim-1", claim.claim)
        assertEquals("/sync/pair", path)
        val posted = Json.parseToJsonElement(body!!).jsonObject
        assertEquals(nodeId, posted["node_id"]?.jsonPrimitive?.content)
        assertEquals("android", posted["device_type"]?.jsonPrimitive?.content)
        assertNull(posted["token"], "the token flow is gone")
    }

    @Test
    fun `pair without a claim is an error`() = runBlocking {
        val client =
            client {
                respond("""{"status":"pending"}""", HttpStatusCode.OK, jsonHeaders)
            }

        assertFailsWith<PairingException> {
            client.pair(
                nodeId = nodeId,
                deviceId = "device",
                name = "Phone",
                deviceType = "android",
                host = "10.0.0.5",
                port = 3120,
            )
        }
    }

    @Test
    fun `decisions reads the claim-gated stream`() = runBlocking {
        var claim: String? = null
        val client =
            client { request ->
                claim = request.url.parameters["claim"]
                respond(
                    "data: {\"event\":\"approved\",\"session_token\":\"session-1\"}\n\n",
                    HttpStatusCode.OK,
                    sseHeaders,
                )
            }

        val decisions =
            client.decisions(nodeId, "claim 1", "10.0.0.5", 3120).toList()

        assertEquals(1, decisions.size)
        assertEquals("approved", decisions.first().event)
        assertEquals("session-1", decisions.first().sessionToken)
        assertEquals("claim 1", claim)
    }
}
