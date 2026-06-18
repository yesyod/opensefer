package app.opensefer.core.data

import app.opensefer.core.model.Attribution
import app.opensefer.core.model.AuthorBio
import app.opensefer.core.model.BookAbout
import app.opensefer.core.model.BookContents
import app.opensefer.core.model.ChapterText
import app.opensefer.core.model.EditionInfo
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
 * Builds a book's navigable [BookContents] from its `/api/index` response — handling BOTH shapes:
 *  - **simple** (a `JaggedArrayNode`: `depth`/`sectionNames`/`lengths`) → numbered chapters;
 *  - **complex** (a `SchemaNode` with named `nodes`, e.g. a Siddur) → a tree of named sections.
 * Only leaf nodes are addressable text refs; for complex books the FULL ancestor path is required.
 */
internal fun IndexDto.toBookContents(): BookContents {
    val bookTitle = title.orEmpty()
    val bookHe = heTitle ?: bookTitle
    val sch = schema
    val nodes = sch?.nodes
    return if (!nodes.isNullOrEmpty()) {
        val children = nodes.map { it.toTocNode(bookTitle, emptyList(), emptyList()) }
        BookContents(bookTitle, bookHe, isComplex = true, root = TocBranch(bookTitle, bookHe, children))
    } else {
        val depth = sch?.depth ?: 1
        val secEn = sch?.sectionNames?.firstOrNull() ?: "Section"
        val secHe = sch?.heSectionNames?.firstOrNull() ?: secEn
        val count = sch?.lengths?.firstOrNull()?.takeIf { it > 0 }
        val chapters: List<TocNode> = when {
            depth <= 1 || count == null -> listOf(wholeBookLeaf(bookTitle, bookHe))
            else -> (1..count).map { i ->
                val heLabel = "$secHe ${HebrewNumerals.toHebrew(i)}"
                // Integer numbering; Talmud‑daf addressing is a documented future refinement.
                TocLeaf("$secEn $i", heLabel, crumb = heLabel, tref = "${bookTitle.replace(' ', '_')}.$i")
            }
        }
        BookContents(bookTitle, bookHe, isComplex = false, root = TocBranch(bookTitle, bookHe, chapters))
    }
}

private fun wholeBookLeaf(bookTitle: String, bookHe: String) =
    TocLeaf(bookTitle, bookHe, crumb = bookHe, tref = bookTitle)

private fun SchemaDto.toTocNode(bookTitle: String, enPath: List<String>, hePath: List<String>): TocNode {
    val nodeEn = title.orEmpty()
    val nodeHe = heTitle ?: nodeEn
    val en = enPath + nodeEn
    val he = hePath + nodeHe
    val children = nodes
    return if (!children.isNullOrEmpty()) {
        TocBranch(nodeEn, nodeHe, children.map { it.toTocNode(bookTitle, en, he) })
    } else {
        // A readable leaf. The full comma‑joined ancestor path is required (verified against the API).
        TocLeaf(
            title = nodeEn,
            heTitle = nodeHe,
            crumb = he.joinToString(" › "),
            tref = (listOf(bookTitle) + en).joinToString(", "),
        )
    }
}

/** Maps a `/api/v3/texts` response (for [tref]) into a ready‑to‑render [ChapterText]. */
internal fun V3TextResponseDto.toChapterText(tref: String): ChapterText {
    // Resolve English first, then Hebrew — falling back to a non-English version only when there is
    // genuinely no Hebrew (so an English-only book never duplicates English into the Hebrew slot).
    val en = versions.firstOrNull { it.language == "en" }
    val he = versions.firstOrNull { it.language == "he" } ?: versions.firstOrNull { it !== en }
    val heSegs = he?.text.flattenStrings()
    val enSegs = en?.text.flattenStrings()
    val count = maxOf(heSegs.size, enSegs.size)

    var numbered = 0 // rubrics (instructions) are skipped, so prayer verses number consecutively
    val segments = (0 until count).mapNotNull { i ->
        val hebrew = heSegs.getOrNull(i)?.let(SefariaHtmlParser::parse)?.takeUnless { it.isBlank }
        val english = enSegs.getOrNull(i)?.let(SefariaHtmlParser::parse)?.takeUnless { it.isBlank }
        if (hebrew == null && english == null) return@mapNotNull null // genuinely empty segment
        val rubric = (hebrew ?: english)?.isRubric == true
        val label = if (rubric) "" else HebrewNumerals.toHebrew(++numbered)
        Segment(index = i, label = label, isRubric = rubric, hebrew = hebrew, english = english)
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
    )
}

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
