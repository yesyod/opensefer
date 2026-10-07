package app.opensefer.ui.library

import app.opensefer.core.domain.BookDownloader
import app.opensefer.core.model.LibraryBook
import app.opensefer.ui.FakeBookmarkRepository
import app.opensefer.ui.FakeLibraryRepository
import app.opensefer.ui.FakeTextRepository
import app.opensefer.ui.viewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val genesis = LibraryBook("Genesis", "בראשית", addedAt = 10)
    private val berakhot = LibraryBook("Berakhot", "ברכות", lastTref = "Berakhot.2a", lastReadAt = 50, addedAt = 1)
    private val avot = LibraryBook("Pirkei Avot", "פרקי אבות", lastTref = "Pirkei_Avot.1", lastReadAt = 30, addedAt = 2)

    @Test
    fun books_areMostRecentlyUsedFirst_andTheLastReadIsOfferedToContinue() = viewModelTest {
        val library = FakeLibraryRepository(listOf(genesis, berakhot, avot))
        val vm = LibraryViewModel(library, FakeBookmarkRepository(), BookDownloader(FakeTextRepository(), library, backgroundScope))
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val state = vm.state.first()
        assertEquals(listOf("Berakhot", "Pirkei Avot", "Genesis"), state.books.map { it.title })
        assertEquals("Berakhot", state.continueReading?.title)
    }

    @Test
    fun nothingRead_meansNoContinueCard() = viewModelTest {
        val library = FakeLibraryRepository(listOf(genesis))
        val vm = LibraryViewModel(library, FakeBookmarkRepository(), BookDownloader(FakeTextRepository(), library, backgroundScope))

        assertNull(vm.state.value.continueReading)
    }

    @Test
    fun removingABook_canBeUndone() = viewModelTest {
        val library = FakeLibraryRepository(listOf(genesis, berakhot))
        val vm = LibraryViewModel(library, FakeBookmarkRepository(), BookDownloader(FakeTextRepository(), library, backgroundScope))

        vm.remove(berakhot)
        assertEquals(listOf("Genesis"), library.books.value.map { it.title })
        vm.undoRemove(berakhot)
        assertEquals("Berakhot.2a", library.books.value.first { it.title == "Berakhot" }.lastTref) // place kept
    }
}
