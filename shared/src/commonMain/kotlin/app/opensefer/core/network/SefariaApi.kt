package app.opensefer.core.network

import app.opensefer.core.network.dto.IndexDto
import app.opensefer.core.network.dto.NameResponseDto
import app.opensefer.core.network.dto.TopicDto
import app.opensefer.core.network.dto.V3TextResponseDto
import app.opensefer.core.network.dto.VersionMetaDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.appendPathSegments

/**
 * Thin typed wrapper over the public Sefaria REST API (no key, no auth).
 * Base: https://www.sefaria.org/api — see BLUEPRINT §7 for the endpoint map.
 */
class SefariaApi(private val client: HttpClient) {

    /** Title autocomplete — used when adding a book. */
    suspend fun name(query: String, limit: Int = 12): NameResponseDto =
        client.get("$BASE/name") {
            url { appendPathSegments(query) }
            parameter("limit", limit)
        }.body()

    /** Structural index/TOC for a book (depth, sectionNames, lengths…). */
    suspend fun index(title: String): IndexDto =
        client.get("$BASE/index") {
            url { appendPathSegments(title) }
        }.body()

    /**
     * Section text (v3). Requests both Hebrew and English versions in one call; the response's
     * `versions[]` carries whichever exist. `return_format=default` keeps Sefaria's inline HTML.
     */
    suspend fun text(tref: String): V3TextResponseDto =
        client.get("$BASE/v3/texts") {
            url { appendPathSegments(tref) }
            parameter("version", "hebrew")
            parameter("version", "english")
            parameter("return_format", "default")
        }.body()

    /** Author biography page (birth/death, bio, links) for an index author's `slug`. */
    suspend fun topic(slug: String): TopicDto =
        client.get("$BASE/v2/topics") {
            url { appendPathSegments(slug) }
        }.body()

    /**
     * Available editions for a book + their licenses. (The older `/api/versions/{title}` path
     * referenced in the docs now 404s; `/api/texts/versions/{title}` is the working endpoint.)
     */
    suspend fun versions(title: String): List<VersionMetaDto> =
        client.get("$BASE/texts/versions") {
            url { appendPathSegments(title) }
        }.body()

    companion object {
        const val BASE = "https://www.sefaria.org/api"
    }
}
