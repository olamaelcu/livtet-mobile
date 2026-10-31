package net.olamaelcu.livtet.biblio

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** Marks the read-only client the public permalink screens use. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class PublicBiblioHttp

/**
 * HTTP client for the public catalog reads.
 *
 * Deliberately its own client rather than the shared `@Named("app")` one: the permalink screens are
 * read-only, they must not carry any auth state, and they need a hard timeout. The CIO engine is
 * used because it runs on the JVM platforms the app supports without an extra native transport
 * dependency.
 */
@Module
@InstallIn(SingletonComponent::class)
object PublicBiblioModule {
    @Provides
    @Singleton
    @PublicBiblioHttp
    fun providePublicBiblioHttp(): HttpClient =
        HttpClient(CIO) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }
                )
            }
            install(HttpTimeout) {
                requestTimeoutMillis = BiblioClient.TIMEOUT_MS
                connectTimeoutMillis = BiblioClient.TIMEOUT_MS
                socketTimeoutMillis = BiblioClient.TIMEOUT_MS
            }
        }
}
