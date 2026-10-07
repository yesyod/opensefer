package app.opensefer.core.data

import app.opensefer.core.network.SefariaApi
import app.opensefer.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultTextRepositoryTest {

    private val tref = "Mishneh_Torah,_Foundations_of_the_Torah.1"

    private val textFixture = """
        {
          "ref": "Mishneh Torah, Foundations of the Torah 1",
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

    // depth-2 schema; first dimension (10) is the chapter count.
    private val indexFixture = """
        {
          "title": "Mishneh Torah, Foundations of the Torah",
          "heTitle": "משנה תורה, הלכות יסודי התורה",
          "categories": ["Halakhah", "Mishneh Torah"],
          "schema": {
            "depth": 2,
            "sectionNames": ["Chapter", "Halacha"],
            "heSectionNames": ["פרק", "הלכה"],
            "lengths": [10, 88]
          }
        }
    """.trimIndent()

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun getText_returnsSuccessWithParsedSegments() = runTest {
        val engine = MockEngine { respond(textFixture, HttpStatusCode.OK, jsonHeaders()) }
        val repo = DefaultTextRepository(SefariaApi(createHttpClient(engine)))

        val result = repo.getText(tref)

        assertTrue(result.isSuccess, "result was: $result")
        val chapter = result.getOrThrow()
        assertEquals(2, chapter.segments.size)
        assertEquals(tref, chapter.tref)

        // Sanity: parsed content survives the full DTO -> domain pipeline.
        val firstHe = chapter.segments[0].hebrew
        assertNotNull(firstHe)
        assertEquals("יְסוֹד הַיְסוֹדוֹת", firstHe.plainText)
    }

    @Test
    fun getText_secondCallForSameTref_isServedFromCache() = runTest {
        var textCallCount = 0
        val engine = MockEngine {
            textCallCount++
            respond(textFixture, HttpStatusCode.OK, jsonHeaders())
        }
        val repo = DefaultTextRepository(SefariaApi(createHttpClient(engine)))

        val first = repo.getText(tref)
        val second = repo.getText(tref)

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        // The text endpoint must have been hit exactly once; the second read is cache-served.
        assertEquals(1, textCallCount)
        assertEquals(first.getOrThrow().segments.size, second.getOrThrow().segments.size)
    }

    @Test
    fun getContents_isMemoizedAndYieldsNumberedChapters() = runTest {
        var indexCallCount = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/index")) {
                indexCallCount++
                respond(indexFixture, HttpStatusCode.OK, jsonHeaders())
            } else {
                respond("{}", HttpStatusCode.NotFound, jsonHeaders())
            }
        }
        val repo = DefaultTextRepository(SefariaApi(createHttpClient(engine)))

        val a = repo.getContents("Mishneh Torah, Foundations of the Torah")
        val b = repo.getContents("Mishneh Torah, Foundations of the Torah")

        assertTrue(a.isSuccess)
        assertTrue(b.isSuccess)
        val contents = a.getOrThrow()
        assertEquals(10, contents.leaves.size) // lengths[0] = 10 chapters
        assertEquals(tref, contents.leaves.first().tref)
        assertEquals(1, indexCallCount) // memoized
    }

    @Test
    fun getAbout_aggregatesIndexAuthorBioAndEditions() = runTest {
        val aboutIndex = """
            {
              "title": "Mishneh Torah, Foundations of the Torah",
              "heTitle": "משנה תורה, הלכות יסודי התורה",
              "heCategories": ["הלכה", "משנה תורה"],
              "authors": [{"en": "Moses ben Maimon (Rambam)", "he": "רמב\"ם", "slug": "rambam"}],
              "enDesc": "A monumental legal code.",
              "era": "RI",
              "compDateString": {"he": " (1176 – 1178 לספירה בקירוב)"},
              "schema": {"depth": 2, "sectionNames": ["Chapter", "Halacha"], "lengths": [10, 88]}
            }
        """.trimIndent()
        val topicFixture =
            """{"slug":"rambam","properties":{"birthYear":{"value":1137},"deathYear":{"value":1204},"birthPlace":{"value":"Cordoba, Spain"}}}"""
        val versionsFixture =
            """[{"versionTitle":"Torat Emet 363","language":"he","license":"Public Domain","isPrimary":true}]"""
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.contains("/v2/topics") -> respond(topicFixture, HttpStatusCode.OK, jsonHeaders())
                path.contains("/texts/versions") -> respond(versionsFixture, HttpStatusCode.OK, jsonHeaders())
                path.contains("/index") -> respond(aboutIndex, HttpStatusCode.OK, jsonHeaders())
                else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders())
            }
        }
        val repo = DefaultTextRepository(SefariaApi(createHttpClient(engine)))

        val about = repo.getAbout("Mishneh Torah, Foundations of the Torah").getOrThrow()

        assertEquals("רמב\"ם", about.authorHe)
        assertEquals("ראשונים", about.era) // era code mapped to a Hebrew label
        assertEquals("A monumental legal code.", about.description)
        assertNotNull(about.authorBio)
        assertEquals("1137", about.authorBio?.birthYear)
        assertEquals("Cordoba, Spain", about.authorBio?.birthPlace)
        assertEquals(1, about.editions.size)
        assertEquals("Torat Emet 363", about.editions.first().title)
    }

    @Test
    fun getAbout_whenAuthorTopicFails_stillSucceedsWithoutBio() = runTest {
        val aboutIndex = """
            {
              "title": "Mishneh Torah, Foundations of the Torah",
              "heTitle": "משנה תורה, הלכות יסודי התורה",
              "authors": [{"he": "רמב\"ם", "slug": "rambam"}],
              "era": "RI",
              "schema": {"depth": 2, "sectionNames": ["Chapter"], "lengths": [10]}
            }
        """.trimIndent()
        val versionsFixture =
            """[{"versionTitle":"Torat Emet 363","language":"he","license":"Public Domain","isPrimary":true}]"""
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.contains("/v2/topics") -> respond("", HttpStatusCode.NotFound, jsonHeaders())
                path.contains("/texts/versions") -> respond(versionsFixture, HttpStatusCode.OK, jsonHeaders())
                path.contains("/index") -> respond(aboutIndex, HttpStatusCode.OK, jsonHeaders())
                else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders())
            }
        }
        val repo = DefaultTextRepository(SefariaApi(createHttpClient(engine)))

        val result = repo.getAbout("Mishneh Torah, Foundations of the Torah")

        assertTrue(result.isSuccess, "result was: $result")
        val about = result.getOrThrow()
        assertNull(about.authorBio) // the failed author-topic call degrades to null, not an error
        assertEquals("ראשונים", about.era) // index-derived data is intact
        assertEquals(1, about.editions.size) // editions still present
    }

    @Test
    fun getAbout_openedOffline_isNotRemembered_soTheBioAppearsOnceBackOnline() = runTest {
        val aboutIndex = """
            {"title": "Mishneh Torah, Foundations of the Torah", "heTitle": "משנה תורה, הלכות יסודי התורה",
             "authors": [{"he": "רמב\"ם", "slug": "rambam"}],
             "schema": {"depth": 2, "sectionNames": ["Chapter"], "lengths": [10]}}
        """.trimIndent()
        var online = false
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.contains("/index") -> respond(aboutIndex, HttpStatusCode.OK, jsonHeaders())
                !online -> throw kotlinx.io.IOException("no network")
                path.contains("/v2/topics") -> respond("""{"slug":"rambam"}""", HttpStatusCode.OK, jsonHeaders())
                else -> respond("[]", HttpStatusCode.OK, jsonHeaders())
            }
        }
        val repo = DefaultTextRepository(SefariaApi(createHttpClient(engine)))

        assertNull(repo.getAbout("Mishneh Torah, Foundations of the Torah").getOrThrow().authorBio)
        online = true
        assertNotNull(repo.getAbout("Mishneh Torah, Foundations of the Torah").getOrThrow().authorBio)
    }
}
