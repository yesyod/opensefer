package app.opensefer.core.data

import app.opensefer.core.model.Attribution
import app.opensefer.core.model.AuthorBio
import app.opensefer.core.model.BookAbout
import app.opensefer.core.model.BookContents
import app.opensefer.core.model.BookDetails
import app.opensefer.core.model.ChapterText
import app.opensefer.core.model.EditionInfo
import app.opensefer.core.model.RichText
import app.opensefer.core.model.Segment
import app.opensefer.core.model.TocBranch
import app.opensefer.core.model.TocLeaf
import app.opensefer.core.model.TocNode
import app.opensefer.core.network.dto.IndexDto
import app.opensefer.core.network.dto.LocalizedTextDto
import app.opensefer.core.network.dto.SchemaDto
import app.opensefer.core.network.dto.TopicDto
import app.opensefer.core.network.dto.V3TextResponseDto
import app.opensefer.core.network.dto.VersionMetaDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Flattens Sefaria's jagged `text` value (String | array | nested array) into segment strings.
 * Blank entries are PRESERVED (as "") so Hebrew and English segment lists stay positionally
 * aligned — Sefaria emits "" as a placeholder when a verse exists in one language but not the other.
 */
internal fun JsonElement?.flattenStrings(): List<String> = when (this) {
    null, is JsonNull -> emptyList()
    is JsonPrimitive -> listOf(content)
    is JsonArray -> flatMap { child -> if (child is JsonNull) listOf("") else child.flattenStrings() }
    is JsonObject -> emptyList()
}

/**
 * Like [flattenStrings], but keeps each string's index path inside the jagged array — `[verse]` for
 * a chapter, `[verse, comment]` for a commentary's chapter — so the Hebrew and English versions can be
 * aligned by position even when one of them has more comments on a verse than the other.
 */
internal fun JsonElement?.flattenWithPaths(prefix: List<Int> = emptyList()): List<Pair<List<Int>, String>> =
    when (this) {
        null, is JsonNull -> emptyList()
        is JsonPrimitive -> listOf(prefix to content)
        is JsonArray -> flatMapIndexed { i, child ->
            if (child is JsonNull) listOf(prefix + i to "") else child.flattenWithPaths(prefix + i)
        }
        is JsonObject -> emptyList()
    }

private val PathOrder = Comparator<List<Int>> { a, b ->
    a.zip(b).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x.compareTo(y) } ?: a.size.compareTo(b.size)
}

/**
 * Builds a book's navigable [BookContents] from its `/api/index` response — handling BOTH shapes:
 *  - **simple** (a `JaggedArrayNode`: `depth`/`sectionNames`/`lengths`) → numbered chapters, or
 *    daf/amud leaves ("2a", "2b", …) when the first address type is `Talmud`;
 *  - **complex** (a `SchemaNode` with named `nodes`, e.g. a Siddur) → a tree of named sections, where
 *    a chaptered section expands into its chapters and an untitled "default" node adds nothing to
 *    the ref path.
 * Only leaf nodes are addressable text refs; for complex books the FULL ancestor path is required.
 */
internal fun IndexDto.toBookContents(): BookContents {
    val bookTitle = title.orEmpty()
    val bookHe = heTitle ?: bookTitle
    val sch = schema
    val nodes = sch?.nodes
    val details = BookDetails(
        category = categories.firstOrNull().cleaned(),
        heCategory = heCategories.firstOrNull().cleaned(),
        heAuthor = authors.firstNotNullOfOrNull { it.he.cleaned() },
        heSegmentName = if (nodes.isNullOrEmpty()) sch?.heSectionNames?.getOrNull(1).cleaned() else null,
    )
    return if (!nodes.isNullOrEmpty()) {
        val children = nodes.flatMap { it.toTocNodes(bookTitle, bookHe, emptyList(), emptyList()) }
        BookContents(bookTitle, bookHe, isComplex = true, root = TocBranch(bookTitle, bookHe, children), details)
    } else {
        val chapters = sch?.sectionLeaves(bookTitle, emptyList()) ?: listOf(wholeBookLeaf(bookTitle, bookHe))
        BookContents(bookTitle, bookHe, isComplex = false, root = TocBranch(bookTitle, bookHe, chapters), details)
    }
}

/**
 * The chapter (or daf) leaves of a jagged‑array node addressed by [ref], or null when it has no
 * sections to split into (depth 1, or no `lengths`) and must be read as one unit.
 */
