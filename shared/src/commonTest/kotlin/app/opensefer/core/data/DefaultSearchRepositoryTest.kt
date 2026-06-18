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

class DefaultSearchRepositoryTest {

    /** A real-shaped `/api/name` response for a Hebrew query: the matched `title` is Hebrew, the
     *  canonical ref is in `key`, and `he` is null (Sefaria's name endpoint does not populate it). */
    private val hebrewNameFixture = """
        {
          "completions": ["הלכות תשובה", "שערי תשובה", "תשובה"],
          "completion_objects": [
            {"title": "הלכות תשובה", "key": "Mishneh Torah, Repentance", "type": "ref", "is_primary": true},
            {"title": "שערי תשובה", "key": "Sha'arei Teshuvah", "type": "ref"},
            {"title": "תשובה", "key": "teshuvah", "type": "Topic"},
            {"title": "סידור", "key": ["Liturgy", "Siddur"], "type": "TocCategory"}
          ],
          "is_book": false
        }
    """.trimIndent()

    private fun repositoryReturning(json: String): DefaultSearchRepository {
        val engine = MockEngine {
            respond(
                content = json,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return DefaultSearchRepository(SefariaApi(createHttpClient(engine)))
    }

    @Test
    fun hebrewResult_usesCanonicalKeyAsTitle_notTheHebrewDisplayName() = runTest {
        val results = repositoryReturning(hebrewNameFixture).search("תשובה").getOrThrow()

        // The Topic is filtered out; two `ref` books remain.
        assertEquals(2, results.size)

        val repentance = results.first()
        // title = the canonical ref (so index/text calls resolve to a chapter, not the whole book).
        assertEquals("Mishneh Torah, Repentance", repentance.title)
        // heTitle = the Hebrew display name the user searched for.
        assertEquals("הלכות תשובה", repentance.heTitle)
        assertEquals("ref", repentance.category)

        assertEquals("Sha'arei Teshuvah", results[1].title)
        assertEquals("שערי תשובה", results[1].heTitle)
    }

    @Test
    fun listShapedTocCategoryKey_doesNotCrashDeserialization_andIsFilteredOut() = runTest {
        // A TocCategory hit's `key` is a JSON array (["Liturgy","Siddur"]). Before the fix this threw
        // a deserialization error for the whole response (e.g. searching "סידור"). It must now parse,
        // and the TocCategory (like Topic) must be excluded — leaving only the `ref` books.
        val results = repositoryReturning(hebrewNameFixture).search("סידור").getOrThrow()

        assertEquals(2, results.size)
        assertEquals(listOf("Mishneh Torah, Repentance", "Sha'arei Teshuvah"), results.map { it.title })
    }

    @Test
    fun blankQuery_returnsEmptyWithoutHittingTheNetwork() = runTest {
        val engine = MockEngine { error("network should not be called for a blank query") }
        val repository = DefaultSearchRepository(SefariaApi(createHttpClient(engine)))

        assertEquals(emptyList(), repository.search("   ").getOrThrow())
    }
}
