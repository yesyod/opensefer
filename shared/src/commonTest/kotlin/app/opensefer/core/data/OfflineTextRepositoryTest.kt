package app.opensefer.core.data

import app.opensefer.core.domain.DataError
import app.opensefer.core.network.SefariaApi
import app.opensefer.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The offline‑first behaviour of [DefaultTextRepository]: disk tier, stale fallback, validation, dedupe. */
class OfflineTextRepositoryTest {

    private val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "offline-repo-${Random.nextLong().toULong()}"
    private val tref = "Mishneh_Torah,_Repentance.1"

    @AfterTest
    fun cleanup() {
        FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
    }

    private val textFixture = """
        {
          "ref": "Mishneh Torah, Repentance 1",
          "heRef": "משנה תורה, הלכות תשובה א׳",
          "versions": [
            {"language": "he", "versionTitle": "Torat Emet", "text": ["כל מצות שבתורה", "וידוי זה"]},
            {"language": "en", "versionTitle": "Touger", "text": ["All the mitzvot", "This confession"]}
          ]
        }
    """.trimIndent()

    private val indexFixture = """
        {
          "title": "Mishneh Torah, Repentance",
          "heTitle": "משנה תורה, הלכות תשובה",
          "categories": ["Halakhah", "Mishneh Torah"],
          "heCategories": ["הלכה", "משנה תורה"],
          "authors": [{"en": "Rambam", "he": "רמב״ם", "slug": "rambam"}],
          "schema": {"depth": 2, "sectionNames": ["Chapter", "Halacha"], "heSectionNames": ["פרק", "הלכה"], "lengths": [10, 88]}
        }
    """.trimIndent()

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun repo(
        disk: DiskCache = DiskCache(root),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = DefaultTextRepository(SefariaApi(createHttpClient(MockEngine(handler))), disk = disk)

    @Test
    fun aFetchedPassage_isServedFromDisk_afterARestart_withoutTheNetwork() = runTest {
        repo { json(textFixture) }.getText(tref).getOrThrow()

        // A fresh repository (empty memory) whose network is down: the disk copy answers.
        val offline = repo { throw IOException("no network") }
        val chapter = offline.getText(tref).getOrThrow()

        assertEquals(2, chapter.segments.size)
        assertEquals("משנה תורה, הלכות תשובה א׳", chapter.heRef)
    }

    @Test
    fun anExpiredEntry_isRefetched_butStillServedWhenOffline() = runTest {
        var now = 0L
        val disk = DiskCache(root, clock = { now })
        repo(disk) { json(indexFixture) }.getContents("Mishneh Torah, Repentance").getOrThrow()
        now = Long.MAX_VALUE / 2 // far past every max age

        var calls = 0
        val online = repo(disk) { calls++; json(indexFixture) }
        online.getContents("Mishneh Torah, Repentance").getOrThrow()
        assertEquals(1, calls) // stale → re‑validated against the network

        val offline = repo(disk) { throw IOException("no network") }
        assertEquals(10, offline.getContents("Mishneh Torah, Repentance").getOrThrow().leaves.size)
    }

    @Test
    fun noNetworkAndNothingSaved_isAnOfflineError() = runTest {
        val result = repo { throw IOException("no network") }.getText(tref)

        assertIs<DataError.Offline>(result.exceptionOrNull())
    }

    @Test
    fun http404_isNotFound() = runTest {
        val result = repo { json("{}", HttpStatusCode.NotFound) }.getContents("Nope")

        assertIs<DataError.NotFound>(result.exceptionOrNull())
    }

    @Test
    fun anErrorAnsweredAsHttp200_isNotFound_andIsNeverCached() = runTest {
        var calls = 0
        val repository = repo { calls++; json("""{"error": "Unknown book: Nope"}""") }

        assertIs<DataError.NotFound>(repository.getContents("Nope").exceptionOrNull())
        assertIs<DataError.NotFound>(repository.getContents("Nope").exceptionOrNull())
        assertEquals(2, calls) // not memoized, not written to disk
        assertTrue(!DiskCache(root).contains("index", "Nope"))
    }

    @Test
    fun concurrentRequestsForOnePassage_shareOneNetworkCall() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val repository = repo {
            calls++
            gate.await()
            json(textFixture)
        }

        val results = List(3) { async { repository.getText(tref) } }
        gate.complete(Unit)

        assertTrue(results.awaitAll().all { it.isSuccess })
        assertEquals(1, calls)
    }

    @Test
    fun cacheForOffline_storesThePassage_soItLaterOpensOffline() = runTest {
        var calls = 0
        val downloading = repo { calls++; json(textFixture) }
        downloading.cacheForOffline(tref).getOrThrow()
        downloading.cacheForOffline(tref).getOrThrow() // already on disk → no second request
        assertEquals(1, calls)

        val offline = repo { throw IOException("no network") }
        assertEquals(2, offline.getText(tref).getOrThrow().segments.size)
    }

    @Test
    fun aSectionWithNoText_countsAsDownloaded_andStaysEmptyOffline() = runTest {
        var calls = 0
        val downloading = repo { calls++; json("""{"error": "We have no text for Middot 2a."}""", HttpStatusCode.NotFound) }
        downloading.cacheForOffline("Middot.2a").getOrThrow() // nothing to keep — and that's a success
        downloading.cacheForOffline("Middot.2a").getOrThrow()
        assertEquals(1, calls) // remembered: not asked again

        val offline = repo { throw IOException("no network") }
        assertIs<DataError.NotFound>(offline.getText("Middot.2a").exceptionOrNull()) // "empty", not "offline"
    }

    @Test
    fun cacheForOffline_failsWhenTheDeviceCantStoreIt() = runTest {
        FileSystem.SYSTEM.write(root) { writeUtf8("not a folder") } // storage unavailable
        val result = repo { json(textFixture) }.cacheForOffline(tref)

        assertIs<DataError.Storage>(result.exceptionOrNull()) // never "done" with nothing saved
    }

    @Test
    fun freeingSpace_keepsTheSavedBooks_andDropsWhatWasMerelyRead() = runTest {
        val online = repo { request ->
            if (request.url.encodedPath.contains("/index")) json(indexFixture) else json(textFixture)
        }
        online.getContents("Mishneh Torah, Repentance").getOrThrow()
        online.getText(tref).getOrThrow() // a passage of the saved book
        online.getText("Genesis.1").getOrThrow() // a book that was only browsed

        online.clearExcept(setOf("Mishneh Torah, Repentance"))

        val offline = repo { throw IOException("no network") }
        assertTrue(offline.getText(tref).isSuccess)
        assertTrue(offline.getContents("Mishneh Torah, Repentance").isSuccess)
        assertIs<DataError.Offline>(offline.getText("Genesis.1").exceptionOrNull())
        assertTrue(offline.isStored("Mishneh Torah, Repentance"))
        assertTrue(!offline.isStored("Genesis"))
    }

    @Test
    fun contents_carryShelfDetails_forTheCover() = runTest {
        val details = repo { json(indexFixture) }.getContents("Mishneh Torah, Repentance").getOrThrow().details

        assertEquals("Halakhah", details.category)
        assertEquals("הלכה", details.heCategory)
        assertEquals("רמב״ם", details.heAuthor)
        assertEquals("הלכה", details.heSegmentName)
    }
}
