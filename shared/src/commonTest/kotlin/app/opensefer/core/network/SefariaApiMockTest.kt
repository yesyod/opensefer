package app.opensefer.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SefariaApiMockTest {

    private val textFixture = """
        {
          "ref": "Mishneh Torah, Foundations of the Torah 1",
          "heRef": "משנה תורה, הלכות יסודי התורה א",
          "title": "Mishneh Torah, Foundations of the Torah",
          "heTitle": "משנה תורה, הלכות יסודי התורה",
          "versions": [
            {
              "language": "he",
              "versionTitle": "Torat Emet 363",
              "license": "Public Domain",
              "text": ["<b>יְסוֹד</b> הַיְסוֹדוֹת", "וִידִיעַת"]
            },
            {
              "language": "en",
              "versionTitle": "Mishneh Torah, trans. by Eliyahu Touger",
              "text": ["The <b>foundation</b>", "And to know"]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun text_parsesVersionsAndSegments() = runTest {
        var requestedPath: String? = null
        val engine = MockEngine { request ->
            requestedPath = request.url.encodedPath
            respond(
                content = textFixture,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = SefariaApi(createHttpClient(engine))

        val dto = api.text("Mishneh_Torah,_Foundations_of_the_Torah.1")

        // Two versions (he + en), each with two segments.
        assertEquals(2, dto.versions.size)
        val he = dto.versions.first { it.language == "he" }
        val en = dto.versions.first { it.language == "en" }
        assertEquals("Torat Emet 363", he.versionTitle)
        assertEquals(2, he.text.flattenStringsCount())
        assertEquals(2, en.text.flattenStringsCount())

        // The request hit the v3/texts endpoint.
        assertTrue(requestedPath!!.contains("v3/texts"), "path was: $requestedPath")
    }

    @Test
    fun text_sendsBothVersionAndReturnFormatParameters() = runTest {
        var query: String? = null
        val engine = MockEngine { request ->
            query = request.url.encodedQuery
            respond(
                content = textFixture,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = SefariaApi(createHttpClient(engine))

        api.text("Genesis.1")

        val q = query!!
        // "source" (not "hebrew"): Aramaic originals — the Talmud, Targumim — are tagged Aramaic.
        assertTrue(q.contains("version=source"), "query was: $q")
        assertTrue(q.contains("version=english"), "query was: $q")
        assertTrue(q.contains("return_format=default"), "query was: $q")
    }
}

/** Local helper so this network test stays self-contained against the jagged `text` element. */
private fun kotlinx.serialization.json.JsonElement?.flattenStringsCount(): Int = when (this) {
    null -> 0
    is kotlinx.serialization.json.JsonNull -> 0
    is kotlinx.serialization.json.JsonPrimitive -> if (content.isBlank()) 0 else 1
    is kotlinx.serialization.json.JsonArray -> sumOf { it.flattenStringsCount() }
    is kotlinx.serialization.json.JsonObject -> 0
}
