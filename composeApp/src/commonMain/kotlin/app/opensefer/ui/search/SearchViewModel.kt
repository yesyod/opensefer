package app.opensefer.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.domain.SearchRepository
import app.opensefer.core.model.BookSearchResult
import app.opensefer.core.model.LibraryBook
import app.opensefer.ui.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<BookSearchResult> = emptyList(),
    val searched: Boolean = false, // a search for [query] has completed (so "no results" can show)
    val error: String? = null,
    val savedTitles: Set<String> = emptySet(),
)

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            libraryRepository.books.collect { books ->
                _state.update { it.copy(savedTitles = books.mapTo(HashSet()) { book -> book.title }) }
            }
        }
    }

    fun onQueryChange(query: String) {
        if (query == _state.value.query) return
        _state.update { it.copy(query = query, searched = false) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(results = emptyList(), loading = false, error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS) // debounce keystrokes before hitting the API
            _state.update { it.copy(loading = true, error = null) }
            searchRepository.search(query).fold(
                onSuccess = { results ->
                    _state.update { it.copy(results = results, loading = false, error = null, searched = true) }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage(), searched = true) } },
            )
        }
    }

    /** One‑tap save from the results; tapping again removes it. */
    fun toggleSaved(result: BookSearchResult) {
        if (result.title in _state.value.savedTitles) {
            libraryRepository.remove(result.title)
        } else {
            libraryRepository.add(LibraryBook(result.title, result.heTitle))
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 280L
    }
}
