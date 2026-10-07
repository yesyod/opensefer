package app.opensefer.ui.reader

import app.opensefer.core.model.ChapterText
import app.opensefer.core.model.ReadingHeading
import app.opensefer.core.model.ReadingItem
import app.opensefer.core.model.ReadingPassage
import app.opensefer.core.model.Segment
import app.opensefer.core.model.TocLeaf

/**
 * One row of the continuous reader's lazy list. The whole book is a flat list of rows — section
 * headings, a title per passage, and then **one row per segment** (verse / halacha / paragraph), so
 * scrolling stays light however long a chapter is, every segment is individually addressable (exact
 * resume, bookmarks, copy), and a passage that isn't loaded yet is a single [PendingRow].
 *
 * Keys are stable across loads (`t:` title, `s:` segment, `l:` pending), so the list keeps the reader's
 * place by key while passages around it load.
 */
sealed interface ReaderRow {
    val key: String

    /** Index of the passage (leaf) this row belongs to, or -1 for a section heading. */
    val passage: Int
}

data class HeadingRow(override val key: String, val depth: Int, val heTitle: String) : ReaderRow {
    override val passage: Int get() = -1
}

data class PassageTitleRow(override val passage: Int, val leaf: TocLeaf) : ReaderRow {
    override val key: String = "t:${leaf.tref}"
}

data class SegmentRow(
    override val passage: Int,
    val leaf: TocLeaf,
    val segment: Segment,
) : ReaderRow {
    override val key: String = segmentKey(leaf.tref, segment.index)
}

/** A passage whose text is still loading — or failed to ([error] non‑null, with a retry). */
data class PendingRow(override val passage: Int, val leaf: TocLeaf, val error: String? = null) : ReaderRow {
    override val key: String = "l:${leaf.tref}"
}

/** A segment's identity inside the reader: passage (leaf) index + [Segment.index]. */
data class SegmentRef(val passage: Int, val segment: Int)

fun segmentKey(tref: String, segment: Int): String = "s:$tref:$segment"

/** What the reader knows about one passage. */
sealed interface PassageState {
    data object Loading : PassageState
    data class Loaded(val chapter: ChapterText) : PassageState

    /** Sefaria has no text here (an empty daf, an uncommented chapter): it takes no room at all. */
    data object Empty : PassageState
    data class Failed(val message: String) : PassageState
}

/**
 * Flattens the book ([items], in reading order) into rows, given what is loaded so far.
 * [passageIndex] maps a leaf's tref to its position in reading order.
 */
internal fun buildReaderRows(
    items: List<ReadingItem>,
    passageIndex: Map<String, Int>,
    passages: Map<Int, PassageState>,
): List<ReaderRow> {
    val rows = ArrayList<ReaderRow>(items.size * 2)
    for (item in items) {
        when (item) {
            is ReadingHeading -> rows += HeadingRow(item.key, item.depth, item.heTitle)
            is ReadingPassage -> {
                val index = passageIndex[item.leaf.tref] ?: continue
                val state = passages[index]
                val empty = state == PassageState.Empty ||
                    (state is PassageState.Loaded && state.chapter.segments.isEmpty())
                if (empty) continue
                rows += PassageTitleRow(index, item.leaf)
                when (state) {
                    is PassageState.Loaded -> state.chapter.segments.mapTo(rows) { SegmentRow(index, item.leaf, it) }
                    is PassageState.Failed -> rows += PendingRow(index, item.leaf, state.message)
                    PassageState.Loading, PassageState.Empty, null -> rows += PendingRow(index, item.leaf)
                }
            }
        }
    }
    return rows
}

/**
 * The row index to show for a position in [passage]: the segment's own row — or the passage title
 * when the position is the very start of a passage ([atPassageStart]: first segment, not scrolled
 * into), so its heading stays visible, or when the segment no longer exists. A passage with no text
 * (no rows) resolves to the next one that has some.
 */
internal fun List<ReaderRow>.rowIndexOf(passage: Int, segment: Int, atPassageStart: Boolean = true): Int {
    val segmentRow = indexOfFirst { it is SegmentRow && it.passage == passage && it.segment.index == segment }
    val titleRow = indexOfFirst { it is PassageTitleRow && it.passage == passage }
    val isFirstSegment = segmentRow >= 0 && segmentRow == titleRow + 1
    return when {
        segmentRow >= 0 && !(isFirstSegment && atPassageStart) -> segmentRow
        titleRow >= 0 -> titleRow
        else -> indexOfFirst { it.passage > passage }.takeIf { it >= 0 } ?: lastIndex.coerceAtLeast(0)
    }
}

/**
 * The segment that row [index] stands for — the "current place" when that row is at the top: the
 * row itself, or (for a passage title, a placeholder, or a section heading) the first segment that
 * follows it. Null while that passage's text isn't loaded.
 */
internal fun List<ReaderRow>.segmentAt(index: Int): SegmentRow? {
    if (isEmpty()) return null
    val first = (index.coerceIn(0, lastIndex)..lastIndex).firstOrNull { this[it] !is HeadingRow } ?: return null
    return when (val row = this[first]) {
        is SegmentRow -> row
        else -> nextSegmentOf(first, row.passage)
    }
}

private fun List<ReaderRow>.nextSegmentOf(from: Int, passage: Int): SegmentRow? =
    (from until size).asSequence()
        .map { this[it] }
        .takeWhile { it.passage == passage }
        .filterIsInstance<SegmentRow>()
        .firstOrNull()
