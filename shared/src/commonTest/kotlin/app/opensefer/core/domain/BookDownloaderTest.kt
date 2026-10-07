package app.opensefer.core.domain

import app.opensefer.core.model.BookAbout
import app.opensefer.core.model.BookContents
import app.opensefer.core.model.ChapterText
import app.opensefer.core.model.LibraryBook
import app.opensefer.core.model.ReadingPosition
import app.opensefer.core.model.TocBranch
import app.opensefer.core.model.TocLeaf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BookDownloaderTest {

    private fun book(title: String, chapters: Int) = BookContents(
        title = title,
        heTitle = title,
        isComplex = false,
        root = TocBranch(title, title, (1..chapters).map { TocLeaf("$it", "$it", "$it", "$title.$it") }),
    )

    private class FakeText(
        private val books: Map<String, BookContents>,
        private val failWith: ((String) -> DataError?) = { null },
    ) : TextRepository {
        val cached = mutableListOf<String>()
        override suspend fun getContents(bookTitle: String) =
            books[bookTitle]?.let { Result.success(it) } ?: Result.failure(DataError.NotFound())
        override suspend fun getText(tref: String): Result<ChapterText> = error("unused")
        override suspend fun getAbout(bookTitle: String): Result<BookAbout> = error("unused")
        override suspend fun prefetch(tref: String) = Unit
        override suspend fun cacheForOffline(tref: String): Result<Unit> {
            failWith(tref)?.let { return Result.failure(it) }
            cached += tref
            return Result.success(Unit)
        }
    }

    private class FakeLibrary(initial: List<LibraryBook>) : LibraryRepository {
        private val _books = MutableStateFlow(initial)
        override val books: StateFlow<List<LibraryBook>> = _books
        override val loaded: StateFlow<Boolean> = MutableStateFlow(true)
        override fun add(book: LibraryBook) = _books.update { it + book }
        override fun remove(title: String) = _books.update { list -> list.filterNot { it.title == title } }
        override fun updatePosition(title: String, position: ReadingPosition) = Unit
        override fun updateDetails(title: String, category: String?, heCategory: String?, heAuthor: String?) = Unit
        override fun setOffline(title: String, offline: Boolean) =
            _books.update { list -> list.map { if (it.title == title) it.copy(offline = offline) else it } }
    }

    /**
     * Runs [body] with an app scope whose work is *foreground* test work — `advanceUntilIdle` skips
     * `backgroundScope` tasks — and cancels it afterwards (the auto‑download collector never ends).
     */
    private fun runWithAppScope(body: suspend TestScope.(CoroutineScope) -> Unit) = runTest {
        val appScope = CoroutineScope(coroutineContext + Job())
        try {
            body(appScope)
        } finally {
            appScope.cancel()
        }
    }

    @Test
    fun download_cachesEveryPassage_andMarksTheBookOffline() = runWithAppScope { appScope ->
        val text = FakeText(mapOf("Genesis" to book("Genesis", 5)))
        val library = FakeLibrary(listOf(LibraryBook("Genesis", "בראשית")))
        val downloader = BookDownloader(text, library, appScope)

        downloader.download("Genesis")
        advanceUntilIdle()

        assertEquals((1..5).map { "Genesis.$it" }.toSet(), text.cached.toSet())
        assertTrue(library.books.value.single().offline)
        assertTrue(downloader.progress.value.isEmpty()) // finished downloads leave the progress map
    }

    @Test
    fun goingOffline_stopsTheDownload_withoutMarkingTheBookOffline() = runWithAppScope { appScope ->
        val text = FakeText(mapOf("Genesis" to book("Genesis", 30))) { tref ->
            if (tref == "Genesis.4") DataError.Offline() else null
        }
        val library = FakeLibrary(listOf(LibraryBook("Genesis", "בראשית")))
        val downloader = BookDownloader(text, library, appScope)

        downloader.download("Genesis")
        advanceUntilIdle()

        assertTrue(text.cached.size < 30) // stopped early instead of hammering a dead connection
        assertFalse(library.books.value.single().offline)
    }

    @Test
    fun autoDownload_fetchesSmallSavedBooks_butLeavesHugeOnesForAnExplicitTap() = runWithAppScope { appScope ->
        val huge = BookDownloader.AUTO_DOWNLOAD_MAX_PASSAGES + 1
        val text = FakeText(mapOf("Genesis" to book("Genesis", 3), "Shulchan Arukh" to book("Shulchan Arukh", huge)))
        val library = FakeLibrary(listOf(LibraryBook("Genesis", "בראשית"), LibraryBook("Shulchan Arukh", "שולחן ערוך")))
        val downloader = BookDownloader(text, library, appScope)

        downloader.startAutoDownloads(startDelayMillis = 0)
        advanceUntilIdle()

        assertTrue(library.books.value.first { it.title == "Genesis" }.offline)
        assertFalse(library.books.value.first { it.title == "Shulchan Arukh" }.offline)
        assertTrue(text.cached.none { it.startsWith("Shulchan Arukh") })
    }

    @Test
    fun aBookSavedLater_isAutoDownloadedToo() = runWithAppScope { appScope ->
        val text = FakeText(mapOf("Genesis" to book("Genesis", 2), "Exodus" to book("Exodus", 2)))
        val library = FakeLibrary(listOf(LibraryBook("Genesis", "בראשית")))
        val downloader = BookDownloader(text, library, appScope)
        downloader.startAutoDownloads(startDelayMillis = 0)
        advanceUntilIdle()

        library.add(LibraryBook("Exodus", "שמות"))
        advanceUntilIdle()

        assertTrue(library.books.value.all { it.offline })
    }
}
