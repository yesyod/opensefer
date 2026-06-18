package app.opensefer.ui

import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.domain.ReadingLanguage
import app.opensefer.core.domain.ReadingPreferences
import app.opensefer.core.domain.ReadingPreferencesRepository
import app.opensefer.core.domain.ReadingTheme
import app.opensefer.core.domain.SearchRepository
import app.opensefer.core.domain.TextRepository
import app.opensefer.core.model.Attribution
import app.opensefer.core.model.BookAbout
import app.opensefer.core.model.BookContents
import app.opensefer.core.model.BookSearchResult
import app.opensefer.core.model.ChapterText
import app.opensefer.core.model.LibraryBook
import app.opensefer.core.model.TocBranch
import app.opensefer.core.model.TocLeaf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Runs [body] with the Main dispatcher backed by the test scheduler, so coroutines launched on a
 * ViewModel's `viewModelScope` execute under the test's virtual clock (and `advanceUntilIdle()` etc.
 * drive them). Main is reset afterwards. See the Google guidance on testing coroutines/ViewModels.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun viewModelTest(body: suspend TestScope.() -> Unit): TestResult = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        body()
    } finally {
        Dispatchers.resetMain()
    }
}

/** A three‑section simple book reused across reader tests. */
internal fun sampleContents(): BookContents = BookContents(
    title = "Book",
    heTitle = "ספר",
    isComplex = false,
    root = TocBranch(
        title = "Book",
        heTitle = "ספר",
        children = listOf(
            TocLeaf("Book 1", "פרק א", "פרק א", "Book 1"),
            TocLeaf("Book 2", "פרק ב", "פרק ב", "Book 2"),
            TocLeaf("Book 3", "פרק ג", "פרק ג", "Book 3"),
        ),
    ),
)

internal fun sampleChapter(tref: String = "Book 1"): ChapterText =
    ChapterText(tref, tref, tref, segments = emptyList(), attribution = Attribution(null, null, null))

internal class FakeTextRepository(
    private val contents: Result<BookContents> = Result.success(sampleContents()),
    private val chapter: Result<ChapterText> = Result.success(sampleChapter()),
) : TextRepository {
    val prefetched = mutableListOf<String>()
    var getTextCount = 0
        private set

    override suspend fun getContents(bookTitle: String): Result<BookContents> = contents

    override suspend fun getText(tref: String): Result<ChapterText> {
        getTextCount++
        return chapter
    }

    override suspend fun getAbout(bookTitle: String): Result<BookAbout> =
        Result.failure(UnsupportedOperationException("not used in tests"))

    override suspend fun prefetch(tref: String) {
        prefetched += tref
    }
}

internal class FakePreferencesRepository(
    initial: ReadingPreferences = ReadingPreferences(),
) : ReadingPreferencesRepository {
    private val _preferences = MutableStateFlow(initial)
    override val preferences: StateFlow<ReadingPreferences> = _preferences

    override fun setFontScale(scale: Float) = _preferences.update { it.copy(fontScale = scale) }
    override fun setTheme(theme: ReadingTheme) = _preferences.update { it.copy(theme = theme) }
    override fun setLanguage(language: ReadingLanguage) = _preferences.update { it.copy(language = language) }
    override fun setShowNikud(show: Boolean) = _preferences.update { it.copy(showNikud = show) }
}

internal class FakeLibraryRepository : LibraryRepository {
    private val _books = MutableStateFlow<List<LibraryBook>>(emptyList())
    override val books: StateFlow<List<LibraryBook>> = _books
    val positions = mutableListOf<Triple<String, String, String>>()

    override fun add(book: LibraryBook) = _books.update { it + book }
    override fun remove(title: String) = _books.update { books -> books.filterNot { it.title == title } }
    override fun updatePosition(title: String, tref: String, label: String) {
        positions += Triple(title, tref, label)
    }
}

internal class FakeSearchRepository(
    private val result: Result<List<BookSearchResult>> = Result.success(emptyList()),
) : SearchRepository {
    var callCount = 0
        private set
    var lastQuery: String? = null
        private set

    override suspend fun search(query: String): Result<List<BookSearchResult>> {
        callCount++
        lastQuery = query
        return result
    }
}
