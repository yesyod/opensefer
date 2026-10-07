package app.opensefer.core.data

import app.opensefer.core.domain.DataError
import app.opensefer.core.domain.OfflineStorage
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
 *
 * It is also the [OfflineStorage] behind About's "free up space": it knows which stored files belong
 * to which book, so the saved books can stay while everything else goes.
 */
class DefaultTextRepository(
    private val api: SefariaApi,
    private val memory: SectionCache = SectionCache(),
    private val disk: DiskCache? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : TextRepository, OfflineStorage {

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
        val store = disk ?: return@dataResult
        if (store.contains(TEXTS, tref) || store.contains(EMPTY, tref)) return@dataResult
        val text = try {
            textDto(tref)
        } catch (ignored: DataError.NotFound) {
            return@dataResult // Sefaria has no text here (an empty daf, an uncommented chapter): nothing to keep
        }
        // An empty answer isn't stored, and there's nothing to keep; otherwise it must now be on disk.
        if (text.versions.isNotEmpty() && !store.contains(TEXTS, tref)) throw DataError.Storage()
    }

    override suspend fun getAbout(bookTitle: String): Result<BookAbout> = dataResult {
        lock.withLock { aboutMemo[bookTitle] } ?: loadAbout(bookTitle).let { (about, complete) ->
            // Remember it only when nothing was missing — an offline visit mustn't hide the bio for good.
            if (complete) lock.withLock { aboutMemo[bookTitle] = about }
            about
        }
    }

    override suspend fun isStored(bookTitle: String): Boolean = disk?.contains(INDEX, bookTitle) == true

    override suspend fun sizeBytes(): Long = disk?.sizeBytes() ?: 0L

    override suspend fun clearExcept(keepBooks: Set<String>) {
        val store = disk ?: return
        val kept = HashMap<String, MutableSet<String>>()
        fun keep(namespace: String, key: String) {
            kept.getOrPut(namespace) { HashSet() } += key
        }
        for (title in keepBooks) {
            val index = storedIndex(title) ?: continue // never stored → nothing of it on disk
            keep(INDEX, title)
            keep(VERSIONS, title)
            index.authors.mapNotNull { it.slug?.takeUnless(String::isBlank) }.forEach { keep(TOPICS, it) }
            val leaves = withContext(Dispatchers.Default) { runCatching { index.toBookContents().leaves } }
            leaves.getOrDefault(emptyList()).forEach {
                keep(TEXTS, it.tref)
                keep(EMPTY, it.tref)
            }
        }
        store.retainOnly(kept)
    }

    /** A book's index as already known on the device — never from the network. */
    private suspend fun storedIndex(bookTitle: String): IndexDto? =
        lock.withLock { indexMemo[bookTitle] } ?: disk?.read(INDEX, bookTitle)?.let { decode(IndexDto.serializer(), it) }

    private suspend fun chapter(tref: String): ChapterText {
        lock.withLock { memory.get(tref) }?.let { return it }
        return withContext(Dispatchers.Default) {
            textDto(tref).toChapterText(tref)
        }.also { chapter -> lock.withLock { memory.put(tref, chapter) } }
    }

    private suspend fun textDto(tref: String): V3TextResponseDto {
        // Sefaria has said before that there's no text here (an empty daf): don't ask again, even offline.
        if (disk?.read(EMPTY, tref, TEXT_MAX_AGE_MS) != null) throw DataError.NotFound()
        return try {
            cached(
                namespace = TEXTS,
                key = tref,
                serializer = V3TextResponseDto.serializer(),
                maxAgeMillis = TEXT_MAX_AGE_MS,
                shouldStore = { it.versions.isNotEmpty() },
            ) {
                api.text(tref).also { if (it.error != null) throw DataError.NotFound() }
            }
        } catch (failure: DataError) {
            throw withEmptyNote(tref, failure)
        }
    }

    /**
     * A "no text here" answer is noted for [tref], so it isn't asked again; and when Sefaria can't be
     * reached, an older such note still answers (an empty section stays empty offline).
     */
    private suspend fun withEmptyNote(tref: String, failure: DataError): DataError = when {
        failure is DataError.NotFound -> failure.also { disk?.write(EMPTY, tref, EMPTY_MARKER) }
        disk?.contains(EMPTY, tref) == true -> DataError.NotFound(failure)
        else -> failure
    }

    /** A book's `/api/index`, shared by contents + about so opening a book then its details costs one call. */
    private suspend fun indexOf(bookTitle: String): IndexDto {
        lock.withLock { indexMemo[bookTitle] }?.let { return it }
        return cached(INDEX, bookTitle, IndexDto.serializer(), INDEX_MAX_AGE_MS) {
            api.index(bookTitle).also { if (it.error != null || it.title.isNullOrBlank()) throw DataError.NotFound() }
        }.also { index -> lock.withLock { indexMemo[bookTitle] = index } }
    }

    /** The book's about data, and whether every part of it is settled (false: shown degraded, not memoized). */
    private suspend fun loadAbout(bookTitle: String): Pair<BookAbout, Boolean> {
        // The index drives the screen; if it fails, the whole call fails.
        val index = indexOf(bookTitle)
        val base = index.toBookAbout()
        val authorSlug = index.authors.firstNotNullOfOrNull { it.slug?.takeUnless(String::isBlank) }
        // Author bio + editions are enrichments — fetched in parallel, each degrading to null/empty.
        return coroutineScope {
            val bio = async {
                authorSlug?.let { slug ->
                    dataResult { cached(TOPICS, slug, TopicDto.serializer(), ABOUT_MAX_AGE_MS) { api.topic(slug) } }
                }
            }
            val editions = async {
                dataResult {
                    cached(VERSIONS, bookTitle, VersionsSerializer, ABOUT_MAX_AGE_MS) { api.versions(bookTitle) }
                }
            }
            val bioResult = bio.await()
            val editionsResult = editions.await()
            val about = base.copy(
                authorBio = bioResult?.getOrNull()?.toAuthorBio(),
                editions = editionsResult.getOrNull()?.toEditions().orEmpty(),
            )
            about to ((bioResult?.isSettled() ?: true) && editionsResult.isSettled())
        }
    }

    /** Answered for good: a value, or Sefaria saying there's none (unlike being offline, worth remembering). */
    private fun Result<*>.isSettled(): Boolean = isSuccess || exceptionOrNull() is DataError.NotFound

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
        } catch (expected: Exception) {
            val error = expected.toDataError() // HTTP 404 and an HTTP‑200 {"error"} alike are NotFound
            if (error is DataError.NotFound) throw error // a real answer, not an outage — no stale fallback
            // Offline (or Sefaria is down): an old copy beats an error.
            disk?.read(namespace, key)?.let { decode(serializer, it) } ?: throw error
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
        const val EMPTY = "empty" // refs Sefaria has no text for
        const val EMPTY_MARKER = "{}"
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val TEXT_MAX_AGE_MS = 30 * DAY_MS
        const val INDEX_MAX_AGE_MS = 7 * DAY_MS
        const val ABOUT_MAX_AGE_MS = 30 * DAY_MS
        val VersionsSerializer = ListSerializer(VersionMetaDto.serializer())
    }
}
