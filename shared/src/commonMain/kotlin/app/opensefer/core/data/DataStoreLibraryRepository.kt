package app.opensefer.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.model.LibraryBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okio.IOException

/**
 * [LibraryRepository] backed by a Preferences DataStore — the user's books and resume positions,
 * stored as a JSON list under one key. The library shows [DEFAULT_BOOKS] until the user first
 * changes it; from then on the persisted list (which may be empty) is the source of truth.
 */
class DataStoreLibraryRepository(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : LibraryRepository {

    private val booksKey = stringPreferencesKey("books")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(LibraryBook.serializer())

    override val books: StateFlow<List<LibraryBook>> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it.decodeBooks() }
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_BOOKS)

    override fun add(book: LibraryBook) = mutate { current ->
        if (current.any { it.title == book.title }) current else current + book
    }

    override fun remove(title: String) = mutate { it.filterNot { book -> book.title == title } }

    override fun updatePosition(title: String, tref: String, label: String) = mutate { current ->
        current.map { if (it.title == title) it.copy(lastTref = tref, lastLabel = label) else it }
    }

    /** Read‑modify‑write INSIDE the transaction → no lost updates between rapid successive writes. */
    private fun mutate(transform: (List<LibraryBook>) -> List<LibraryBook>) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[booksKey] = json.encodeToString(serializer, transform(prefs.decodeBooks()))
            }
        }
    }

    private fun Preferences.decodeBooks(): List<LibraryBook> =
        this[booksKey]?.let { json.decodeFromString(serializer, it) } ?: DEFAULT_BOOKS

    companion object {
        val DEFAULT_BOOKS = listOf(
            LibraryBook(
                title = "Mishneh Torah, Foundations of the Torah",
                heTitle = "משנה תורה, הלכות יסודי התורה",
                lastTref = "Mishneh_Torah,_Foundations_of_the_Torah.1",
                lastLabel = "פרק א",
            ),
            LibraryBook(
                title = "Mishneh Torah, Repentance",
                heTitle = "משנה תורה, הלכות תשובה",
                lastTref = "Mishneh_Torah,_Repentance.1",
                lastLabel = "פרק א",
            ),
        )
    }
}
