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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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

    private val _preferences = MutableStateFlow(ReadingPreferences())
    override val preferences: StateFlow<ReadingPreferences> = _preferences.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    override val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        scope.launch {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .collect { prefs ->
                    _preferences.value = prefs.toReadingPreferences()
                    _loaded.value = true // after the value, so the first frame already has the real theme
                }
        }
    }

    override fun setFontScale(scale: Float) = edit { it[Keys.fontScale] = scale.coerceIn(MIN_SCALE, MAX_SCALE) }
    override fun setTheme(theme: ReadingTheme) = edit { it[Keys.theme] = theme.name }
    override fun setLanguage(language: ReadingLanguage) = edit { it[Keys.language] = language.name }
    override fun setShowNikud(show: Boolean) = edit { it[Keys.showNikud] = show }

    private fun Preferences.toReadingPreferences(): ReadingPreferences {
        val defaults = ReadingPreferences()
        return ReadingPreferences(
            fontScale = (this[Keys.fontScale] ?: defaults.fontScale).coerceIn(MIN_SCALE, MAX_SCALE),
            theme = this[Keys.theme].toEnum(defaults.theme),
            language = this[Keys.language].toEnum(defaults.language),
            showNikud = this[Keys.showNikud] ?: defaults.showNikud,
        )
    }

    private fun edit(block: (MutablePreferences) -> Unit) {
        // UNDISPATCHED keeps rapid successive writes (a slider drag) in call order.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { dataStore.edit(block) }
    }

    private companion object {
        const val MIN_SCALE = 0.8f
        const val MAX_SCALE = 2.0f
    }
}

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { raw -> enumValues<T>().firstOrNull { it.name == raw } } ?: default
