package app.opensefer.core.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.opensefer.core.data.DataStoreBookmarkRepository
import app.opensefer.core.data.DataStoreLibraryRepository
import app.opensefer.core.data.DataStoreReadingPreferencesRepository
import app.opensefer.core.data.DefaultSearchRepository
import app.opensefer.core.data.DefaultTextRepository
import app.opensefer.core.data.DiskCache
import app.opensefer.core.data.SectionCache
import app.opensefer.core.data.createPreferencesDataStore
import app.opensefer.core.data.diskCacheDir
import app.opensefer.core.domain.BookDownloader
import app.opensefer.core.domain.BookmarkRepository
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.domain.OfflineStorage
import app.opensefer.core.domain.ReadingPreferencesRepository
import app.opensefer.core.domain.SearchRepository
import app.opensefer.core.domain.TextRepository
import app.opensefer.core.network.SefariaApi
import app.opensefer.core.network.createHttpClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Koin definitions for the shared layer (network → data → domain). UI modules add ViewModels. */
val sharedModule = module {
    single { createHttpClient() }
    single { SefariaApi(get()) }
    single { SectionCache() }
    // Versioned root: a future change to the stored JSON shape just bumps the folder.
    single { DiskCache(diskCacheDir().toPath() / TEXT_CACHE_DIR / TEXT_CACHE_VERSION) }

    // Application‑lifetime scope for the persistence StateFlows, fire‑and‑forget writes, shared
    // in‑flight requests and offline downloads. A background failure (a full disk during a write)
    // must never take the app down, so uncaught errors are logged and dropped.
    single<CoroutineScope> {
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                CoroutineExceptionHandler { _, error -> println("OpenSefer: background task failed: $error") },
        )
    }

    // One DataStore instance per file (named singletons enforce DataStore's single‑owner rule).
    single<DataStore<Preferences>>(named(READING_PREFS_STORE)) { createPreferencesDataStore(READING_PREFS_STORE) }
    single<DataStore<Preferences>>(named(LIBRARY_STORE)) { createPreferencesDataStore(LIBRARY_STORE) }
    single<DataStore<Preferences>>(named(BOOKMARKS_STORE)) { createPreferencesDataStore(BOOKMARKS_STORE) }

    single { DefaultTextRepository(api = get(), memory = get(), disk = get(), scope = get()) }
    single<TextRepository> { get<DefaultTextRepository>() }
    single<OfflineStorage> { get<DefaultTextRepository>() } // it knows which stored files are whose
    single<SearchRepository> { DefaultSearchRepository(get()) }
    single<LibraryRepository> { DataStoreLibraryRepository(get(named(LIBRARY_STORE)), get()) }
    single<BookmarkRepository> { DataStoreBookmarkRepository(get(named(BOOKMARKS_STORE)), get()) }
    single<ReadingPreferencesRepository> {
        DataStoreReadingPreferencesRepository(get(named(READING_PREFS_STORE)), get())
    }
    single { BookDownloader(text = get(), library = get(), scope = get(), storage = get()) }
}

private const val READING_PREFS_STORE = "reading_prefs"
private const val LIBRARY_STORE = "library"
private const val BOOKMARKS_STORE = "bookmarks"
private const val TEXT_CACHE_DIR = "sefaria"
private const val TEXT_CACHE_VERSION = "v1"
