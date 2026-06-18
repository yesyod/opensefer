package app.opensefer.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

/** Platform persistence directory: `Context.filesDir` on Android, `NSDocumentDirectory` on iOS. */
internal expect fun dataStoreDir(): String

/**
 * Builds a Preferences [DataStore] at `<dataStoreDir>/<name>.preferences_pb`.
 *
 * IMPORTANT: DataStore permits exactly one instance per file per process — every caller must hold
 * the result as a singleton. We do, via named Koin singletons in [app.opensefer.core.di.sharedModule].
 */
internal fun createPreferencesDataStore(name: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { "${dataStoreDir()}/$name.preferences_pb".toPath() },
    )
