package app.opensefer.core.data

import app.opensefer.core.domain.DataError
import app.opensefer.core.domain.TextRepository
import app.opensefer.core.model.BookAbout
import app.opensefer.core.model.BookContents
import app.opensefer.core.model.ChapterText
import app.opensefer.core.network.SefariaApi
import app.opensefer.core.network.SefariaJson
import app.opensefer.core.network.dto.IndexDto
import app.opensefer.core.network.dto.TopicDto
import app.opensefer.core.network.dto.V3TextResponseDto
import app.opensefer.core.network.dto.VersionMetaDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/**
 * Offline‑first [TextRepository] — three tiers, fastest first (BLUEPRINT §8):
 *
 * 1. **Memory** — parsed chapters in an LRU ([SectionCache]); a book's contents/about memoized.
 * 2. **Disk** — the Sefaria JSON of every index and passage ever fetched ([DiskCache]), so a book
 *    opens instantly on the next launch and stays readable offline. Entries are re‑validated after a
 *    while (Sefaria does correct texts), falling back to the stale copy when offline. Error answers
 *    (Sefaria reports unknown refs as HTTP 200 + `{"error"}`) and empty texts are never stored.
 * 3. **Network** — Sefaria, with concurrent requests for the same resource de‑duplicated into one.
 *
 * Parsing (JSON → DTO → domain, including the inline‑HTML parser) runs on [Dispatchers.Default], so
 * the UI thread never pays for it. In‑flight fetches run in [scope], so a caller that goes away (a
 * passage scrolled off screen) doesn't cancel a download another caller is waiting on.
 */
class DefaultTextRepository(
    private val api: SefariaApi,
    private val memory: SectionCache = SectionCache(),
    private val disk: DiskCache? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : TextRepository {

    private val lock = Mutex() // guards every mutable map below + [memory]
    private val indexMemo = mutableMapOf<String, IndexDto>()
    private val contentsMemo = mutableMapOf<String, BookContents>()
    private val aboutMemo = mutableMapOf<String, BookAbout>()
    private val inFlight = mutableMapOf<String, Deferred<Any?>>()

    override suspend fun getContents(bookTitle: String): Result<BookContents> = dataResult {
        lock.withLock { contentsMemo[bookTitle] } ?: withContext(Dispatchers.Default) {
            indexOf(bookTitle).toBookContents()
        }.also { contents -> lock.withLock { contentsMemo[bookTitle] = contents } }
    }

    override suspend fun getText(tref: String): Result<ChapterText> = dataResult { chapter(tref) }

    override suspend fun prefetch(tref: String) {
        dataResult { chapter(tref) }
    }

    override suspend fun cacheForOffline(tref: String): Result<Unit> = dataResult {
        if (disk?.contains(TEXTS, tref) != true) textDto(tref)
        Unit
    }

    override suspend fun getAbout(bookTitle: String): Result<BookAbout> = dataResult {
        lock.withLock { aboutMemo[bookTitle] } ?: loadAbout(bookTitle).also { about ->
            lock.withLock { aboutMemo[bookTitle] = about }
        }
    }

    private suspend fun chapter(tref: String): ChapterText {
        lock.withLock { memory.get(tref) }?.let { return it }
        return withContext(Dispatchers.Default) {
            textDto(tref).toChapterText(tref)
        }.also { chapter -> lock.withLock { memory.put(tref, chapter) } }
    }

    private suspend fun textDto(tref: String): V3TextResponseDto = cached(
        namespace = TEXTS,
        key = tref,
        serializer = V3TextResponseDto.serializer(),
        maxAgeMillis = TEXT_MAX_AGE_MS,
        shouldStore = { it.versions.isNotEmpty() },
    ) {
        api.text(tref).also { if (it.error != null) throw DataError.NotFound() }
    }

    /** A book's `/api/index`, shared by contents + about so opening a book then its details costs one call. */
    private suspend fun indexOf(bookTitle: String): IndexDto {
        lock.withLock { indexMemo[bookTitle] }?.let { return it }
        return cached(INDEX, bookTitle, IndexDto.serializer(), INDEX_MAX_AGE_MS) {
            api.index(bookTitle).also { if (it.error != null || it.title.isNullOrBlank()) throw DataError.NotFound() }
        }.also { index -> lock.withLock { indexMemo[bookTitle] = index } }
    }

    private suspend fun loadAbout(bookTitle: String): BookAbout {
        // The index drives the screen; if it fails, the whole call fails.
        val index = indexOf(bookTitle)
        val base = index.toBookAbout()
        val authorSlug = index.authors.firstNotNullOfOrNull { it.slug?.takeUnless(String::isBlank) }
        // Author bio + editions are enrichments — fetched in parallel, each degrading to null/empty.
        return coroutineScope {
            val bio = async {
                authorSlug?.let { slug ->
                    dataResult {
                        cached(TOPICS, slug, TopicDto.serializer(), ABOUT_MAX_AGE_MS) { api.topic(slug) }
                    }.getOrNull()?.toAuthorBio()
                }
            }
            val editions = async {
                dataResult {
                    cached(VERSIONS, bookTitle, VersionsSerializer, ABOUT_MAX_AGE_MS) { api.versions(bookTitle) }
                }.getOrNull()?.toEditions().orEmpty()
            }
            base.copy(authorBio = bio.await(), editions = editions.await())
        }
    }

    /**
     * Cache‑first fetch of one resource: a fresh disk entry is served as‑is; otherwise the network is
     * asked (one request per resource at a time) and the answer stored. When the network fails, a
     * stale disk entry still beats an error — that's what makes saved books work offline.
     */
    private suspend fun <T> cached(
        namespace: String,
        key: String,
        serializer: KSerializer<T>,
        maxAgeMillis: Long,
        shouldStore: (T) -> Boolean = { true },
        fetch: suspend () -> T,
    ): T {
        disk?.read(namespace, key, maxAgeMillis)?.let { decode(serializer, it) }?.let { return it }
        return try {
            shared("$namespace/$key") {
                fetch().also { value ->
                    if (shouldStore(value)) disk?.write(namespace, key, SefariaJson.encodeToString(serializer, value))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (notFound: DataError.NotFound) {
            throw notFound // a real answer, not an outage — no stale fallback
        } catch (expected: Exception) {
            // Offline (or Sefaria is down): an old copy beats an error.
            disk?.read(namespace, key)?.let { decode(serializer, it) } ?: throw expected
        }
    }

    private fun <T> decode(serializer: KSerializer<T>, json: String): T? =
        runCatching { SefariaJson.decodeFromString(serializer, json) }.getOrNull()

    /** Joins an identical in‑flight request instead of starting a second one. */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> shared(key: String, block: suspend () -> T): T {
        val deferred = lock.withLock {
            inFlight.getOrPut(key) {
                scope.async {
                    try {
                        block()
                    } finally {
                        withContext(NonCancellable) { lock.withLock { inFlight.remove(key) } }
                    }
                }
            }
        }
        return deferred.await() as T
    }

    private companion object {
        const val TEXTS = "texts"
        const val INDEX = "index"
        const val TOPICS = "topics"
        const val VERSIONS = "versions"
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val TEXT_MAX_AGE_MS = 30 * DAY_MS
        const val INDEX_MAX_AGE_MS = 7 * DAY_MS
        const val ABOUT_MAX_AGE_MS = 30 * DAY_MS
        val VersionsSerializer = ListSerializer(VersionMetaDto.serializer())
    }
}
