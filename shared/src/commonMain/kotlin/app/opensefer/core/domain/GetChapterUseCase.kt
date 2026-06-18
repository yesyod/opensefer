package app.opensefer.core.domain

import app.opensefer.core.model.ChapterText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Loads one readable unit and warms the cache for its neighbours (the prev/next sections) so
 * paging feels instant. This is the one piece of real orchestration that earns a use case
 * (BLUEPRINT §9); library and preferences are trivial enough to read straight from their repos.
 */
class GetChapterUseCase(private val repository: TextRepository) {

    suspend operator fun invoke(
        tref: String,
        neighbours: List<String>,
        prefetchScope: CoroutineScope,
    ): Result<ChapterText> {
        val result = repository.getText(tref)
        if (result.isSuccess) {
            neighbours.forEach { neighbour ->
                prefetchScope.launch { repository.prefetch(neighbour) }
            }
        }
        return result
    }
}
