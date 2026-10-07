package app.opensefer.ui.reader

import app.opensefer.core.model.toReadingItems
import app.opensefer.ui.sampleChapter
import app.opensefer.ui.sampleContents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReaderRowsTest {

    private val contents = sampleContents(chapters = 3)
    private val items = contents.toReadingItems()
    private val index = contents.leaves.withIndex().associate { (i, leaf) -> leaf.tref to i }

    private fun rows(vararg loaded: Int, failed: Int? = null) = buildReaderRows(
        items,
        index,
        buildMap {
            loaded.forEach { put(it, PassageState.Loaded(sampleChapter(contents.leaves[it].tref, segments = 2))) }
            failed?.let { put(it, PassageState.Failed("x")) }
        },
    )

    @Test
    fun loadedPassagesExpandToSegments_othersArePlaceholders() {
        val keys = rows(1, failed = 2).map { it.key }

        assertEquals(
            listOf("t:Book 1", "l:Book 1", "t:Book 2", "s:Book 2:0", "s:Book 2:1", "t:Book 3", "l:Book 3"),
            keys,
        )
        assertEquals("x", (rows(1, failed = 2).last() as PendingRow).error)
    }

    @Test
    fun rowIndexOf_prefersThePassageTitle_atTheVeryStartOfAPassage() {
        val rows = rows(0, 1)

        assertEquals("t:Book 2", rows[rows.rowIndexOf("Book 2", segment = 0)].key)
        assertEquals("s:Book 2:0", rows[rows.rowIndexOf("Book 2", segment = 0, atPassageStart = false)].key)
        assertEquals("s:Book 2:1", rows[rows.rowIndexOf("Book 2", segment = 1)].key)
        assertEquals("t:Book 2", rows[rows.rowIndexOf("Book 2", segment = 99)].key) // a vanished segment
    }

    @Test
    fun segmentAtOrBefore_findsTheSegmentBeingRead() {
        val rows = rows(0, 1)

        val onTitle = rows.segmentAtOrBefore(rows.indexOfFirst { it.key == "t:Book 2" })
        assertIs<SegmentRow>(onTitle)
        assertEquals("s:Book 2:0", onTitle.key) // a passage title reads as its first segment

        assertEquals("s:Book 1:1", rows.segmentAtOrBefore(1 + 1)?.key)
        assertNull(rows(/* nothing loaded */).segmentAtOrBefore(0))
    }
}
