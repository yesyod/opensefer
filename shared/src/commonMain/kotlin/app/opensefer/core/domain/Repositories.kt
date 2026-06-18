package app.opensefer.core.domain

import app.opensefer.core.model.BookAbout
import app.opensefer.core.model.BookContents
import app.opensefer.core.model.BookSearchResult
import app.opensefer.core.model.ChapterText
import app.opensefer.core.model.LibraryBook
import kotlinx.coroutines.flow.StateFlow

/** Reads structure and text from Sefaria, with caching (impl in :shared/data). */
interface TextRepository {
    /** A book's navigable table of contents (numbered chapters or named sections). */
    suspend fun getContents(bookTitle: String): Result<BookContents>

    /** The text of one readable unit, addressed by its exact Sefaria [tref]. */
    suspend fun getText(tref: String): Result<ChapterText>

    /** Rich metadata for the "About this book" screen (index + author bio + editions). */
    suspend fun getAbout(bookTitle: String): Result<BookAbout>

    /** Fire‑and‑forget warm of the cache for snappy paging; failures are swallowed. */
    suspend fun prefetch(tref: String)
}

/** The user's selected books + resume positions (local only, no account). */
interface LibraryRepository {
    val books: StateFlow<List<LibraryBook>>
    fun add(book: LibraryBook)
    fun remove(title: String)
    fun updatePosition(title: String, tref: String, label: String)
}

/** Title autocomplete for adding a book. */
interface SearchRepository {
    suspend fun search(query: String): Result<List<BookSearchResult>>
}

/** Reading preferences (font scale, theme, language, nikud). */
interface ReadingPreferencesRepository {
    val preferences: StateFlow<ReadingPreferences>
    fun setFontScale(scale: Float)
    fun setTheme(theme: ReadingTheme)
    fun setLanguage(language: ReadingLanguage)
    fun setShowNikud(show: Boolean)
}

data class ReadingPreferences(
    val fontScale: Float = 1f,
    val theme: ReadingTheme = ReadingTheme.Light,
    val language: ReadingLanguage = ReadingLanguage.Bilingual,
    val showNikud: Boolean = true,
)

enum class ReadingTheme { Light, Sepia, Dark }

enum class ReadingLanguage { Hebrew, English, Bilingual }
