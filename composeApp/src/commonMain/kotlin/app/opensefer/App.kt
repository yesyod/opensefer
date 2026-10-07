package app.opensefer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import app.opensefer.core.di.sharedModule
import app.opensefer.core.domain.BookDownloader
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.domain.ReadingPreferencesRepository
import app.opensefer.ui.about.AboutBookScreen
import app.opensefer.ui.about.AboutScreen
import app.opensefer.ui.book.BookScreen
import app.opensefer.ui.library.LibraryScreen
import app.opensefer.ui.navigation.Destination
import app.opensefer.ui.navigation.Navigator
import app.opensefer.ui.navigation.PlatformBackHandler
import app.opensefer.ui.navigation.rememberNavigator
import app.opensefer.ui.reader.ReaderScreen
import app.opensefer.ui.search.SearchScreen
import app.opensefer.ui.theme.LocalReadingColors
import app.opensefer.ui.theme.OpenSeferTheme
import app.opensefer.ui.theme.SystemBarsEffect
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.koinInject
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication

/**
 * The app's single dependency graph, created once per **process**. (Koin's `KoinApplication`
 * composable builds a graph per composition and stops it when the composition ends — after an
 * Activity was recreated that opened a second DataStore on the same file, which crashes.)
 */
internal object AppGraph {
    val koin: KoinApplication by lazy { koinApplication { modules(sharedModule) } }
}

/**
 * Root composable — the single shared UI entry point for Android ([MainActivity]) and
 * iOS ([MainViewController]). Applies the reactive reading theme and hosts navigation.
 */
@Composable
fun App() {
    KoinIsolatedContext(AppGraph.koin) {
        val preferencesRepository = koinInject<ReadingPreferencesRepository>()
        val libraryRepository = koinInject<LibraryRepository>()
        val downloader = koinInject<BookDownloader>()
        val preferences by preferencesRepository.preferences.collectAsState()
        val preferencesLoaded by preferencesRepository.loaded.collectAsState()
        val libraryLoaded by libraryRepository.loaded.collectAsState()
        LaunchedEffect(Unit) { downloader.startAutoDownloads() }

        OpenSeferTheme(theme = preferences.theme, fontScale = preferences.fontScale) {
            SystemBarsEffect(darkIcons = !LocalReadingColors.current.isDark)
            Surface(color = MaterialTheme.colorScheme.background) {
                // The saved theme and library are read in a few milliseconds; until then only the
                // background is drawn, so the first real frame is already right — no flash of the
                // light theme or of the default books.
                if (preferencesLoaded && libraryLoaded) {
                    // Hebrew‑first chrome: right‑to‑left everywhere (the reader switches to LTR for English).
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                                ),
                        ) {
                            AppNavHost()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shows the top of the back stack. Each entry gets its own [androidx.lifecycle.ViewModelStore] and
 * saved‑state slot, so a screen's ViewModel and scroll position live exactly as long as the entry:
 * returning to a screen finds it as it was, and a popped screen releases everything.
 */
@Composable
private fun AppNavHost() {
    val navigator = rememberNavigator()
    val savedState = rememberSaveableStateHolder()
    PlatformBackHandler(enabled = navigator.canGoBack) { navigator.back() }
    DisposableEffect(navigator) { onDispose { navigator.clearAll() } }

    AnimatedContent(
        targetState = navigator.current,
        contentKey = { it.id },
        transitionSpec = {
            // Pushing keeps the old entry on the stack; popping removes it. In RTL, "forward" comes from the left.
            val forward = initialState in navigator.backStack
            val shift = { width: Int -> (if (forward) -width else width) / SLIDE_FRACTION }
            (fadeIn(tween(ENTER_MS)) + slideInHorizontally(tween(ENTER_MS), shift)) togetherWith
                (fadeOut(tween(EXIT_MS)) + slideOutHorizontally(tween(EXIT_MS)) { -shift(it) })
        },
        label = "navigation",
    ) { entry ->
        savedState.SaveableStateProvider(entry.id) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides entry) {
                Screen(entry.destination, navigator)
            }
        }
        // Released only once the screen has finished leaving (not mid‑animation), and only if popped.
        DisposableEffect(entry) {
            onDispose {
                if (entry !in navigator.backStack) {
                    entry.viewModelStore.clear()
                    savedState.removeState(entry.id)
                }
            }
        }
    }
}

private const val ENTER_MS = 220
private const val EXIT_MS = 160
private const val SLIDE_FRACTION = 10

@Composable
private fun Screen(destination: Destination, navigator: Navigator) {
    when (destination) {
        Destination.Library -> LibraryScreen(
            // Tapping a book opens the continuous reader where the user left off.
            onContinueReading = { book -> navigator.goTo(Destination.Reader(book.title, book.heTitle)) },
            onOpenBookPage = { book -> navigator.goTo(Destination.Book(book.title, book.heTitle)) },
            onOpenBookmark = { mark ->
                navigator.goTo(Destination.Reader(mark.bookTitle, mark.heBookTitle, mark.tref, mark.segment))
            },
            onAddBook = { navigator.goTo(Destination.Search) },
            onAbout = { navigator.goTo(Destination.About) },
        )

        Destination.Search -> SearchScreen(
            onBack = { navigator.back() },
            onPick = { result -> navigator.goTo(Destination.Book(result.title, result.heTitle)) },
        )

        is Destination.Book -> BookScreen(
            title = destination.title,
            heTitle = destination.heTitle,
            onBack = { navigator.back() },
            onRead = { tref -> navigator.goTo(Destination.Reader(destination.title, destination.heTitle, tref)) },
            onAbout = { navigator.goTo(Destination.AboutBook(destination.title, destination.heTitle)) },
        )

        is Destination.Reader -> ReaderScreen(
            destination = destination,
            onBack = { navigator.back() },
            onAboutBook = {
                navigator.goTo(Destination.AboutBook(destination.bookTitle, destination.heBookTitle))
            },
        )

        is Destination.AboutBook -> AboutBookScreen(
            bookTitle = destination.bookTitle,
            heBookTitle = destination.heBookTitle,
            onBack = { navigator.back() },
        )

        Destination.About -> AboutScreen(onBack = { navigator.back() })
    }
}
