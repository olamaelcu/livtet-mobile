package net.olamaelcu.livtet.core.auth.provider

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.kikin81.atproto.oauth.AtOAuth
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.olamaelcu.livtet.core.auth.model.AtprotoProfile
import net.olamaelcu.livtet.core.auth.storage.AndroidOAuthSessionStore
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class AtprotoAuthService
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val sessionStore: AndroidOAuthSessionStore,
    @Named("atproto") private val httpClient: HttpClient,
) {
    private val oauth by lazy {
        AtOAuth(
            clientMetadataUrl = CLIENT_METADATA_URL,
            redirectUri = REDIRECT_URI,
            sessionStore = sessionStore,
            httpClient = httpClient,
        )
    }

    private var cachedClient: XrpcClient? = null

    suspend fun beginLogin(handle: String): String {
        Timber.tag(TAG).d("beginLogin: handle=$handle")
        return oauth.beginLogin(handle)
    }

    suspend fun completeLogin(redirectUri: String) {
        Timber.tag(TAG).d("completeLogin: redirectUri=$redirectUri")
        oauth.completeLogin(redirectUri)
        cachedClient = null
    }

    suspend fun createClient(): XrpcClient {
        cachedClient?.let { return it }
        return oauth.createClient().also { cachedClient = it }
    }

    suspend fun signOut() {
        Timber.tag(TAG).d("signOut")
        sessionStore.clear()
        cachedClient = null
    }

    suspend fun hasSession(): Boolean = sessionStore.load() != null

    suspend fun getDid(): String? = sessionStore.load()?.did

    suspend fun getHandle(): String? = sessionStore.load()?.handle

    suspend fun fetchProfile(did: String): AtprotoProfile {
        return try {
            val session = sessionStore.load()
                ?: return AtprotoProfile(did, "", "", "", null)
            val url = "$APP_VIEW_URL/xrpc/net.olamaelcu.livtet.biblio.getActorProfile"
            val response = httpClient.get(url) {
                parameter("did", did)
                header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            }
            val body = response.bodyAsText()
            val json = Json { ignoreUnknownKeys = true }
            val element = json.parseToJsonElement(body)
            val handle = element.jsonObject["handle"]?.jsonPrimitive?.content ?: ""
            val profile = element.jsonObject["profile"]?.jsonObject
            val displayName = profile?.get("displayName")?.jsonPrimitive?.content ?: ""
            val description = profile?.get("description")?.jsonPrimitive?.content ?: ""
            val avatarCid = profile?.get("avatarCid")?.jsonPrimitive?.content
            AtprotoProfile(did, handle, displayName, description, avatarCid)
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch ATProto profile")
            AtprotoProfile(did, "", "", "", null)
        }
    }

    companion object {
        private const val TAG = "AtprotoAuth"
        const val CLIENT_METADATA_URL =
            "https://livtet.olamaelcu.net/.well-known/oauth-client-metadata.json"
        const val REDIRECT_URI = "net.olamaelcu.livtet:/oauth-redirect"
        const val APP_VIEW_URL = "https://livtet.olamaelcu.net"
    }
}
