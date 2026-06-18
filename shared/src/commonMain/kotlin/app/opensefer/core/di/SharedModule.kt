package app.opensefer.core.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.opensefer.core.data.DataStoreLibraryRepository
import app.opensefer.core.data.DataStoreReadingPreferencesRepository
import app.opensefer.core.data.DefaultSearchRepository
import app.opensefer.core.data.DefaultTextRepository
import app.opensefer.core.data.SectionCache
import app.opensefer.core.data.createPreferencesDataStore
import app.opensefer.core.domain.GetChapterUseCase
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.domain.ReadingPreferencesRepository
import app.opensefer.core.domain.SearchRepository
import app.opensefer.core.domain.TextRepository
import app.opensefer.core.network.SefariaApi
import app.opensefer.core.network.createHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Koin definitions for the shared layer (network → data → domain). UI modules add ViewModels. */
val sharedModule = module {
    single { createHttpClient() }
    single { SefariaApi(get()) }
    single { SectionCache() }

    // Application‑lifetime scope for the persistence StateFlows + fire‑and‑forget writes.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // One DataStore instance per file (named singletons enforce DataStore's single‑owner rule).
    single<DataStore<Preferences>>(named(READING_PREFS_STORE)) { createPreferencesDataStore(READING_PREFS_STORE) }
    single<DataStore<Preferences>>(named(LIBRARY_STORE)) { createPreferencesDataStore(LIBRARY_STORE) }

    single<TextRepository> { DefaultTextRepository(get(), get()) }
    single<SearchRepository> { DefaultSearchRepository(get()) }
    single<LibraryRepository> { DataStoreLibraryRepository(get(named(LIBRARY_STORE)), get()) }
    single<ReadingPreferencesRepository> {
        DataStoreReadingPreferencesRepository(get(named(READING_PREFS_STORE)), get())
    }

    factory { GetChapterUseCase(get()) }
}

private const val READING_PREFS_STORE = "reading_prefs"
private const val LIBRARY_STORE = "library"
