package app.opensefer.ui.book

import app.opensefer.core.domain.BookDownloader
import app.opensefer.core.domain.DataError
import app.opensefer.ui.FakeLibraryRepository
import app.opensefer.ui.FakeTextRepository
import app.opensefer.ui.UiStrings
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
        val vm = BookViewModel(text, library, BookDownloader(text, library, backgroundScope), "Book", "ספר")
        advanceUntilIdle()

        vm.toggleSaved()
        advanceUntilIdle()
        val saved = assertNotNull(vm.state.value.saved)
        assertEquals("תנ״ך", saved.heCategory)

        vm.toggleSaved()
        advanceUntilIdle()
        assertNull(vm.state.value.saved)
    }

    @Test
    fun aBookThatFailedToLoad_cannotBeSaved_andExplainsWhy() = viewModelTest {
        val library = FakeLibraryRepository()
        val text = FakeTextRepository(contents = Result.failure(DataError.NotFound()))
        val vm = BookViewModel(text, library, BookDownloader(text, library, backgroundScope), "Nope", "לא")
        advanceUntilIdle()

        vm.toggleSaved()
        advanceUntilIdle()

        assertTrue(library.books.value.isEmpty()) // a book that won't open is never saved
        assertEquals(UiStrings.ERROR_NOT_FOUND, vm.state.value.error)
    }
}
