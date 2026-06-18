package app.opensefer.ui.reader

import app.opensefer.core.domain.GetChapterUseCase
import app.opensefer.core.domain.ReadingTheme
import app.opensefer.ui.FakeLibraryRepository
import app.opensefer.ui.FakePreferencesRepository
import app.opensefer.ui.FakeTextRepository
import app.opensefer.ui.viewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private fun reader(
        text: FakeTextRepository = FakeTextRepository(),
        prefs: FakePreferencesRepository = FakePreferencesRepository(),
        library: FakeLibraryRepository = FakeLibraryRepository(),
        startTref: String? = null,
    ) = ReaderViewModel(
        textRepository = text,
        preferencesRepository = prefs,
        libraryRepository = library,
        getChapter = GetChapterUseCase(text),
        bookTitle = "Book",
        heBookTitle = "ספר",
        startTref = startTref,
    )

    @Test
    fun loadContents_success_flattensBookIntoItems() = viewModelTest {
        val vm = reader()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertNull(state.error)
        assertEquals(3, state.contents?.leaves?.size)
        assertTrue(state.items.isNotEmpty())
    }

    @Test
    fun loadContents_failure_surfacesErrorMessage() = viewModelTest {
        val vm = reader(text = FakeTextRepository(contents = Result.failure(RuntimeException("offline"))))
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals("offline", state.error)
    }

    @Test
    fun passage_warmsNeighbouringSections() = viewModelTest {
        val text = FakeTextRepository()
        val vm = reader(text = text)
        advanceUntilIdle()

        val result = vm.passage("Book 2")
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        // Book 2's neighbours — Book 1 and Book 3 — get prefetched so paging stays instant.
        assertEquals(setOf("Book 1", "Book 3"), text.prefetched.toSet())
    }

    @Test
    fun preferenceChange_propagatesToState() = viewModelTest {
        val prefs = FakePreferencesRepository()
        val vm = reader(prefs = prefs)
        advanceUntilIdle()

        prefs.setTheme(ReadingTheme.Dark)
        advanceUntilIdle()

        assertEquals(ReadingTheme.Dark, vm.state.value.preferences.theme)
    }

    @Test
    fun rememberPosition_recordsTheBookAndTref() = viewModelTest {
        val library = FakeLibraryRepository()
        val vm = reader(library = library)
        advanceUntilIdle()

        vm.rememberPosition("Book 2", "פרק ב")

        assertEquals(Triple("Book", "Book 2", "פרק ב"), library.positions.single())
    }

    @Test
    fun sheetState_opensAndCloses() = viewModelTest {
        val vm = reader()
        advanceUntilIdle()

        vm.openTree()
        assertTrue(vm.state.value.showTree)
        vm.closeTree()
        assertFalse(vm.state.value.showTree)

        vm.openDisplaySheet()
        assertTrue(vm.state.value.showDisplaySheet)
        vm.closeDisplaySheet()
        assertFalse(vm.state.value.showDisplaySheet)
    }
}
