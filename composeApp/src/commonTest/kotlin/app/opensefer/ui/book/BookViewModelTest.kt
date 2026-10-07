package app.opensefer.ui.book

import app.opensefer.core.domain.DataError
import app.opensefer.core.model.BookContents
import app.opensefer.core.model.TocBranch
import app.opensefer.core.model.TocLeaf
import app.opensefer.ui.FakeLibraryRepository
import app.opensefer.ui.FakeTextRepository
import app.opensefer.ui.UiStrings
import app.opensefer.ui.sampleContents
import app.opensefer.ui.testDownloader
import app.opensefer.ui.viewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BookViewModelTest {

    @Test
    fun aLoadedBook_savesWithItsCoverDetails_andUnsavesAgain() = viewModelTest {
        val library = FakeLibraryRepository()
        val text = FakeTextRepository()
        val vm = BookViewModel(text, library, testDownloader(library, text), "Book", "ספר")
        advanceUntilIdle()

        vm.toggleSaved()
        advanceUntilIdle()
        val saved = assertNotNull(vm.state.value.saved)
        assertEquals("תנ״ך", saved.heCategory)

        val removed = assertNotNull(vm.toggleSaved()) // removing hands back what was removed…
        advanceUntilIdle()
        assertNull(vm.state.value.saved)

        vm.restore(removed) // …so "undo" puts it back as it was
        advanceUntilIdle()
        assertEquals(saved, vm.state.value.saved)
    }

    @Test
    fun theSizeLine_countsInTheBooksOwnUnits() {
        assertEquals("3 פרקים", sectionCount(sampleContents(chapters = 3).withHebrewTitles { "פרק $it" }))
        assertNull(sectionCount(sampleContents(chapters = 1))) // one piece: no count at all
        val tractate = sampleContents(chapters = 125).withHebrewTitles { "דף ב׳ ע״א" } // amudim 2a…64a
        assertEquals("63 דפים", sectionCount(tractate))
    }

    private fun BookContents.withHebrewTitles(title: (Int) -> String) = copy(
        root = TocBranch(
            root.title,
            root.heTitle,
            leaves.mapIndexed { i, leaf -> TocLeaf(leaf.title, title(i + 1), leaf.crumb, leaf.tref) },
        ),
    )

    @Test
    fun aBookThatFailedToLoad_cannotBeSaved_andExplainsWhy() = viewModelTest {
        val library = FakeLibraryRepository()
        val text = FakeTextRepository(contents = Result.failure(DataError.NotFound()))
        val vm = BookViewModel(text, library, testDownloader(library, text), "Nope", "לא")
        advanceUntilIdle()

        vm.toggleSaved()
        advanceUntilIdle()

        assertTrue(library.books.value.isEmpty()) // a book that won't open is never saved
        assertEquals(UiStrings.ERROR_NOT_FOUND, vm.state.value.error)
    }
}
