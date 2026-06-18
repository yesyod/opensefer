package app.opensefer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Destinations as plain serializable‑friendly data — the "routes" of the app (BLUEPRINT §10.2).
 * The three reader sheets are NOT destinations; they are reader UI state.
 */
sealed interface Destination {
    data object Library : Destination
    data object Search : Destination
    data class BookToc(val title: String, val heTitle: String) : Destination
    data class Reader(val bookTitle: String, val heBookTitle: String, val startTref: String?) : Destination
    data class AboutBook(val bookTitle: String, val heBookTitle: String) : Destination
    data object About : Destination
}

/**
 * A minimal "backstack‑as‑state" navigator: the back stack is a [SnapshotStateList] you own and
 * mutate directly — the same mental model as Navigation 3. This thin seam is exactly where the
 * Nav3 `NavDisplay` (or the stable `navigation-compose` multiplatform) drops in later with no
 * change to any screen (BLUEPRINT §10.2 + §15).
 */
class Navigator(start: Destination) {
    val backStack: SnapshotStateList<Destination> = mutableStateListOf(start)

    val current: Destination get() = backStack.last()
    val canGoBack: Boolean get() = backStack.size > 1

    fun goTo(destination: Destination) {
        backStack.add(destination)
    }

    /** Pops to the existing instance of [destination] if present, else navigates fresh. */
    fun goToSingleTop(destination: Destination) {
        val existing = backStack.indexOfLast { it == destination }
        if (existing >= 0) {
            while (backStack.lastIndex > existing) backStack.removeAt(backStack.lastIndex)
        } else {
            goTo(destination)
        }
    }

    fun back(): Boolean {
        if (!canGoBack) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}

@Composable
fun rememberNavigator(start: Destination = Destination.Library): Navigator =
    remember { Navigator(start) }
