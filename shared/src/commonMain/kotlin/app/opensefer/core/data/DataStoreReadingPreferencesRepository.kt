package app.opensefer.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.opensefer.core.domain.ReadingLanguage
import app.opensefer.core.domain.ReadingPreferences
import app.opensefer.core.domain.ReadingPreferencesRepository
import app.opensefer.core.domain.ReadingTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okio.IOException

/**
 * [ReadingPreferencesRepository] backed by a Preferences DataStore (persists across restarts).
 * Setters stay fire‑and‑forget — DataStore serialises writes; reads recover from a corrupt file
 * via [catch]. Unknown enum values fall back to the default ([toEnum]) so a downgrade never crashes.
 */
class DataStoreReadingPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : ReadingPreferencesRepository {

    private object Keys {
        val fontScale = floatPreferencesKey("fontScale")
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
        val showNikud = booleanPreferencesKey("showNikud")
    }

    override val preferences: StateFlow<ReadingPreferences> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { prefs ->
                val defaults = ReadingPreferences()
                ReadingPreferences(
                    fontScale = (prefs[Keys.fontScale] ?: defaults.fontScale).coerceIn(0.8f, 2.0f),
                    theme = prefs[Keys.theme].toEnum(defaults.theme),
                    language = prefs[Keys.language].toEnum(defaults.language),
                    showNikud = prefs[Keys.showNikud] ?: defaults.showNikud,
                )
            }
            .stateIn(scope, SharingStarted.Eagerly, ReadingPreferences())

    override fun setFontScale(scale: Float) = edit { it[Keys.fontScale] = scale.coerceIn(0.8f, 2.0f) }
    override fun setTheme(theme: ReadingTheme) = edit { it[Keys.theme] = theme.name }
    override fun setLanguage(language: ReadingLanguage) = edit { it[Keys.language] = language.name }
    override fun setShowNikud(show: Boolean) = edit { it[Keys.showNikud] = show }

    private fun edit(block: (MutablePreferences) -> Unit) {
        scope.launch { dataStore.edit(block) }
    }
}

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { raw -> enumValues<T>().firstOrNull { it.name == raw } } ?: default
