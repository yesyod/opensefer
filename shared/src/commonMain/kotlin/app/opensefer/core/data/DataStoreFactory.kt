package app.opensefer.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry
import okio.Path.Companion.toPath

/** Platform persistence directory: `Context.filesDir` on Android, `NSDocumentDirectory` on iOS. */
internal expect fun dataStoreDir(): String

/**
 * Builds a Preferences [DataStore] at `<dataStoreDir>/<name>.preferences_pb`. A corrupt file is
 * replaced by an empty one instead of failing every read and write from then on.
 *
 * IMPORTANT: DataStore permits exactly one instance per file per process — every caller must hold
 * the result as a singleton. We do, via named Koin singletons in [app.opensefer.core.di.sharedModule].
 */
internal fun createPreferencesDataStore(name: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { "${dataStoreDir()}/$name.preferences_pb".toPath() },
    )

/**
 * [DataStore.data] made safe to drive the app's state: a failed read is retried a few times, and if
 * it keeps failing the defaults are used — so a bad file can never leave the app waiting forever.
 */
internal fun DataStore<Preferences>.resilientData(): Flow<Preferences> =
    data
        .retry(READ_RETRIES) {
            delay(READ_RETRY_DELAY_MS)
            true
        }
        .catch { emit(emptyPreferences()) }

private const val READ_RETRIES = 3L
private const val READ_RETRY_DELAY_MS = 300L
