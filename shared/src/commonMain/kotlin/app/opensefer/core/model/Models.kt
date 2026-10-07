package app.opensefer.core.model

import kotlinx.serialization.Serializable

/**
 * Domain models for OpenSefer — pure Kotlin, framework‑free.
 *
 * A book's structure is a tree of [TocNode]s — [TocBranch]es (named sections) down to [TocLeaf]es
 * (readable units, each carrying the exact Sefaria [TocLeaf.tref]). Simple books (numbered chapters)
 * and complex books (named sections, e.g. a Siddur) share this one model; see
 * [app.opensefer.core.data.toBookContents].
 *
 * Text from Sefaria arrives as a *jagged array* of HTML strings. We parse that HTML into a
 * framework‑neutral [RichText] in the data layer ([app.opensefer.core.data.SefariaHtmlParser]),
 * so the UI layer is the only place that ever touches Compose types.
 */

/** A node in a book's table of contents. */
sealed interface TocNode {
    val title: String      // English title — used to construct refs
    val heTitle: String    // Hebrew display title
}

/** A named section that contains children (e.g. a Siddur's "Weekday" → "Shacharit" → …). */
data class TocBranch(
    override val title: String,
    override val heTitle: String,
    val children: List<TocNode>,
) : TocNode

/**
 * A directly‑readable unit (a chapter, a prayer…). [tref] is the exact, complete Sefaria ref to
 * fetch; [crumb] is the Hebrew breadcrumb shown in the table of contents; [shortLabel] is the compact
 * form for a chapter grid ("א", or "ב." / "ב:" for a Talmud amud).
 */
data class TocLeaf(
    override val title: String,
    override val heTitle: String,
    val crumb: String,
    val tref: String,
    val shortLabel: String = heTitle,
) : TocNode

/** A book's full navigable structure (from `/api/index/{title}`). */
data class BookContents(
    val title: String,
    val heTitle: String,
    val isComplex: Boolean,
    val root: TocBranch,
    val details: BookDetails = BookDetails(),
) {
    /** Every readable leaf, in reading order — drives prev/next paging and the jump picker. */
    val leaves: List<TocLeaf> = buildList { flattenLeaves(root, this) }
}

/**
 * Shelf metadata derived from a book's index — enough to draw its cover and label its segments.
 * Every field is optional: Sefaria omits authors for many books, and complex books have no single
 * segment name.
 */
data class BookDetails(
    val category: String? = null,      // top‑level English category ("Halakhah") — picks the cover colour
    val heCategory: String? = null,    // its Hebrew label ("הלכה")
    val heAuthor: String? = null,      // "רמב״ם"
    val heSegmentName: String? = null, // what one segment is called ("הלכה", "פסוק") — simple books only
)

private fun flattenLeaves(node: TocNode, out: MutableList<TocLeaf>) {
    when (node) {
        is TocLeaf -> out.add(node)
        is TocBranch -> node.children.forEach { flattenLeaves(it, out) }
    }
}

/** One addressable, tappable unit of text (a halacha / verse / paragraph). The [index] is the
 *  segment hook that lets commentaries, footnotes and sharing attach later (BLUEPRINT §3 #5). */
data class Segment(
    val index: Int,
    val label: String,        // displayed marker: "א", "ב", … (Hebrew); blank for rubrics
    val isRubric: Boolean,    // a Sefaria instruction line ("say this:") — de‑emphasized, un‑numbered
    val hebrew: RichText?,
    val english: RichText?,
    val enLabel: String = "", // the same marker in digits ("3", or "2:4" in a commentary) for English citations
)

/** The fully‑loaded text of one readable unit (one [tref]), ready to render. */
data class ChapterText(
    val tref: String,
    val displayTitle: String,
    val heDisplayTitle: String,
    val segments: List<Segment>,
    val attribution: Attribution,
    val ref: String? = null,   // Sefaria's normalized English ref ("Genesis 1") — for citations
    val heRef: String? = null, // …and its Hebrew form ("בראשית א׳")
)

