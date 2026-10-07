package app.opensefer.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.opensefer.core.domain.BookmarkRepository
import app.opensefer.core.model.Bookmark
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * [BookmarkRepository] backed by its own Preferences DataStore file (`bookmarks`) — a JSON list,
 * newest first, at most one bookmark per segment. Same transactional read‑modify‑write as the library.
 */
class DataStoreBookmarkRepository(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val clock: () -> Long = ::getTimeMillis,
) : BookmarkRepository {

    private val key = stringPreferencesKey("bookmarks")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Bookmark.serializer())

    override val bookmarks: StateFlow<List<Bookmark>> =
        dataStore.resilientData()
            .map { it.decode() }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Adds [bookmark]; one that keeps its [Bookmark.createdAt] (an undo) returns to its old place in the list. */
    override fun add(bookmark: Bookmark) = mutate { current ->
        val stamped = if (bookmark.createdAt == 0L) bookmark.copy(createdAt = clock()) else bookmark
        (listOf(stamped) + current.filterNot { it.id == bookmark.id }).sortedByDescending { it.createdAt }
    }

    override fun remove(id: String) = mutate { current -> current.filterNot { it.id == id } }

    private fun mutate(transform: (List<Bookmark>) -> List<Bookmark>) {
        // UNDISPATCHED: the edit is queued inside DataStore before this returns, so writes apply in
        // call order (an "add" then "undo" can never land reversed).
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            dataStore.edit { prefs -> prefs[key] = json.encodeToString(serializer, transform(prefs.decode())) }
        }
    }

    private fun Preferences.decode(): List<Bookmark> =
        this[key]?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrNull() }.orEmpty()
}
