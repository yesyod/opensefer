package app.opensefer.ui.reader

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.opensefer.core.domain.GetChapterUseCase
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.domain.ReadingLanguage
import app.opensefer.core.domain.ReadingPreferences
import app.opensefer.core.domain.ReadingPreferencesRepository
import app.opensefer.core.domain.ReadingTheme
import app.opensefer.core.domain.TextRepository
import app.opensefer.core.model.BookContents
import app.opensefer.core.model.ChapterText
import app.opensefer.core.model.ReadingItem
import app.opensefer.core.model.toReadingItems
import app.opensefer.ui.UiStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for the **continuous** reader: the whole book as one flattened stream of [ReadingItem]s.
 * Each passage's text is fetched lazily by the UI as it scrolls into view ([passage]); the VM only
 * owns the structure, preferences and sheet state. One object in, intents out (BLUEPRINT §10.1).
 *
 * `@Immutable`: every field is a `val` of an immutable type, so Compose may treat the whole state as
 * stable and skip recomposition when the instance is unchanged.
 */
@Immutable
data class ReaderUiState(
    val bookTitle: String,
    val heBookTitle: String,
    val startTref: String?,
    val contents: BookContents? = null,
    val items: List<ReadingItem> = emptyList(),
    val preferences: ReadingPreferences = ReadingPreferences(),
    val loading: Boolean = true,
    val error: String? = null,
    val showTree: Boolean = false,
    val showDisplaySheet: Boolean = false,
)

class ReaderViewModel(
    private val textRepository: TextRepository,
    private val preferencesRepository: ReadingPreferencesRepository,
    private val libraryRepository: LibraryRepository,
    private val getChapter: GetChapterUseCase,
    bookTitle: String,
    heBookTitle: String,
    startTref: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ReaderUiState(
            bookTitle = bookTitle,
            heBookTitle = heBookTitle,
            startTref = startTref,
            preferences = preferencesRepository.preferences.value,
        ),
    )
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                _state.update { it.copy(preferences = prefs) }
            }
        }
        loadContents()
    }

    fun loadContents() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            textRepository.getContents(state.value.bookTitle).fold(
                onSuccess = { contents ->
                    _state.update {
                        it.copy(loading = false, contents = contents, items = contents.toReadingItems(), error = null)
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: UiStrings.ERROR_BOOK) }
                },
            )
        }
    }

    /**
     * Lazily fetch one passage's text (served from cache when warm) and warm the cache for its
     * neighbours so paging stays instant. Called by a passage as it scrolls into view.
     */
    suspend fun passage(tref: String): Result<ChapterText> =
        getChapter(tref, neighboursOf(tref), viewModelScope)

    /** The prev/next readable units around [tref] in reading order — the sections worth prefetching. */
    private fun neighboursOf(tref: String): List<String> {
        val leaves = state.value.contents?.leaves ?: return emptyList()
        val here = leaves.indexOfFirst { it.tref == tref }
        if (here < 0) return emptyList()
        return listOfNotNull(leaves.getOrNull(here - 1)?.tref, leaves.getOrNull(here + 1)?.tref)
    }

    /** Remember the user's scroll position so the book reopens where they left off. */
    fun rememberPosition(tref: String, label: String) =
        libraryRepository.updatePosition(state.value.bookTitle, tref, label)

    // Reading preferences
    fun setFontScale(scale: Float) = preferencesRepository.setFontScale(scale)
    fun setTheme(theme: ReadingTheme) = preferencesRepository.setTheme(theme)
    fun setLanguage(language: ReadingLanguage) = preferencesRepository.setLanguage(language)
    fun toggleNikud() = preferencesRepository.setShowNikud(!state.value.preferences.showNikud)

    // Sheets (reader UI state, not navigation)
    fun openTree() = _state.update { it.copy(showTree = true) }
    fun closeTree() = _state.update { it.copy(showTree = false) }
    fun openDisplaySheet() = _state.update { it.copy(showDisplaySheet = true) }
    fun closeDisplaySheet() = _state.update { it.copy(showDisplaySheet = false) }
}