private fun SchemaDto.sectionLeaves(ref: String, hePath: List<String>): List<TocNode>? {
    val count = lengths.firstOrNull()?.takeIf { it > 0 } ?: return null
    if ((depth ?: 1) < 2) return null
    val stem = ref.replace(' ', '_')
    return if (addressTypes.firstOrNull() == TALMUD_ADDRESS) {
        talmudLeaves(stem, count, hePath)
    } else {
        val secEn = sectionNames.firstOrNull() ?: "Section"
        val secHe = heSectionNames.firstOrNull() ?: secEn
        (1..count).map { i ->
            val numeral = HebrewNumerals.toHebrew(i)
            val heLabel = "$secHe ${HebrewNumerals.punctuate(numeral)}"
            TocLeaf(
                title = "$secEn $i",
                heTitle = heLabel,
                crumb = (hePath + heLabel).joinToString(" › "),
                tref = "$stem.$i",
                shortLabel = numeral,
            )
        }
    }
}

private const val TALMUD_ADDRESS = "Talmud"
private const val FIRST_TALMUD_AMUD = 2

/**
 * Talmud sections are amudim counted from 1a (index 0 = 1a, 1 = 1b, 2 = 2a…) and `lengths` holds the
 * last one, so the leaves run from index 2 (every tractate opens on daf 2) and are addressed
 * `Berakhot.2a`, `Berakhot.2b`, ….
 */
private fun talmudLeaves(stem: String, amudim: Int, hePath: List<String>): List<TocNode> =
    (FIRST_TALMUD_AMUD until amudim).map { i ->
        val daf = i / 2 + 1
        val isAmudA = i % 2 == 0
        val numeral = HebrewNumerals.toHebrew(daf)
        val heLabel = "דף ${HebrewNumerals.punctuate(numeral)} ${if (isAmudA) "ע״א" else "ע״ב"}"
        val address = "$daf${if (isAmudA) "a" else "b"}"
        TocLeaf(
            title = "Daf $address",
            heTitle = heLabel,
            crumb = (hePath + heLabel).joinToString(" › "),
            tref = "$stem.$address",
            shortLabel = numeral + if (isAmudA) "." else ":", // the traditional ב. / ב: amud notation
        )
    }

private fun wholeBookLeaf(bookTitle: String, bookHe: String) =
    TocLeaf(bookTitle, bookHe, crumb = bookHe, tref = bookTitle)

private fun SchemaDto.toTocNodes(
    bookTitle: String,
    bookHe: String,
    enPath: List<String>,
    hePath: List<String>,
): List<TocNode> {
    val nodeEn = title.orEmpty().trim()
    val nodeHe = heTitle.cleaned() ?: nodeEn
    val untitled = isDefault || nodeEn.isEmpty()
    val en = if (untitled) enPath else enPath + nodeEn
    val he = if (untitled) hePath else hePath + nodeHe
    val children = nodes
    if (!children.isNullOrEmpty()) {
        val inner = children.flatMap { it.toTocNodes(bookTitle, bookHe, en, he) }
        return if (untitled) inner else listOf(TocBranch(nodeEn, nodeHe, inner))
    }
    // A readable unit. The full comma‑joined ancestor path is required (verified against the API).
    val ref = (listOf(bookTitle) + en).joinToString(", ")
    val chapters = sectionLeaves(ref, he)
    return when {
        chapters == null -> listOf(
            TocLeaf(
                title = en.lastOrNull() ?: bookTitle,
                heTitle = he.lastOrNull() ?: bookHe,
                crumb = he.joinToString(" › ").ifEmpty { bookHe },
                tref = ref,
            ),
        )
        untitled -> chapters // a default node's chapters sit directly under its parent
        else -> listOf(TocBranch(nodeEn, nodeHe, chapters))
    }
}

/** Maps a `/api/v3/texts` response (for [tref]) into a ready‑to‑render [ChapterText]. */
internal fun V3TextResponseDto.toChapterText(tref: String): ChapterText {
    // Resolve English first, then the original — falling back to a non-English version only when there
    // is genuinely no Hebrew (so an English-only book never duplicates English into the Hebrew slot).
    val en = versions.firstOrNull { it.language == "en" }
    val he = versions.firstOrNull { it.language == "he" }
        ?: versions.firstOrNull { it !== en && it.versionTitle != en?.versionTitle }
    val heByPath = he?.text.flattenWithPaths().toMap()
    val enByPath = en?.text.flattenWithPaths().toMap()
    val paths = (heByPath.keys + enByPath.keys).sortedWith(PathOrder)

    val parsed = paths.mapIndexedNotNull { i, path ->
        val hebrew = heByPath[path]?.let(SefariaHtmlParser::parse)?.takeUnless { it.isBlank }
        val english = enByPath[path]?.let(SefariaHtmlParser::parse)?.takeUnless { it.isBlank }
        if (hebrew == null && english == null) return@mapIndexedNotNull null // genuinely empty segment
        ParsedSegment(i, path, hebrew, english, rubric = (hebrew ?: english)?.isRubric == true)
    }
    // Prayer books interleave un‑numbered instructions, so their prayers number consecutively; every
    // other text keeps its own numbering (verse 7 stays 7 even if verse 6 is missing).
    val hasRubrics = parsed.any { it.rubric }
    var numbered = 0
    val segments = parsed.map { seg ->
        val numbers: List<Int> = when {
            seg.rubric -> emptyList()
            hasRubrics -> listOf(++numbered)
            else -> seg.path.ifEmpty { listOf(0) }.map { it + 1 }
        }
        Segment(
            index = seg.index,
            label = numbers.joinToString(":") { HebrewNumerals.toHebrew(it) },
            isRubric = seg.rubric,
            hebrew = seg.hebrew,
            english = seg.english,
            enLabel = numbers.joinToString(":"),
        )
    }

    return ChapterText(
        tref = tref,
        displayTitle = title ?: tref,
        heDisplayTitle = heTitle ?: title ?: tref,
        segments = segments,
        attribution = Attribution(
            hebrewVersion = he?.versionTitle,
            englishVersion = en?.versionTitle,
            license = he?.license ?: en?.license,
        ),
        ref = ref.cleaned(),
        heRef = heRef.cleaned(),
    )
}

