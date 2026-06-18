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
 * fetch; [crumb] is the Hebrew breadcrumb shown in the table of contents.
 */
data class TocLeaf(
    override val title: String,
    override val heTitle: String,
    val crumb: String,
    val tref: String,
) : TocNode

/** A book's full navigable structure (from `/api/index/{title}`). */
data class BookContents(
    val title: String,
    val heTitle: String,
    val isComplex: Boolean,
    val root: TocBranch,
) {
    /** Every readable leaf, in reading order — drives prev/next paging and the jump picker. */
    val leaves: List<TocLeaf> = buildList { flattenLeaves(root, this) }
}

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
)

/** The fully‑loaded text of one readable unit (one [tref]), ready to render. */
data class ChapterText(
    val tref: String,
    val displayTitle: String,
    val heDisplayTitle: String,
    val segments: List<Segment>,
    val attribution: Attribution,
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

/** A book the user has added to their library, with a resume position. */
@Serializable
data class LibraryBook(
    val title: String,             // canonical Sefaria title (the identifier)
    val heTitle: String,
    val lastTref: String? = null,  // last‑opened readable unit; null = never opened
    val lastLabel: String? = null, // display label for that unit, e.g. "פרק א" / "מודה אני"
)

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
