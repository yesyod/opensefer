package app.opensefer.ui.book

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.opensefer.core.domain.BookDownloader
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.domain.TextRepository
import app.opensefer.core.model.BookContents
import app.opensefer.core.model.DownloadProgress
import app.opensefer.core.model.LibraryBook
import app.opensefer.ui.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A book's page: its contents, whether it's saved (and where the reader is in it), its offline state. */
@Immutable
data class BookUiState(
    val title: String,
    val heTitle: String,
    val contents: BookContents? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val saved: LibraryBook? = null,
    val download: DownloadProgress? = null,
)

class BookViewModel(
    private val text: TextRepository,
    private val library: LibraryRepository,
    private val downloader: BookDownloader,
    title: String,
    heTitle: String,
) : ViewModel() {

    private val _state = MutableStateFlow(BookUiState(title, heTitle))
    val state: StateFlow<BookUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(library.books, downloader.progress) { books, downloads ->
                books.firstOrNull { it.title == title } to downloads[title]
            }.collect { (saved, download) -> _state.update { it.copy(saved = saved, download = download) } }
        }
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            text.getContents(_state.value.title).fold(
                onSuccess = { contents -> _state.update { it.copy(contents = contents, loading = false) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
        }
    }

    /** Saves the book (its whole text then downloads in the background) — or removes it. */
    fun toggleSaved() {
        val s = _state.value
        if (s.saved != null) {
            library.remove(s.title)
            return
        }
        val details = s.contents?.details ?: return // only a book that loaded can be saved
        library.add(
            LibraryBook(
                title = s.title,
                heTitle = s.heTitle,
                category = details.category,
                heCategory = details.heCategory,
                heAuthor = details.heAuthor,
            ),
        )
    }

    fun download() = downloader.download(_state.value.title)
}
