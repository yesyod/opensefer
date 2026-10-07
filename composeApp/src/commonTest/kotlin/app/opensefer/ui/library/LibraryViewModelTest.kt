package app.opensefer.ui.library

import app.opensefer.core.model.Bookmark
import app.opensefer.core.model.LibraryBook
import app.opensefer.ui.FakeBookmarkRepository
import app.opensefer.ui.FakeLibraryRepository
import app.opensefer.ui.testDownloader
import app.opensefer.ui.viewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val genesis = LibraryBook("Genesis", "בראשית", addedAt = 10)
    private val berakhot = LibraryBook("Berakhot", "ברכות", lastTref = "Berakhot.2a", lastReadAt = 50, addedAt = 1)
    private val avot = LibraryBook("Pirkei Avot", "פרקי אבות", lastTref = "Pirkei_Avot.1", lastReadAt = 30, addedAt = 2)

    @Test
    fun books_areMostRecentlyUsedFirst_andTheLastReadIsOfferedToContinue() = viewModelTest {
        val library = FakeLibraryRepository(listOf(genesis, berakhot, avot))
        val vm = LibraryViewModel(library, FakeBookmarkRepository(), testDownloader(library))
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val state = vm.state.first()
        assertEquals(listOf("Berakhot", "Pirkei Avot", "Genesis"), state.books.map { it.title })
        assertEquals("Berakhot", state.continueReading?.title)
    }

    @Test
    fun nothingRead_meansNoContinueCard() = viewModelTest {
        val library = FakeLibraryRepository(listOf(genesis))
        val vm = LibraryViewModel(library, FakeBookmarkRepository(), testDownloader(library))

        assertNull(vm.state.value.continueReading)
    }

    @Test
    fun removingABook_canBeUndone() = viewModelTest {
        val library = FakeLibraryRepository(listOf(genesis, berakhot))
        val vm = LibraryViewModel(library, FakeBookmarkRepository(), testDownloader(library))

        val removed = vm.remove(berakhot)
        assertEquals(listOf("Genesis"), library.books.value.map { it.title })
        vm.undoRemove(removed)
        val back = library.books.value.first { it.title == "Berakhot" }
        assertEquals("Berakhot.2a", back.lastTref) // place kept…
        assertEquals(1, back.addedAt) // …and its spot on the shelf
    }

    @Test
    fun removingABookmark_canBeUndone() = viewModelTest {
        val library = FakeLibraryRepository(listOf(genesis))
        val bookmarks = FakeBookmarkRepository()
        val mark = Bookmark("Genesis", "בראשית", "Genesis.1", 2, "פרק א׳, פסוק ג׳", "וַיֹּאמֶר", createdAt = 5)
        bookmarks.add(mark)
        val vm = LibraryViewModel(library, bookmarks, testDownloader(library))

        vm.removeBookmark(mark)
        assertTrue(bookmarks.bookmarks.value.isEmpty())
        vm.restoreBookmark(mark)
        assertEquals(listOf(mark), bookmarks.bookmarks.value)
    }
}
