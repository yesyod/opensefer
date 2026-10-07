package app.opensefer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Destinations as plain data — the "routes" of the app (BLUEPRINT §10.2). The reader's sheets are
 * NOT destinations; they are reader UI state.
 */
sealed interface Destination {
    data object Library : Destination
    data object Search : Destination
    data class Book(val title: String, val heTitle: String) : Destination

    /** Opens [bookTitle] at [startTref]/[startSegment] — or, when null, where the reader left off. */
    data class Reader(
        val bookTitle: String,
        val heBookTitle: String,
        val startTref: String? = null,
        val startSegment: Int = 0,
    ) : Destination

    data class AboutBook(val bookTitle: String, val heBookTitle: String) : Destination
    data object About : Destination
}

/**
 * One back‑stack entry: a [destination] plus the [ViewModelStore] its screen's ViewModels live in.
 * The store is cleared once the entry has left the stack and its screen has left the composition,
 * so a screen's ViewModel lives exactly as long as the screen does (the Navigation 3 `NavEntry`
 * model). [id] is a String so it can key saved state.
 */
class NavEntry(val id: String, val destination: Destination) : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

/**
 * A minimal "backstack‑as‑state" navigator: the back stack is a [SnapshotStateList] you own and
 * mutate directly — the same mental model as Navigation 3. This thin seam is exactly where the
 * Nav3 `NavDisplay` (or the stable `navigation-compose` multiplatform) drops in later with no
 * change to any screen (BLUEPRINT §10.2 + §15).
 */
class Navigator internal constructor(entries: List<Pair<String, Destination>>) {
    constructor(start: Destination = Destination.Library) : this(listOf("entry-0" to start))

    private var nextId = entries.maxOfOrNull { it.first.removePrefix(ID_PREFIX).toIntOrNull() ?: 0 }?.plus(1) ?: 0
    val backStack: SnapshotStateList<NavEntry> =
        mutableStateListOf<NavEntry>().apply { entries.forEach { (id, destination) -> add(NavEntry(id, destination)) } }

    val current: NavEntry get() = backStack.last()
    val canGoBack: Boolean get() = backStack.size > 1

    /** Pushes [destination] — unless it's already on top (a double tap must not open a screen twice). */
    fun goTo(destination: Destination) {
        if (backStack.lastOrNull()?.destination == destination) return
        backStack.add(NavEntry("$ID_PREFIX${nextId++}", destination))
    }

    /**
     * Pops the top entry. Its ViewModels are released by the host once the screen has finished
     * animating out — clearing them here would let the leaving screen recreate them mid‑animation.
     */
    fun back(): Boolean {
        if (!canGoBack) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    /** Pops everything above the start destination (e.g. "back to the library"). */
    fun backToRoot() {
        // Entries under the top one aren't on screen, so nothing will release them later: do it now.
        while (backStack.size > 2) backStack.removeAt(backStack.lastIndex - 1).viewModelStore.clear()
        back()
    }

    /** Releases every entry's ViewModels — when the whole UI goes away. */
    fun clearAll() {
        backStack.forEach { it.viewModelStore.clear() }
    }

    internal fun saveable(): List<String> =
        backStack.flatMap { entry -> listOf(entry.id) + entry.destination.encode() + END }

    internal companion object {
        const val ID_PREFIX = "entry-"
        const val END = "\u0000"

        fun restore(saved: List<String>): Navigator {
            val entries = saved.chunkedBy(END).mapNotNull { chunk ->
                val destination = chunk.drop(1).decodeDestination() ?: return@mapNotNull null
                chunk.first() to destination
            }
            return if (entries.isEmpty()) Navigator() else Navigator(entries)
        }
    }
}

/**
 * A [Navigator] whose back stack survives process death (Android may kill the app in the
 * background): destinations are saved as plain string lists and rebuilt on restore.
 */
@Composable
fun rememberNavigator(): Navigator = rememberSaveable(saver = NavigatorSaver) { Navigator() }

private val NavigatorSaver = listSaver<Navigator, String>(
    save = { navigator -> navigator.saveable() },
    restore = { saved -> Navigator.restore(saved) },
)

private fun Destination.encode(): List<String> = when (this) {
    Destination.Library -> listOf("library")
    Destination.Search -> listOf("search")
    Destination.About -> listOf("about")
    is Destination.Book -> listOf("book", title, heTitle)
    is Destination.Reader -> listOf("reader", bookTitle, heBookTitle, startTref.orEmpty(), startSegment.toString())
    is Destination.AboutBook -> listOf("aboutBook", bookTitle, heBookTitle)
}

private fun List<String>.decodeDestination(): Destination? = when (firstOrNull()) {
    "library" -> Destination.Library
    "search" -> Destination.Search
    "about" -> Destination.About
    "book" -> if (size == 3) Destination.Book(this[1], this[2]) else null
    "reader" -> if (size == 5) {
        Destination.Reader(this[1], this[2], this[3].ifEmpty { null }, this[4].toIntOrNull() ?: 0)
    } else {
        null
    }
    "aboutBook" -> if (size == 3) Destination.AboutBook(this[1], this[2]) else null
    else -> null
}

private fun List<String>.chunkedBy(separator: String): List<List<String>> {
    val out = mutableListOf<List<String>>()
    var current = mutableListOf<String>()
    for (item in this) {
        if (item == separator) {
            out += current
            current = mutableListOf()
        } else {
            current += item
        }
    }
    return out
}
