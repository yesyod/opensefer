package app.opensefer.ui.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.opensefer.core.domain.BookDownloader
import app.opensefer.core.domain.BookmarkRepository
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.model.Bookmark
import app.opensefer.core.model.DownloadProgress
import app.opensefer.core.model.LibraryBook
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The home screen: the saved books (most recently used first), what to continue, and bookmarks. */
@Immutable
data class LibraryUiState(
    val books: List<LibraryBook> = emptyList(),
    val continueReading: LibraryBook? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val downloads: Map<String, DownloadProgress> = emptyMap(),
)

class LibraryViewModel(
    private val library: LibraryRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val downloader: BookDownloader,
) : ViewModel() {

    val state: StateFlow<LibraryUiState> =
        combine(library.books, bookmarkRepository.bookmarks, downloader.progress, ::toState)
            // The first frame already has the library (the app shows nothing until it has loaded).
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                toState(library.books.value, bookmarkRepository.bookmarks.value, downloader.progress.value),
            )

    private fun toState(
        books: List<LibraryBook>,
        bookmarks: List<Bookmark>,
        downloads: Map<String, DownloadProgress>,
    ) = LibraryUiState(
        // Most recently read (or saved) first — the books you use most are always at the top.
        books = books.sortedByDescending { maxOf(it.lastReadAt, it.addedAt) },
        continueReading = books.filter { it.lastReadAt > 0 && it.lastTref != null }.maxByOrNull { it.lastReadAt },
        bookmarks = bookmarks,
        downloads = downloads,
    )

    /** Removes [book]; returns it as it was at that moment, for [undoRemove]. */
    fun remove(book: LibraryBook): LibraryBook {
        val current = library.books.value.firstOrNull { it.title == book.title } ?: book
        library.remove(book.title)
        return current
    }

    /** Puts a just‑removed book back exactly as it was (position, place on the shelf and all). */
    fun undoRemove(book: LibraryBook) = library.add(book)

    fun download(book: LibraryBook) = downloader.download(book.title)

    fun removeBookmark(bookmark: Bookmark) = bookmarkRepository.remove(bookmark.id)

    /** Puts a just‑removed bookmark back where it was in the list. */
    fun restoreBookmark(bookmark: Bookmark) = bookmarkRepository.add(bookmark)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
