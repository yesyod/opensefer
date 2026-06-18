package app.opensefer.core.data

import app.opensefer.core.domain.TextRepository
import app.opensefer.core.model.BookAbout
import app.opensefer.core.model.BookContents
import app.opensefer.core.model.ChapterText
import app.opensefer.core.network.SefariaApi
import app.opensefer.core.network.dto.IndexDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Network‑first [TextRepository]: serve from the in‑memory [SectionCache] when warm, otherwise
 * fetch from Sefaria, map DTO → domain (parsing inline HTML + the structure tree), cache, and
 * return. A book's contents are fetched once and memoized (they change rarely). See BLUEPRINT §8.
 */
class DefaultTextRepository(
    private val api: SefariaApi,
    private val cache: SectionCache = SectionCache(),
) : TextRepository {

    private val indexCache = mutableMapOf<String, IndexDto>()
    private val aboutCache = mutableMapOf<String, BookAbout>()

    /** Fetch a book's `/api/index` once and memoize the raw DTO — shared by contents + about,
     *  so opening a book and then viewing its details doesn't hit the endpoint twice. */
    private suspend fun indexOf(bookTitle: String): IndexDto =
        indexCache[bookTitle] ?: api.index(bookTitle).also { indexCache[bookTitle] = it }

    override suspend fun getContents(bookTitle: String): Result<BookContents> = runCatching {
        indexOf(bookTitle).toBookContents()
    }

    override suspend fun getText(tref: String): Result<ChapterText> = runCatching {
        cache.get(tref)?.let { return Result.success(it) }
        api.text(tref).toChapterText(tref).also { cache.put(tref, it) }
    }

    override suspend fun getAbout(bookTitle: String): Result<BookAbout> = runCatching {
        aboutCache[bookTitle]?.let { return Result.success(it) }
        // The index drives the screen; if it fails, the whole call fails.
        val index = indexOf(bookTitle)
        val base = index.toBookAbout()
        val authorSlug = index.authors.firstNotNullOfOrNull { it.slug?.takeUnless(String::isBlank) }
        // Author bio + editions are enrichments — fetched in parallel, each degrading to null/empty.
        coroutineScope {
            val bioDeferred = async {
                authorSlug?.let { slug -> runCatching { api.topic(slug).toAuthorBio() }.getOrNull() }
            }
            val editionsDeferred = async {
                runCatching { api.versions(bookTitle).toEditions() }.getOrDefault(emptyList())
            }
            base.copy(authorBio = bioDeferred.await(), editions = editionsDeferred.await())
        }.also { aboutCache[bookTitle] = it }
    }

    override suspend fun prefetch(tref: String) {
        runCatching {
            if (cache.get(tref) == null) {
                api.text(tref).toChapterText(tref).also { cache.put(tref, it) }
            }
        }
    }
}
