package app.opensefer.core.data

import app.opensefer.core.domain.SearchRepository
import app.opensefer.core.model.BookSearchResult
import app.opensefer.core.network.SefariaApi

/** Title autocomplete via `/api/name`, filtered to texts the user can actually open. */
class DefaultSearchRepository(private val api: SefariaApi) : SearchRepository {

    private val excludedTypes = setOf("User", "Topic", "Term", "AuthorTopic", "TocCategory")

    override suspend fun search(query: String): Result<List<BookSearchResult>> = runCatching {
        if (query.isBlank()) return Result.success(emptyList())
        api.name(query).completionObjects
            .asSequence()
            .filter { it.type !in excludedTypes }
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
            .take(20)
            .toList()
    }
}
