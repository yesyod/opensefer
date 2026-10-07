package app.opensefer.core.domain

import app.opensefer.core.model.BookAbout
import app.opensefer.core.model.BookContents
import app.opensefer.core.model.BookSearchResult
import app.opensefer.core.model.Bookmark
import app.opensefer.core.model.ChapterText
import app.opensefer.core.model.LibraryBook
import app.opensefer.core.model.ReadingPosition
import kotlinx.coroutines.flow.StateFlow

/**
 * Reads structure and text from Sefaria, **offline‑first**: memory → on‑device cache → network
 * (impl in :shared/data). Anything fetched once is kept on the device, so a book opens instantly the
 * next time and stays readable without a connection. Failures arrive as a [DataError].
 */
interface TextRepository {
    /** A book's navigable table of contents (numbered chapters or named sections). */
    suspend fun getContents(bookTitle: String): Result<BookContents>

    /** The text of one readable unit, addressed by its exact Sefaria [tref]. */
    suspend fun getText(tref: String): Result<ChapterText>

    /** Rich metadata for the "About this book" screen (index + author bio + editions). */
    suspend fun getAbout(bookTitle: String): Result<BookAbout>

    /** Fire‑and‑forget warm of the caches for snappy paging; failures are swallowed. */
    suspend fun prefetch(tref: String)

    /**
     * Makes sure [tref] is in the on‑device cache (downloading it if needed) without parsing it —
     * the building block of whole‑book offline downloads. Succeeds once it's stored, or when Sefaria
     * has no text there (nothing to keep); fails with [DataError.Storage] when it couldn't be written.
     */
    suspend fun cacheForOffline(tref: String): Result<Unit>
}

/** The user's saved books + resume positions (local only, no account). */
interface LibraryRepository {
    val books: StateFlow<List<LibraryBook>>

    /** False until the persisted library has been read once — lets the first frame skip a flash of defaults. */
    val loaded: StateFlow<Boolean>

    fun add(book: LibraryBook)
    fun remove(title: String)

    /** Remembers where the reader is in a saved book (no‑op for books that aren't saved). */
    fun updatePosition(title: String, position: ReadingPosition)

    /** Fills in the cover metadata (category, author) once the book's index is known. */
    fun updateDetails(title: String, category: String?, heCategory: String?, heAuthor: String?)

    fun setOffline(title: String, offline: Boolean)
}

/** Saved places inside books — one segment each — newest first. */
interface BookmarkRepository {
    val bookmarks: StateFlow<List<Bookmark>>

    /** Adds [bookmark], replacing any existing bookmark on the same segment. */
    fun add(bookmark: Bookmark)
    fun remove(id: String)
}

/** The on‑device copy of every text read or downloaded — so the user can see and free its space. */
interface OfflineStorage {
    /**
     * True when [bookTitle]'s structure is on this device — false after a backup restore, which
     * brings the library back but not the texts (they are deliberately left out of backups).
     */
    suspend fun isStored(bookTitle: String): Boolean

    suspend fun sizeBytes(): Long

    /** Frees space: deletes every stored text except those of [keepBooks] (the saved books' titles). */
    suspend fun clearExcept(keepBooks: Set<String>)
}

/** Title autocomplete for adding a book. */
interface SearchRepository {
    suspend fun search(query: String): Result<List<BookSearchResult>>
}

/** Reading preferences (font scale, theme, language, nikud). */
interface ReadingPreferencesRepository {
    val preferences: StateFlow<ReadingPreferences>

    /** False until the persisted preferences have been read once (see [LibraryRepository.loaded]). */
    val loaded: StateFlow<Boolean>

    fun setFontScale(scale: Float)
    fun setTheme(theme: ReadingTheme)
    fun setLanguage(language: ReadingLanguage)
    fun setShowNikud(show: Boolean)

    /** Flips [ReadingPreferences.showNikud] against the stored value (two quick taps cancel out). */
    fun toggleShowNikud()
}

data class ReadingPreferences(
    val fontScale: Float = 1f,
    val theme: ReadingTheme = ReadingTheme.System,
    val language: ReadingLanguage = ReadingLanguage.Hebrew,
    val showNikud: Boolean = true,
)

/** [System] follows the device's light / dark setting. */
enum class ReadingTheme { System, Light, Sepia, Dark }

enum class ReadingLanguage { Hebrew, English, Bilingual }
