package app.opensefer.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.opensefer.core.model.LibraryBook
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
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreLibraryRepositoryTest {

    private val path: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "lib-${Random.nextLong()}.preferences_pb"

    @AfterTest
    fun cleanup() {
        FileSystem.SYSTEM.delete(path, mustExist = false)
    }

    // A DataStore on a caller-controlled scope so the persistence test can release the file
    // (one-instance-per-file) before opening a fresh instance.
    private fun TestScope.storeOn(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { path })

    @Test
    fun firstRun_showsSeededDefaults() = runTest {
        val repo = DataStoreLibraryRepository(storeOn(backgroundScope), backgroundScope)
        assertEquals(DataStoreLibraryRepository.DEFAULT_BOOKS, repo.books.first())
    }

    @Test
    fun addRemovePosition_persistAcrossAFreshInstance() = runTest {
        val scopeA = CoroutineScope(coroutineContext + Job())
        val repoA = DataStoreLibraryRepository(storeOn(scopeA), scopeA)
        repoA.add(LibraryBook("Pirkei Avot", "פרקי אבות"))
        repoA.remove("Mishneh Torah, Repentance")
        repoA.updatePosition("Mishneh Torah, Foundations of the Torah", "Genesis.5", "פרק ה")
        advanceUntilIdle()
        val afterA = repoA.books.first { list -> list.any { it.title == "Pirkei Avot" } }
        scopeA.cancel() // release the file
        advanceUntilIdle()

        // Fresh instance on the same file = simulated app restart.
        val repoB = DataStoreLibraryRepository(storeOn(backgroundScope), backgroundScope)
        val reloaded = repoB.books.first { list -> list.any { it.title == "Pirkei Avot" } }

        assertEquals(afterA.map { it.title }.toSet(), reloaded.map { it.title }.toSet())
        assertTrue(reloaded.none { it.title == "Mishneh Torah, Repentance" })
        assertEquals("Genesis.5", reloaded.first { it.title == "Mishneh Torah, Foundations of the Torah" }.lastTref)
    }
}
