package app.opensefer.ui.search

import app.opensefer.core.domain.DataError
import app.opensefer.core.model.BookSearchResult
import app.opensefer.ui.FakeLibraryRepository
import app.opensefer.ui.FakeSearchRepository
import app.opensefer.ui.UiStrings
import app.opensefer.ui.viewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val sample = listOf(BookSearchResult("Mishneh Torah, Repentance", "הלכות תשובה", "ref"))

    // Comfortably shorter than the ViewModel's 280 ms debounce — used to assert no early search fires.
    private val withinDebounceMs = 100L

    @Test
    fun blankQuery_doesNotHitTheNetwork() = viewModelTest {
        val search = FakeSearchRepository()
        val vm = SearchViewModel(search, FakeLibraryRepository())

        vm.onQueryChange("   ")
        advanceUntilIdle()

        assertEquals(0, search.callCount)
        assertTrue(vm.state.value.results.isEmpty())
    }

    @Test
    fun query_searchesOnlyAfterTheDebounce() = viewModelTest {
        val search = FakeSearchRepository(Result.success(sample))
        val vm = SearchViewModel(search, FakeLibraryRepository())

        vm.onQueryChange("תשובה")
        advanceTimeBy(withinDebounceMs)
        runCurrent()
        assertEquals(0, search.callCount) // still inside the debounce window

        advanceUntilIdle()
        assertEquals(1, search.callCount)
        assertEquals(sample, vm.state.value.results)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun rapidTyping_searchesOnlyTheFinalQuery() = viewModelTest {
        val search = FakeSearchRepository(Result.success(emptyList()))
        val vm = SearchViewModel(search, FakeLibraryRepository())

        vm.onQueryChange("א")
        advanceTimeBy(withinDebounceMs)
        vm.onQueryChange("אב")
        advanceTimeBy(withinDebounceMs)
        vm.onQueryChange("אבג")
        advanceUntilIdle()

        assertEquals(1, search.callCount)
        assertEquals("אבג", search.lastQuery)
    }

    @Test
    fun searchFailure_setsAHebrewError() = viewModelTest {
        val search = FakeSearchRepository(Result.failure(DataError.Offline()))
        val vm = SearchViewModel(search, FakeLibraryRepository())

        vm.onQueryChange("x")
        advanceUntilIdle()

        assertEquals(UiStrings.ERROR_OFFLINE, vm.state.value.error)
    }

    @Test
    fun emptyResults_areMarkedSearched_soTheScreenCanSayNothingWasFound() = viewModelTest {
        val vm = SearchViewModel(FakeSearchRepository(Result.success(emptyList())), FakeLibraryRepository())

        vm.onQueryChange("zzz")
        assertFalse(vm.state.value.searched)
        advanceUntilIdle()

        assertTrue(vm.state.value.searched)
        assertTrue(vm.state.value.results.isEmpty())
    }

    @Test
    fun toggleSaved_savesThenRemovesTheBook_andTracksItAsSaved() = viewModelTest {
        val library = FakeLibraryRepository()
        val vm = SearchViewModel(FakeSearchRepository(), library)
        val result = BookSearchResult("Mishneh Torah, Repentance", "הלכות תשובה", "ref")

        vm.toggleSaved(result)
        advanceUntilIdle()
        assertEquals("Mishneh Torah, Repentance", library.books.value.single().title)
        assertTrue(result.title in vm.state.value.savedTitles)

        vm.toggleSaved(result)
        advanceUntilIdle()
        assertTrue(library.books.value.isEmpty())
    }
}
