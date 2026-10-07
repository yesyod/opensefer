package app.opensefer.ui

import app.opensefer.core.domain.BookDownloader
import app.opensefer.core.domain.BookmarkRepository
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.domain.OfflineStorage
import app.opensefer.core.domain.ReadingLanguage
import app.opensefer.core.domain.ReadingPreferences
import app.opensefer.core.domain.ReadingPreferencesRepository
import app.opensefer.core.domain.ReadingTheme
import app.opensefer.core.domain.SearchRepository
import app.opensefer.core.domain.TextRepository
import app.opensefer.core.model.Attribution
import app.opensefer.core.model.BookAbout
import app.opensefer.core.model.BookContents
import app.opensefer.core.model.BookDetails
import app.opensefer.core.model.BookSearchResult
import app.opensefer.core.model.Bookmark
import app.opensefer.core.model.ChapterText
import app.opensefer.core.model.LibraryBook
import app.opensefer.core.model.ReadingPosition
import app.opensefer.core.model.RichText
import app.opensefer.core.model.Segment
import app.opensefer.core.model.TocBranch
import app.opensefer.core.model.TocLeaf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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

/** A three‑chapter simple book reused across reader tests: "Book 1".."Book 3". */
internal fun sampleContents(chapters: Int = 3): BookContents = BookContents(
    title = "Book",
    heTitle = "ספר",
    isComplex = false,
    root = TocBranch(
        title = "Book",
        heTitle = "ספר",
        children = (1..chapters).map { TocLeaf("Book $it", "פרק $it", "פרק $it", "Book $it") },
    ),
    details = BookDetails(category = "Tanakh", heCategory = "תנ״ך", heSegmentName = "פסוק"),
)

/** A chapter of [segments] numbered segments ("א", "ב", …) with Hebrew + English text. */
internal fun sampleChapter(tref: String = "Book 1", segments: Int = 3): ChapterText = ChapterText(
    tref = tref,
    displayTitle = tref,
    heDisplayTitle = tref,
    segments = (0 until segments).map { i ->
        Segment(
            index = i,
            label = listOf("א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט", "י")[i % 10],
            isRubric = false,
            hebrew = RichText.of("עברית $tref $i"),
            english = RichText.of("English $tref $i"),
            enLabel = "${i + 1}",
        )
    },
    attribution = Attribution(null, null, null),
    ref = tref,
    heRef = "ספר ${tref.substringAfter(' ')}",
)

internal class FakeTextRepository(
    private val contents: Result<BookContents> = Result.success(sampleContents()),
    private val latencyMillis: (String) -> Long = { 0L }, // virtual time each passage takes to "download"
    private val chapter: (String) -> Result<ChapterText> = { Result.success(sampleChapter(it)) },
) : TextRepository {
    val requested = mutableListOf<String>()

    override suspend fun getContents(bookTitle: String): Result<BookContents> = contents

    override suspend fun getText(tref: String): Result<ChapterText> {
        requested += tref
        latencyMillis(tref).takeIf { it > 0 }?.let { delay(it) }
        return chapter(tref)
    }

    override suspend fun getAbout(bookTitle: String): Result<BookAbout> =
        Result.failure(UnsupportedOperationException("not used in tests"))

    override suspend fun prefetch(tref: String) = Unit

    override suspend fun cacheForOffline(tref: String): Result<Unit> = Result.success(Unit)
}

internal class FakePreferencesRepository(
    initial: ReadingPreferences = ReadingPreferences(),
) : ReadingPreferencesRepository {
    private val _preferences = MutableStateFlow(initial)
    override val preferences: StateFlow<ReadingPreferences> = _preferences
    override val loaded: StateFlow<Boolean> = MutableStateFlow(true)

    override fun setFontScale(scale: Float) = _preferences.update { it.copy(fontScale = scale) }
    override fun setTheme(theme: ReadingTheme) = _preferences.update { it.copy(theme = theme) }
    override fun setLanguage(language: ReadingLanguage) = _preferences.update { it.copy(language = language) }
    override fun setShowNikud(show: Boolean) = _preferences.update { it.copy(showNikud = show) }
    override fun toggleShowNikud() = _preferences.update { it.copy(showNikud = !it.showNikud) }
}

/** A downloader running in the test's background scope, over fakes. */
internal fun TestScope.testDownloader(
    library: LibraryRepository,
    text: TextRepository = FakeTextRepository(),
): BookDownloader = BookDownloader(text, library, backgroundScope, FakeOfflineStorage())

/** On‑device storage that holds every book (nothing to re‑download). */
internal class FakeOfflineStorage : OfflineStorage {
    override suspend fun isStored(bookTitle: String) = true
    override suspend fun sizeBytes() = 0L
    override suspend fun clearExcept(keepBooks: Set<String>) = Unit
}

internal class FakeLibraryRepository(initial: List<LibraryBook> = emptyList()) : LibraryRepository {
    private val _books = MutableStateFlow(initial)
    override val books: StateFlow<List<LibraryBook>> = _books
    override val loaded: StateFlow<Boolean> = MutableStateFlow(true)
    val positions = mutableListOf<Pair<String, ReadingPosition>>()

    override fun add(book: LibraryBook) = _books.update { list -> if (list.any { it.title == book.title }) list else list + book }
    override fun remove(title: String) = _books.update { books -> books.filterNot { it.title == title } }
    override fun updatePosition(title: String, position: ReadingPosition) {
        positions += title to position
        _books.update { list ->
            list.map {
                if (it.title == title) it.copy(lastTref = position.tref, lastSegment = position.segment, lastReadAt = 1) else it
            }
        }
    }
    override fun updateDetails(title: String, category: String?, heCategory: String?, heAuthor: String?) = Unit
    override fun setOffline(title: String, offline: Boolean) = Unit
}

internal class FakeBookmarkRepository : BookmarkRepository {
    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    override val bookmarks: StateFlow<List<Bookmark>> = _bookmarks

    override fun add(bookmark: Bookmark) = _bookmarks.update { list -> listOf(bookmark) + list.filterNot { it.id == bookmark.id } }
    override fun remove(id: String) = _bookmarks.update { list -> list.filterNot { it.id == id } }
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
