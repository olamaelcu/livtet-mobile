package net.olamaelcu.livtet.core.auth.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import net.olamaelcu.livtet.core.auth.storage.AndroidOAuthSessionStore
import net.olamaelcu.livtet.core.auth.storage.SecureTokenStore
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    @Named("atproto")
    fun provideAtprotoHttpClient(): HttpClient =
        HttpClient(OkHttp)

    @Provides
    @Singleton
    @Named("app")
    fun provideAppHttpClient(): HttpClient =
        HttpClient(OkHttp) {
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        timber.log.Timber.tag("HttpClient").d(message)
                    }
                }
                level = LogLevel.ALL
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }
                )
            }
        }

    @Provides
    @Singleton
    fun provideOAuthSessionStore(@ApplicationContext context: Context): AndroidOAuthSessionStore =
        AndroidOAuthSessionStore(context)

    @Provides
    @Singleton
    fun provideSecureTokenStore(@ApplicationContext context: Context): SecureTokenStore =
        SecureTokenStore(context)
}