/** Per‑edition credit + license — required by Sefaria's data terms (see ATTRIBUTION.md). */
data class Attribution(
    val hebrewVersion: String?,
    val englishVersion: String?,
    val license: String?,
)

/**
 * Everything the "About this book" screen shows, aggregated from `/api/index/{title}` plus
 * (optionally) the author's `/api/v2/topics` page and the book's `/api/texts/versions` editions.
 * Strings are resolved Hebrew‑first; any field may be null/empty when Sefaria doesn't provide it.
 */
data class BookAbout(
    val title: String,
    val heTitle: String,
    val authorHe: String?,
    val authorEn: String?,
    val description: String?,
    val era: String?,
    val compDate: String?,
    val compPlace: String?,
    val pubDate: String?,
    val pubPlace: String?,
    val categories: List<String>,
    val authorBio: AuthorBio?,
    val editions: List<EditionInfo>,
)

/** The author's biographical details, from their Sefaria topic page. */
data class AuthorBio(
    val birthYear: String?,
    val deathYear: String?,
    val birthPlace: String?,
    val deathPlace: String?,
    val bio: String?,
    val wikiLink: String?,
)

/** One edition (version) of a book with its license — for the About screen's editions list. */
data class EditionInfo(
    val title: String,
    val language: String,   // "he" | "en"
    val license: String?,
    val source: String?,
)

/**
 * A book the user saved to their library: its resume position (down to the segment), reading
 * progress for the cover, and the shelf metadata the cover is drawn from. Every field after
 * [heTitle] has a default, so libraries persisted by older versions still decode.
 */
@Serializable
data class LibraryBook(
    val title: String,              // canonical Sefaria title (the identifier)
    val heTitle: String,
    val lastTref: String? = null,   // last‑read readable unit; null = never opened
    val lastLabel: String? = null,  // display label for that place, e.g. "פרק א, הלכה ג" / "מודה אני"
    val lastSegment: Int = 0,       // Segment.index within [lastTref] — exact resume…
    val lastOffset: Int = 0,        // …and how far into that segment (px) the page was scrolled
    val progress: Float = 0f,       // 0..1 through the book, drawn under the cover
    val lastReadAt: Long = 0L,      // epoch millis of the last read; 0 = never
    val addedAt: Long = 0L,         // epoch millis it was saved
    val category: String? = null,   // see [BookDetails]
    val heCategory: String? = null,
    val heAuthor: String? = null,
    val offline: Boolean = false,   // every passage is in the local cache
)

/**
 * Where the reader is in a book: a passage ([tref]), the [segment] index inside it, and the scroll
 * [offset] (px) into that segment — so a long halacha reopens mid‑way, exactly where it was left.
 */
data class ReadingPosition(
    val tref: String,
    val segment: Int,
    val label: String,
    val progress: Float,
    val offset: Int = 0,
)

/** A saved place in a book — one segment — with a short [snippet] of its text for the list. */
@Serializable
data class Bookmark(
    val bookTitle: String,
    val heBookTitle: String,
    val tref: String,
    val segment: Int,
    val label: String,
    val snippet: String,
    val createdAt: Long = 0L,
) {
    val id: String get() = bookmarkId(tref, segment)
}

/** The stable identity of a bookmark: one segment of one passage. */
fun bookmarkId(tref: String, segment: Int): String = "$tref#$segment"

/** Progress of a whole‑book offline download: [done] of [total] passages are cached. */
data class DownloadProgress(val done: Int, val total: Int) {
    val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total
}

/**
 * A search/autocomplete hit when adding a book.
 *
 * [title] is Sefaria's **canonical ref** (e.g. "Mishneh Torah, Repentance") — the identifier used
 * for all index/text calls, matching the convention used everywhere else ([LibraryBook.title]).
 * [heTitle] is the display name shown to the user.
 */
data class BookSearchResult(
    val title: String,
    val heTitle: String,
    val category: String?,
)
