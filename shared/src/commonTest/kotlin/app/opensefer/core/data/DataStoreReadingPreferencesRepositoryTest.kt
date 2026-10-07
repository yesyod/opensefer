package app.opensefer.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.opensefer.core.domain.ReadingLanguage
import app.opensefer.core.domain.ReadingPreferences
import app.opensefer.core.domain.ReadingTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreReadingPreferencesRepositoryTest {

    private val path: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "prefs-${Random.nextLong()}.preferences_pb"

    @AfterTest
    fun cleanup() {
        FileSystem.SYSTEM.delete(path, mustExist = false)
    }

    private fun TestScope.storeOn(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { path })

    @Test
    fun firstRun_returnsDefaults() = runTest {
        val repo = DataStoreReadingPreferencesRepository(storeOn(backgroundScope), backgroundScope)
        assertEquals(ReadingPreferences(), repo.preferences.first())
    }

    @Test
    fun theDefaults_areHebrew_andFollowTheDevicesLightOrDark() {
        assertEquals(ReadingLanguage.Hebrew, ReadingPreferences().language)
        assertEquals(ReadingTheme.System, ReadingPreferences().theme)
    }

    @Test
    fun nikud_togglesAgainstTheStoredValue_soTwoQuickTapsCancelOut() = runTest {
        val repo = DataStoreReadingPreferencesRepository(storeOn(backgroundScope), backgroundScope)
        repo.loaded.first { it }

        repo.toggleShowNikud()
        repo.toggleShowNikud() // before the first write is even visible
        advanceUntilIdle()

        assertTrue(repo.preferences.value.showNikud)
    }

    @Test
    fun changedPreferences_persistAcrossAFreshInstance() = runTest {
        val scopeA = CoroutineScope(coroutineContext + Job())
        val repoA = DataStoreReadingPreferencesRepository(storeOn(scopeA), scopeA)
        repoA.setTheme(ReadingTheme.Dark)
        repoA.setFontScale(1.5f)
        repoA.setLanguage(ReadingLanguage.Bilingual)
        repoA.setShowNikud(false)
        advanceUntilIdle()
        repoA.preferences.first { it.theme == ReadingTheme.Dark }
        scopeA.cancel()
        advanceUntilIdle()

        val repoB = DataStoreReadingPreferencesRepository(storeOn(backgroundScope), backgroundScope)
        val loaded = repoB.preferences.first { it.theme == ReadingTheme.Dark }

        assertEquals(1.5f, loaded.fontScale)
        assertEquals(ReadingLanguage.Bilingual, loaded.language)
        assertEquals(false, loaded.showNikud)
    }
}
