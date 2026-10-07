package app.opensefer.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.opensefer.core.model.LibraryBook
import app.opensefer.core.model.ReadingPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.SYSTEM
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
    fun firstRun_showsSeededDefaults_onceLoaded() = runTest {
        val repo = DataStoreLibraryRepository(storeOn(backgroundScope), backgroundScope)
        repo.loaded.first { it }
        assertEquals(DataStoreLibraryRepository.DEFAULT_BOOKS, repo.books.value)
    }

    @Test
    fun addRemovePosition_persistAcrossAFreshInstance() = runTest {
        val scopeA = CoroutineScope(coroutineContext + Job())
        val repoA = DataStoreLibraryRepository(storeOn(scopeA), scopeA, clock = { 42L })
        repoA.add(LibraryBook("Pirkei Avot", "פרקי אבות"))
        repoA.remove("Mishneh Torah, Repentance")
        repoA.updatePosition(
            "Mishneh Torah, Foundations of the Torah",
            ReadingPosition(tref = "Genesis.5", segment = 3, label = "פרק ה, ד", progress = 0.5f),
        )
        advanceUntilIdle()
        val afterA = repoA.books.first { list -> list.any { it.title == "Pirkei Avot" } }
        scopeA.cancel() // release the file
        advanceUntilIdle()

        // Fresh instance on the same file = simulated app restart.
        val repoB = DataStoreLibraryRepository(storeOn(backgroundScope), backgroundScope)
        val reloaded = repoB.books.first { list -> list.any { it.title == "Pirkei Avot" } }

        assertEquals(afterA.map { it.title }.toSet(), reloaded.map { it.title }.toSet())
        assertTrue(reloaded.none { it.title == "Mishneh Torah, Repentance" })
        val resumed = reloaded.first { it.title == "Mishneh Torah, Foundations of the Torah" }
        assertEquals("Genesis.5", resumed.lastTref)
        assertEquals(3, resumed.lastSegment) // exact resume: down to the segment
        assertEquals(0.5f, resumed.progress)
        assertEquals(42L, resumed.lastReadAt)
        assertEquals(42L, reloaded.first { it.title == "Pirkei Avot" }.addedAt)
    }

    @Test
    fun detailsAndOfflineFlag_areStoredOnTheBook() = runTest {
        val repo = DataStoreLibraryRepository(storeOn(backgroundScope), backgroundScope)
        repo.add(LibraryBook("Berakhot", "ברכות"))
        repo.updateDetails("Berakhot", category = "Talmud", heCategory = "תלמוד", heAuthor = null)
        repo.setOffline("Berakhot", true)

        val book = repo.books.first { list -> list.any { it.title == "Berakhot" && it.offline } }
            .first { it.title == "Berakhot" }
        assertEquals("Talmud", book.category)
        assertEquals("תלמוד", book.heCategory)
    }

    @Test
    fun aBookPutBack_keepsItsPlaceOnTheShelf() = runTest {
        val repo = DataStoreLibraryRepository(storeOn(backgroundScope), backgroundScope, clock = { 99L })
        repo.add(LibraryBook("Berakhot", "ברכות", addedAt = 5L)) // an undo hands back the original

        val book = repo.books.first { list -> list.any { it.title == "Berakhot" } }.first { it.title == "Berakhot" }
        assertEquals(5L, book.addedAt)
    }

    @Test
    fun anUnreadableLibrary_stillLoads_withTheDefaults_insteadOfLeavingTheAppBlank() = runTest {
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("corrupt file") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                throw IOException("corrupt file")
        }
        val repo = DataStoreLibraryRepository(broken, backgroundScope)

        repo.loaded.first { it }
        assertEquals(DataStoreLibraryRepository.DEFAULT_BOOKS, repo.books.value)
    }

    @Test
    fun addingTheSameBookTwice_keepsOneCopy() = runTest {
        val repo = DataStoreLibraryRepository(storeOn(backgroundScope), backgroundScope)
        repo.add(LibraryBook("Berakhot", "ברכות"))
        repo.add(LibraryBook("Berakhot", "ברכות"))
        advanceUntilIdle()

        assertEquals(1, repo.books.first { list -> list.any { it.title == "Berakhot" } }.count { it.title == "Berakhot" })
    }
}
