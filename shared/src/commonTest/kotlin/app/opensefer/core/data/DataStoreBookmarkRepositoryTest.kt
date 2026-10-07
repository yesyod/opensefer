package app.opensefer.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.opensefer.core.model.Bookmark
import app.opensefer.core.model.bookmarkId
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
class DataStoreBookmarkRepositoryTest {

    private val path: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "bookmarks-${Random.nextLong()}.preferences_pb"

    @AfterTest
    fun cleanup() {
        FileSystem.SYSTEM.delete(path, mustExist = false)
    }

    private fun TestScope.storeOn(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { path })

    private fun mark(tref: String, segment: Int, label: String = "x") =
        Bookmark("Genesis", "בראשית", tref, segment, label, snippet = "בראשית ברא")

    @Test
    fun bookmarks_persistAcrossAFreshInstance_newestFirst() = runTest {
        val scopeA = CoroutineScope(coroutineContext + Job())
        var now = 1L
        val repoA = DataStoreBookmarkRepository(storeOn(scopeA), scopeA, clock = { now++ })
        repoA.add(mark("Genesis.1", 0))
        repoA.add(mark("Genesis.2", 4))
        advanceUntilIdle()
        repoA.bookmarks.first { it.size == 2 }
        scopeA.cancel()
        advanceUntilIdle()

        val repoB = DataStoreBookmarkRepository(storeOn(backgroundScope), backgroundScope)
        val reloaded = repoB.bookmarks.first { it.size == 2 }

        assertEquals(listOf(bookmarkId("Genesis.2", 4), bookmarkId("Genesis.1", 0)), reloaded.map { it.id })
        assertTrue(reloaded.all { it.createdAt > 0 })
    }

    @Test
    fun bookmarkingTheSameSegmentAgain_replacesIt_andRemoveDeletesIt() = runTest {
        val repo = DataStoreBookmarkRepository(storeOn(backgroundScope), backgroundScope)
        repo.add(mark("Genesis.1", 0, label = "old"))
        repo.add(mark("Genesis.1", 0, label = "new"))
        repo.add(mark("Genesis.1", 1))

        val two = repo.bookmarks.first { it.size == 2 && it.any { b -> b.label == "new" } }
        assertEquals(1, two.count { it.id == bookmarkId("Genesis.1", 0) })

        repo.remove(bookmarkId("Genesis.1", 0))
        val one = repo.bookmarks.first { it.size == 1 }
        assertEquals(bookmarkId("Genesis.1", 1), one.single().id)
    }

    @Test
    fun aRemovedBookmarkPutBack_returnsToItsPlaceInTheList() = runTest {
        var now = 1L
        val repo = DataStoreBookmarkRepository(storeOn(backgroundScope), backgroundScope, clock = { now++ })
        repo.add(mark("Genesis.1", 0))
        repo.add(mark("Genesis.2", 0))
        repo.add(mark("Genesis.3", 0))
        val three = repo.bookmarks.first { it.size == 3 }

        val middle = three[1]
        repo.remove(middle.id)
        repo.bookmarks.first { it.size == 2 }
        repo.add(middle) // the undo: it still has its createdAt

        assertEquals(three, repo.bookmarks.first { it.size == 3 })
    }
}
