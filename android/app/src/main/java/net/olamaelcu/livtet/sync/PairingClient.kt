package net.olamaelcu.livtet.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The identity a desktop advertises over HTTP. */
@Serializable
data class SyncIdentity(
    @SerialName("node_id") val nodeId: String,
    @SerialName("device_id") val deviceId: String,
)

/** The claim the desktop files, and the secret the status stream requires. */
@Serializable
data class PairClaim(
    val status: String,
    val claim: String? = null,
)

/** A decision on a filed claim. */
@Serializable
data class PairingDecision(
    val event: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("session_token") val sessionToken: String? = null,
)

@Serializable
private data class PairRequest(
    @SerialName("device_id") val deviceId: String,
    val name: String,
    @SerialName("device_type") val deviceType: String,
    @SerialName("node_id") val nodeId: String,
)

class PairingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The pairing half of the sync protocol: confirm a discovered peer, file a claim, wait for the
 * desktop's decision, and hand back the session token.
 *
 * Data routes (`/sync/changes`, `/sync/push`, …) require that token in `x-livtet-session`; this
 * client does not call them yet.
 */
class PairingClient(
    private val http: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    companion object {
        /** Header the sync server accepts for session-gated data routes. */
        const val SESSION_HEADER = "x-livtet-session"
    }

    /** Confirm that `host:port` holds the node id the caller expects. */
    suspend fun identity(host: String, port: Int): SyncIdentity =
        http.get(url(host, port, "/sync/identity")).body()

    /**
     * File a claim for this device. The desktop approves it by hand; the returned claim is what
     * opens [decisions].
     */
    suspend fun pair(
        nodeId: String,
        deviceId: String,
        name: String,
        deviceType: String,
        host: String,
        port: Int,
    ): PairClaim {
        val claim: PairClaim =
            http
                .post(url(host, port, "/sync/pair")) {
                    contentType(ContentType.Application.Json)
                    setBody(PairRequest(deviceId, name, deviceType, nodeId))
                }
                .body()

        if (claim.claim.isNullOrEmpty()) {
            throw PairingException("the desktop did not return a pairing claim")
        }
        return claim
    }

    /**
     * The decision stream for a claim filed with [pair].
     *
     * The claim is a query parameter: the server opens the stream only for the device that asked.
     */
    fun decisions(nodeId: String, claim: String, host: String, port: Int): Flow<PairingDecision> =
        flow {
            http
                .prepareGet(url(host, port, "/sync/pair/status/$nodeId")) {
                    parameter("claim", claim)
                }
                .execute { response ->
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (!line.startsWith("data:")) continue

                        val payload = line.removePrefix("data:").trim()
                        if (payload.isEmpty()) continue

                        val decision = json.decodeFromString<PairingDecision>(payload)
                        emit(decision)

                        if (decision.event == "approved" || decision.event == "rejected") {
                            return@execute
                        }
                    }
                }
        }

    private fun url(host: String, port: Int, path: String): String =
        URLBuilder(protocol = URLProtocol.HTTP, host = host, port = port).buildString().trimEnd('/') +
            path
}
