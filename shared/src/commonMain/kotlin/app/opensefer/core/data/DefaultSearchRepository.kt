package app.opensefer.core.data

import app.opensefer.core.domain.SearchRepository
import app.opensefer.core.model.BookSearchResult
import app.opensefer.core.network.SefariaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Title autocomplete via `/api/name`, kept to texts the user can actually open (`type == "ref"`). */
class DefaultSearchRepository(private val api: SefariaApi) : SearchRepository {

    override suspend fun search(query: String): Result<List<BookSearchResult>> = dataResult {
        if (query.isBlank()) return Result.success(emptyList())
        withContext(Dispatchers.Default) {
            api.name(query).completionObjects
                .asSequence()
                .filter { it.type == REF_TYPE }
                .mapNotNull { obj ->
                    // `key` is Sefaria's canonical ref (e.g. "Mishneh Torah, Repentance") — the identifier
                    // the index/text endpoints need. `title` is the matched display name in the query's
                    // language (Hebrew for a Hebrew query); using it as the identifier sends a Hebrew tref
                    // that Sefaria resolves to the whole book instead of a chapter. key → id, title → display.
                    val canonical = obj.keyString ?: obj.title ?: return@mapNotNull null
                    val display = obj.title ?: canonical
                    BookSearchResult(title = canonical, heTitle = obj.he ?: display, category = obj.type)
                }
                .distinctBy { it.title }
                .take(MAX_RESULTS)
                .toList()
        }
    }

    private companion object {
        const val REF_TYPE = "ref"
        const val MAX_RESULTS = 20
    }
}
