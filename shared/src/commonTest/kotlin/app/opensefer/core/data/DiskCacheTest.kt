package app.opensefer.core.data

import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiskCacheTest {

    private val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "disk-cache-${Random.nextLong().toULong()}"

    @AfterTest
    fun cleanup() {
        FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
    }

    @Test
    fun writtenValue_readsBack_evenFromAFreshInstance() = runTest {
        DiskCache(root).write("texts", "Genesis.1", """{"ref":"Genesis 1"}""")

        val fresh = DiskCache(root) // simulated app restart
        assertEquals("""{"ref":"Genesis 1"}""", fresh.read("texts", "Genesis.1"))
        assertTrue(fresh.contains("texts", "Genesis.1"))
    }

    @Test
    fun missingKey_isNull_andNamespacesDoNotCollide() = runTest {
        val cache = DiskCache(root)
        cache.write("texts", "Genesis.1", "text")

        assertNull(cache.read("texts", "Genesis.2"))
        assertNull(cache.read("index", "Genesis.1"))
        assertFalse(cache.contains("index", "Genesis.1"))
    }

    @Test
    fun refsWithCommasSpacesAndHebrew_areSafeKeys() = runTest {
        val cache = DiskCache(root)
        val key = "Siddur Ashkenaz, Weekday, Shacharit, מודה אני / ?*"
        cache.write("texts", key, "ok")

        assertEquals("ok", cache.read("texts", key))
    }

    @Test
    fun entryOlderThanMaxAge_readsAsMiss_butStaysAvailableWithoutALimit() = runTest {
        var now = 0L
        val cache = DiskCache(root, clock = { now })
        cache.write("index", "Genesis", "v1")
        now = wallClockMillis() + 10_000 // well past the file's modification time

        assertNull(cache.read("index", "Genesis", maxAgeMillis = 1_000))
        assertEquals("v1", cache.read("index", "Genesis")) // stale copy: the offline fallback
    }

    @Test
    fun overwrite_replacesTheValue() = runTest {
        val cache = DiskCache(root)
        cache.write("texts", "Genesis.1", "old")
        cache.write("texts", "Genesis.1", "new")

        assertEquals("new", cache.read("texts", "Genesis.1"))
    }

    @Test
    fun clear_removesEverything_andSizeReflectsContents() = runTest {
        val cache = DiskCache(root)
        cache.write("texts", "Genesis.1", "12345")
        assertTrue(cache.sizeBytes() >= 5)

        cache.clear()

        assertNull(cache.read("texts", "Genesis.1"))
        assertEquals(0L, cache.sizeBytes())
    }

    /** The real wall clock (modification times are wall‑clock based). */
    private fun wallClockMillis(): Long = io.ktor.util.date.getTimeMillis()
}
