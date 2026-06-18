package app.opensefer

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.opensefer.core.di.sharedModule
import app.opensefer.core.domain.ReadingPreferencesRepository
import app.opensefer.ui.about.AboutBookScreen
import app.opensefer.ui.about.AboutScreen
import app.opensefer.ui.library.LibraryScreen
import app.opensefer.ui.navigation.Destination
import app.opensefer.ui.navigation.PlatformBackHandler
import app.opensefer.ui.navigation.rememberNavigator
import app.opensefer.ui.reader.ReaderScreen
import app.opensefer.ui.search.SearchScreen
import app.opensefer.ui.theme.OpenSeferTheme
import app.opensefer.ui.toc.BookTocScreen
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

/**
 * Root composable — the single shared UI entry point for Android ([MainActivity]) and
 * iOS ([MainViewController]). Starts Koin, applies the reactive reading theme, hosts navigation.
 */
@Composable
fun App() {
    KoinApplication(application = { modules(sharedModule) }) {
        val preferencesRepository = koinInject<ReadingPreferencesRepository>()
        val preferences by preferencesRepository.preferences.collectAsState()

        OpenSeferTheme(theme = preferences.theme, fontScale = preferences.fontScale) {
            Surface(color = MaterialTheme.colorScheme.background) {
                // Full-screen/immersive, but keep top bars clear of a display cutout (notch / punch-hole).
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
                    AppNavHost()
                }
            }
        }
    }
}

@Composable
private fun AppNavHost() {
    val navigator = rememberNavigator(Destination.Library)
    PlatformBackHandler(enabled = navigator.canGoBack) { navigator.back() }

    when (val destination = navigator.current) {
        Destination.Library -> LibraryScreen(
            // Tapping a book opens the continuous reader (resuming where they left off, if anywhere).
            onContinueReading = { book ->
                navigator.goTo(Destination.Reader(book.title, book.heTitle, book.lastTref))
            },
            onBrowse = { book -> navigator.goTo(Destination.BookToc(book.title, book.heTitle)) },
            onAddBook = { navigator.goTo(Destination.Search) },
            onAbout = { navigator.goTo(Destination.About) },
        )

        Destination.Search -> SearchScreen(
            onBack = { navigator.back() },
            onPick = { result -> navigator.goTo(Destination.BookToc(result.title, result.heTitle)) },
        )

        is Destination.BookToc -> BookTocScreen(
            title = destination.title,
            heTitle = destination.heTitle,
            onBack = { navigator.back() },
            onOpen = { tref ->
                navigator.goTo(Destination.Reader(destination.title, destination.heTitle, tref))
            },
        )

        is Destination.Reader -> ReaderScreen(
            bookTitle = destination.bookTitle,
            heBookTitle = destination.heBookTitle,
            startTref = destination.startTref,
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
