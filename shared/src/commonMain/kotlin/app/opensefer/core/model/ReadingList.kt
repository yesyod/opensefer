package app.opensefer.core.model

/**
 * An ordered item in a book's **continuous** reading view: the book's [BookContents] tree flattened
 * depth‑first so the whole book scrolls as one stream — named sections become [ReadingHeading]s,
 * readable leaves become [ReadingPassage]s (whose text the UI loads lazily, per passage).
 */
sealed interface ReadingItem {
    val key: String
}

/** A section divider (a named branch, e.g. "שחרית"); [depth] drives heading size/indent. */
data class ReadingHeading(
    val depth: Int,
    val heTitle: String,
    override val key: String,
) : ReadingItem

/** A readable passage (a leaf, e.g. "מודה אני"); the UI fetches [TocLeaf.tref] on demand. */
data class ReadingPassage(
    val leaf: TocLeaf,
    override val key: String = leaf.tref,
) : ReadingItem

/**
 * Flattens this book into the ordered list the continuous reader renders. The synthetic root branch
 * is skipped (the book title lives in the header); every other named branch becomes a heading and
 * every leaf a passage. For a simple book (chapters are leaves) the result is just passages.
 */
fun BookContents.toReadingItems(): List<ReadingItem> {
    val out = mutableListOf<ReadingItem>()
    fun walk(node: TocNode, depth: Int) {
        when (node) {
            is TocBranch -> {
                if (depth > 0 && node.heTitle.isNotBlank()) {
                    out.add(ReadingHeading(depth, node.heTitle, key = "h:$depth:${node.title}:${out.size}"))
                }
                node.children.forEach { walk(it, depth + 1) }
            }
            is TocLeaf -> out.add(ReadingPassage(node))
        }
    }
    walk(root, 0)
    return out
}

/** The index of the passage with [tref] in [items], or -1. Used to scroll the reader to a jump target. */
fun List<ReadingItem>.indexOfPassage(tref: String): Int =
    indexOfFirst { it is ReadingPassage && it.leaf.tref == tref }
