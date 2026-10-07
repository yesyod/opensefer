package app.opensefer.ui.reader

import app.opensefer.core.domain.DataError
import app.opensefer.core.domain.ReadingTheme
import app.opensefer.core.model.LibraryBook
import app.opensefer.core.model.bookmarkId
import app.opensefer.ui.FakeBookmarkRepository
import app.opensefer.ui.FakeLibraryRepository
import app.opensefer.ui.FakePreferencesRepository
import app.opensefer.ui.FakeTextRepository
import app.opensefer.ui.UiStrings
import app.opensefer.ui.sampleContents
import app.opensefer.ui.viewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private class Deps(
        val text: FakeTextRepository = FakeTextRepository(),
        val prefs: FakePreferencesRepository = FakePreferencesRepository(),
        val library: FakeLibraryRepository = FakeLibraryRepository(),
        val bookmarks: FakeBookmarkRepository = FakeBookmarkRepository(),
    )

    private fun TestScope.reader(deps: Deps = Deps(), startTref: String? = null, startSegment: Int = 0) =
        ReaderViewModel(
            textRepository = deps.text,
            preferencesRepository = deps.prefs,
            libraryRepository = deps.library,
            bookmarkRepository = deps.bookmarks,
            bookTitle = "Book",
            heBookTitle = "ספר",
            startTref = startTref,
            startSegment = startSegment,
            computation = StandardTestDispatcher(testScheduler),
        )

    /** Simulates the screen consuming the initial scroll (which "restores" the reader). */
    private fun ReaderViewModel.restore() {
        val request = assertNotNull(state.value.scrollRequest)
        onScrollRequestHandled(request.id)
    }

    @Test
    fun open_rendersOneRowPerSegment_loadingTheStartPassageAndItsNeighbour() = viewModelTest {
        val vm = reader()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertNull(state.error)
        assertEquals(
            listOf(
                "t:Book 1", "s:Book 1:0", "s:Book 1:1", "s:Book 1:2",
                "t:Book 2", "s:Book 2:0", "s:Book 2:1", "s:Book 2:2",
                "t:Book 3", "l:Book 3", // not near the start yet → a single placeholder row
            ),
            state.rows.map { it.key },
        )
        assertEquals(0, state.scrollRequest?.index) // the start of the book: its first title
    }

    @Test
    fun open_resumesAtTheExactSavedSegment() = viewModelTest {
        val library = FakeLibraryRepository(listOf(LibraryBook("Book", "ספר", lastTref = "Book 2", lastSegment = 2)))
        val vm = reader(Deps(library = library))
        advanceUntilIdle()

        val state = vm.state.value
        val target = state.rows[assertNotNull(state.scrollRequest).index]
        assertIs<SegmentRow>(target)
        assertEquals("Book 2", target.leaf.tref)
        assertEquals(2, target.segment.index)
    }

    @Test
    fun anExplicitStart_winsOverTheSavedPosition() = viewModelTest {
        val library = FakeLibraryRepository(listOf(LibraryBook("Book", "ספר", lastTref = "Book 2", lastSegment = 2)))
        val vm = reader(Deps(library = library), startTref = "Book 3")
        advanceUntilIdle()

        val state = vm.state.value
        val target = state.rows[assertNotNull(state.scrollRequest).index]
        assertEquals("t:Book 3", target.key) // a chapter picked in the contents opens at its title
    }

    @Test
    fun aFailedBook_showsAHebrewMessage() = viewModelTest {
        val vm = reader(Deps(text = FakeTextRepository(contents = Result.failure(DataError.Offline()))))
        advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertEquals(UiStrings.ERROR_OFFLINE, vm.state.value.error)
    }

    @Test
    fun scrolling_savesTheExactPosition_onlyOnceItSettles_andNeverBeforeTheRestore() = viewModelTest {
        val deps = Deps(library = FakeLibraryRepository(listOf(LibraryBook("Book", "ספר"))))
        val vm = reader(deps)
        advanceUntilIdle()

        vm.onScrolled(firstRow = 3, offset = 10) // before the saved place is restored: ignored
        advanceUntilIdle()
        assertTrue(deps.library.positions.isEmpty())

        vm.restore()
        val row = vm.state.value.rows.indexOfFirst { it.key == "s:Book 2:1" }
        vm.onScrolled(firstRow = row, offset = 40)
        advanceTimeBy(100)
        runCurrent()
        assertTrue(deps.library.positions.isEmpty()) // still scrolling — no write yet

        advanceUntilIdle()
        val (title, position) = deps.library.positions.last()
        assertEquals("Book", title)
        assertEquals("Book 2", position.tref)
        assertEquals(1, position.segment)
        assertEquals(40, position.offset)
        assertEquals("פרק 2, פסוק ב", position.label)
        assertEquals(bookmarkId("Book 2", 1), vm.state.value.currentBookmarkId)
    }

    @Test
    fun theVisibleWindow_preloadsTwoPassagesAhead() = viewModelTest {
        val deps = Deps(text = FakeTextRepository(contents = Result.success(sampleContents(chapters = 8))))
        val vm = reader(deps)
        advanceUntilIdle()
        vm.restore()

        val row = vm.state.value.rows.indexOfFirst { it.key == "t:Book 2" }
        vm.onVisibleRange(row, row)
        advanceUntilIdle()

        assertTrue(listOf("Book 3", "Book 4").all { it in deps.text.requested })
        assertFalse("Book 5" in deps.text.requested)
    }

    @Test
    fun selectedSegments_copyAsTextWithTheirSource() = viewModelTest {
        val vm = reader()
        advanceUntilIdle()

        vm.toggleSelection(SegmentRef(passage = 0, segment = 1))
        vm.toggleSelection(SegmentRef(passage = 0, segment = 0))
        val copied = vm.copySelection()

        assertEquals(
            "עברית Book 1 0\nEnglish Book 1 0\n\nעברית Book 1 1\nEnglish Book 1 1\n(ספר 1:א-ב)",
            copied,
        )
        assertTrue(vm.state.value.selection.isEmpty())
        assertEquals(UiStrings.COPIED, vm.state.value.message)
    }

    @Test
    fun bookmarkingASegment_addsThenRemovesIt() = viewModelTest {
        val deps = Deps()
        val vm = reader(deps)
        advanceUntilIdle()

        vm.toggleBookmark(SegmentRef(passage = 1, segment = 2))
        advanceUntilIdle()
        val bookmark = deps.bookmarks.bookmarks.value.single()
        assertEquals("Book 2", bookmark.tref)
        assertEquals(2, bookmark.segment)
        assertEquals("פרק 2, פסוק ג", bookmark.label)
        assertTrue(bookmark.id in vm.state.value.bookmarkedIds)

        vm.toggleBookmark(SegmentRef(passage = 1, segment = 2))
        advanceUntilIdle()
        assertTrue(deps.bookmarks.bookmarks.value.isEmpty())
    }

    @Test
    fun savingFromTheReader_addsTheBookWithItsCoverDetails() = viewModelTest {
        val deps = Deps()
        val vm = reader(deps)
        advanceUntilIdle()
        assertFalse(vm.state.value.saved)

        vm.toggleSaved()
        advanceUntilIdle()

        val book = deps.library.books.value.single()
        assertEquals("Tanakh", book.category)
        assertTrue(vm.state.value.saved)
        assertEquals(UiStrings.SAVED_TO_LIBRARY, vm.state.value.message)
    }

    @Test
    fun preferenceChange_propagatesToState() = viewModelTest {
        val deps = Deps()
        val vm = reader(deps)
        advanceUntilIdle()

        deps.prefs.setTheme(ReadingTheme.Dark)
        advanceUntilIdle()

        assertEquals(ReadingTheme.Dark, vm.state.value.preferences.theme)
    }

    @Test
    fun sheets_openAndClose() = viewModelTest {
        val vm = reader()
        advanceUntilIdle()

        vm.openSheet(ReaderSheet.Contents)
        assertEquals(ReaderSheet.Contents, vm.state.value.sheet)
        vm.openSheet(ReaderSheet.Display)
        assertEquals(ReaderSheet.Display, vm.state.value.sheet)
        vm.closeSheet()
        assertNull(vm.state.value.sheet)
    }

    @Test
    fun jumpingToAFarPassage_loadsItAndScrollsToIt() = viewModelTest {
        val deps = Deps(text = FakeTextRepository(contents = Result.success(sampleContents(chapters = 8))))
        val vm = reader(deps)
        advanceUntilIdle()
        vm.restore()

        vm.jumpTo("Book 7", segment = 2)
        advanceUntilIdle()

        val state = vm.state.value
        val target = state.rows[assertNotNull(state.scrollRequest).index]
        assertEquals("s:Book 7:2", target.key)
    }
}
