package app.opensefer.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Platform engine (OkHttp on Android, Darwin on iOS). */
internal expect fun httpClientEngine(): HttpClientEngine

/** Shared JSON config — tolerant of Sefaria's large, evolving payloads. */
internal val SefariaJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

/** Builds the configured [HttpClient]. A custom [engine] can be injected in tests. */
fun createHttpClient(engine: HttpClientEngine = httpClientEngine()): HttpClient =
    HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) { json(SefariaJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 15_000
        }
        install(Logging) { level = LogLevel.NONE }
    }