private class ParsedSegment(
    val index: Int,
    val path: List<Int>,
    val hebrew: RichText?,
    val english: RichText?,
    val rubric: Boolean,
)

/* ---- "About this book" metadata ---- */

/** Sefaria era codes → Hebrew labels (best‑effort; unknown codes fall through to the raw code). */
private val EraLabels = mapOf(
    "T" to "תנאים",
    "A" to "אמוראים",
    "GN" to "גאונים",
    "RI" to "ראשונים",
    "AH" to "אחרונים",
    "CO" to "בני זמננו",
)

/** Hebrew‑first text from a bilingual `{en, he}` value, trimmed; null when effectively empty. */
private fun LocalizedTextDto?.heFirst(): String? =
    (this?.he ?: this?.en)?.trim()?.takeUnless { it.isEmpty() }

private fun String?.cleaned(): String? = this?.trim()?.takeUnless { it.isEmpty() }

/** First non-blank candidate, with any inline HTML stripped to plain text; null when none. */
private fun plainTextOf(vararg candidates: String?): String? =
    candidates.firstNotNullOfOrNull { it.cleaned() }?.let { SefariaHtmlParser.parse(it).plainText }.cleaned()

/**
 * Maps an `/api/index` response into the index‑derived part of [BookAbout]. The author biography
 * ([BookAbout.authorBio]) and editions ([BookAbout.editions]) are filled in separately by the
 * repository (they come from other endpoints) via `copy`.
 */
internal fun IndexDto.toBookAbout(): BookAbout {
    val bookTitle = title.orEmpty()
    val author = authors.firstOrNull()
    return BookAbout(
        title = bookTitle,
        heTitle = heTitle ?: bookTitle,
        authorHe = author?.he.cleaned(),
        authorEn = author?.en.cleaned(),
        description = plainTextOf(heDesc, enDesc, heShortDesc, enShortDesc),
        era = era.cleaned()?.let { EraLabels[it] ?: it },
        compDate = compDateString.heFirst(),
        compPlace = compPlaceString.heFirst(),
        pubDate = pubDateString.heFirst(),
        pubPlace = pubPlaceString.heFirst(),
        categories = heCategories.ifEmpty { categories },
        authorBio = null,
        editions = emptyList(),
    )
}

/** Maps an author's `/api/v2/topics` page into an [AuthorBio] (Hebrew‑first bio + links). */
internal fun TopicDto.toAuthorBio(): AuthorBio {
    val p = properties
    return AuthorBio(
        birthYear = p?.birthYear?.text.cleaned(),
        deathYear = p?.deathYear?.text.cleaned(),
        birthPlace = p?.birthPlace?.text.cleaned(),
        deathPlace = p?.deathPlace?.text.cleaned(),
        bio = plainTextOf(p?.heBio?.text, p?.enBio?.text, description?.he, description?.en),
        wikiLink = (p?.heWikiLink?.text.cleaned() ?: p?.enWikiLink?.text.cleaned()),
    )
}

/** Picks the primary (or first) Hebrew and English editions from `/api/texts/versions`. */
internal fun List<VersionMetaDto>.toEditions(): List<EditionInfo> {
    fun pick(lang: String): EditionInfo? {
        val candidates = filter { it.language == lang }
        val chosen = candidates.firstOrNull { it.isPrimary } ?: candidates.firstOrNull() ?: return null
        val editionTitle = (if (lang == "he") chosen.versionTitleInHebrew.cleaned() else null)
            ?: chosen.versionTitle.cleaned() ?: return null
        return EditionInfo(
            title = editionTitle,
            language = lang,
            license = chosen.license.cleaned(),
            source = chosen.versionSource.cleaned(),
        )
    }
    return listOfNotNull(pick("he"), pick("en"))
}
