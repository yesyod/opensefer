package app.opensefer.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.model.LibraryBook
import app.opensefer.core.model.ReadingPosition
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okio.IOException

/**
 * [LibraryRepository] backed by a Preferences DataStore — the user's saved books, their exact resume
 * positions and cover metadata, stored as a JSON list under one key. The library shows
 * [DEFAULT_BOOKS] until the user first changes it; from then on the persisted list (which may be
 * empty) is the source of truth.
 */
class DataStoreLibraryRepository(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val clock: () -> Long = ::getTimeMillis,
) : LibraryRepository {

    private val booksKey = stringPreferencesKey("books")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(LibraryBook.serializer())

    private val _books = MutableStateFlow<List<LibraryBook>>(emptyList())
    override val books: StateFlow<List<LibraryBook>> = _books.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    override val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        scope.launch {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .collect { prefs ->
                    _books.value = prefs.decodeBooks()
                    _loaded.value = true // after the books, so `loaded` never exposes the empty placeholder
                }
        }
    }

    override fun add(book: LibraryBook) = mutate { current ->
        if (current.any { it.title == book.title }) current else current + book.copy(addedAt = clock())
    }

    override fun remove(title: String) = mutate { it.filterNot { book -> book.title == title } }

    override fun updatePosition(title: String, position: ReadingPosition) = mutate { current ->
        current.map { book ->
            if (book.title != title) return@map book
            book.copy(
                lastTref = position.tref,
                lastSegment = position.segment,
                lastOffset = position.offset.coerceAtLeast(0),
                lastLabel = position.label,
                progress = position.progress.coerceIn(0f, 1f),
                lastReadAt = clock(),
            )
        }
    }

    override fun updateDetails(title: String, category: String?, heCategory: String?, heAuthor: String?) =
        mutate { current ->
            current.map { book ->
                if (book.title != title) return@map book
                book.copy(
                    category = category ?: book.category,
                    heCategory = heCategory ?: book.heCategory,
                    heAuthor = heAuthor ?: book.heAuthor,
                )
            }
        }

    override fun setOffline(title: String, offline: Boolean) = mutate { current ->
        current.map { if (it.title == title) it.copy(offline = offline) else it }
    }

    /**
     * Read‑modify‑write INSIDE the transaction → no lost updates between rapid successive writes.
     * Unchanged lists are not written back, so no‑op updates cost no disk IO.
     */
    private fun mutate(transform: (List<LibraryBook>) -> List<LibraryBook>) {
        // UNDISPATCHED: the edit is queued inside DataStore before this returns, so writes apply in
        // call order (an "add" then "undo" can never land reversed).
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            dataStore.edit { prefs ->
                val current = prefs.decodeBooks()
                val updated = transform(current)
                if (updated != current) prefs[booksKey] = json.encodeToString(serializer, updated)
            }
        }
    }

    private fun Preferences.decodeBooks(): List<LibraryBook> =
        this[booksKey]?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrNull() }
            ?: DEFAULT_BOOKS

    companion object {
        val DEFAULT_BOOKS = listOf(
            LibraryBook(
                title = "Mishneh Torah, Foundations of the Torah",
                heTitle = "משנה תורה, הלכות יסודי התורה",
                lastTref = "Mishneh_Torah,_Foundations_of_the_Torah.1",
                lastLabel = "פרק א",
                category = "Halakhah",
                heCategory = "הלכה",
                heAuthor = "רמב״ם",
            ),
            LibraryBook(
                title = "Mishneh Torah, Repentance",
                heTitle = "משנה תורה, הלכות תשובה",
                lastTref = "Mishneh_Torah,_Repentance.1",
                lastLabel = "פרק א",
                category = "Halakhah",
                heCategory = "הלכה",
                heAuthor = "רמב״ם",
            ),
        )
    }
}
