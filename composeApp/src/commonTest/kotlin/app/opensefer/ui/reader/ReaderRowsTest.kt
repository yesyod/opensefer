package app.opensefer.ui.reader

import app.opensefer.core.model.BookContents
import app.opensefer.core.model.TocBranch
import app.opensefer.core.model.TocLeaf
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

    private fun rows(vararg loaded: Int, failed: Int? = null, empty: Int? = null) = buildReaderRows(
        items,
        index,
        buildMap {
            loaded.forEach { put(it, PassageState.Loaded(sampleChapter(contents.leaves[it].tref, segments = 2))) }
            failed?.let { put(it, PassageState.Failed("x")) }
            empty?.let { put(it, PassageState.Empty) }
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
    fun aPassageWithNoText_takesNoRoomAtAll() {
        val keys = rows(0, 2, empty = 1).map { it.key }

        assertEquals(listOf("t:Book 1", "s:Book 1:0", "s:Book 1:1", "t:Book 3", "s:Book 3:0", "s:Book 3:1"), keys)
    }

    @Test
    fun rowIndexOf_prefersThePassageTitle_atTheVeryStartOfAPassage() {
        val rows = rows(0, 1)

        assertEquals("t:Book 2", rows[rows.rowIndexOf(passage = 1, segment = 0)].key)
        assertEquals("s:Book 2:0", rows[rows.rowIndexOf(passage = 1, segment = 0, atPassageStart = false)].key)
        assertEquals("s:Book 2:1", rows[rows.rowIndexOf(passage = 1, segment = 1)].key)
        assertEquals("t:Book 2", rows[rows.rowIndexOf(passage = 1, segment = 99)].key) // a vanished segment
    }

    @Test
    fun rowIndexOf_anEmptyPassage_landsOnTheNextOneWithText() {
        val rows = rows(0, 2, empty = 1)

        assertEquals("t:Book 3", rows[rows.rowIndexOf(passage = 1, segment = 0)].key)
    }

    @Test
    fun segmentAt_findsTheSegmentBeingRead() {
        val rows = rows(0, 1)

        val onTitle = rows.segmentAt(rows.indexOfFirst { it.key == "t:Book 2" })
        assertIs<SegmentRow>(onTitle)
        assertEquals("s:Book 2:0", onTitle.key) // a passage title reads as its first segment

        assertEquals("s:Book 1:1", rows.segmentAt(1 + 1)?.key)
        assertNull(rows(/* nothing loaded */).segmentAt(0)) // still loading: no place to bookmark yet
    }

    @Test
    fun segmentAt_aSectionHeadingAtTheTop_belongsToWhatFollowsIt() {
        // A prayer book: two named sections, so a heading sits between the passages.
        val siddur = BookContents(
            title = "Siddur",
            heTitle = "סידור",
            isComplex = true,
            root = TocBranch(
                "Siddur",
                "סידור",
                listOf(
                    TocBranch("Morning", "שחרית", listOf(TocLeaf("Modeh Ani", "מודה אני", "מודה אני", "Siddur, Modeh Ani"))),
                    TocBranch("Evening", "ערבית", listOf(TocLeaf("Shema", "שמע", "שמע", "Siddur, Shema"))),
                ),
            ),
        )
        val passages = siddur.leaves.withIndex().associate { (i, leaf) -> leaf.tref to i }
        val rows = buildReaderRows(
            siddur.toReadingItems(),
            passages,
            siddur.leaves.indices.associateWith { PassageState.Loaded(sampleChapter(siddur.leaves[it].tref, segments = 2)) },
        )
        val evening = rows.indexOfFirst { it is HeadingRow && it.heTitle == "ערבית" }

        // Not the morning section's last paragraph (off screen above), but the evening's first.
        assertEquals("s:Siddur, Shema:0", rows.segmentAt(evening)?.key)
    }
}
