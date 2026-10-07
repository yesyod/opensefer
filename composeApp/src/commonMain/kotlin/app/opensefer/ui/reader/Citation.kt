package app.opensefer.ui.reader

import app.opensefer.core.data.HebrewNumerals
import app.opensefer.core.domain.ReadingLanguage
import app.opensefer.core.model.ChapterText
import app.opensefer.core.model.RichText
import app.opensefer.core.model.Segment
import app.opensefer.core.model.SpanStyleType
import app.opensefer.core.model.TocLeaf
import app.opensefer.ui.text.stripNikud

/** One passage's selected segments, in reading order — the input to [buildCopyText]. */
internal data class CopyPart(val leaf: TocLeaf, val chapter: ChapterText, val segments: List<Segment>)

/**
 * The text put on the clipboard for "copy": the selected segments in the languages on screen
 * (nikud stripped when it's hidden), followed by a source line such as `(בראשית א׳:א׳-ג׳)` —
 * so a pasted quote always says where it's from.
 */
internal fun buildCopyText(
    heBookTitle: String,
    parts: List<CopyPart>,
    language: ReadingLanguage,
    showNikud: Boolean,
): String {
    val body = parts.flatMap { part ->
        part.segments.map { segment ->
            listOfNotNull(
                segment.hebrew?.takeIf { language != ReadingLanguage.English }?.copyText()
                    ?.let { if (showNikud) it else it.stripNikud() },
                segment.english?.takeIf { language != ReadingLanguage.Hebrew }?.copyText(),
            ).joinToString("\n")
        }
    }.filter { it.isNotBlank() }
    val separator = if (language == ReadingLanguage.Bilingual) "\n\n" else "\n"
    val source = parts.joinToString("; ") { citation(heBookTitle, it, english = language == ReadingLanguage.English) }
    return body.joinToString(separator) + "\n(" + source + ")"
}

/** `בראשית א׳:ג׳`, `בראשית א׳:ג׳-ה׳`, `ברכות ב׳ א:ד׳, ו׳` — Sefaria's ref with the segment labels. */
internal fun citation(heBookTitle: String, part: CopyPart, english: Boolean): String {
    val ref = if (english) {
        part.chapter.ref ?: part.leaf.title
    } else {
        part.chapter.heRef ?: "$heBookTitle, ${part.leaf.heTitle}"
    }
    val labels = part.segments.filterNot { it.isRubric }.map {
        if (english) it.enLabel else HebrewNumerals.punctuate(it.label)
    }
    val consecutive = part.segments.zipWithNext().all { (a, b) -> b.index == a.index + 1 }
    val range = when {
        labels.isEmpty() -> null
        labels.size == 1 -> labels.single()
        consecutive -> "${labels.first()}-${labels.last()}"
        else -> labels.joinToString(", ")
    }
    return if (range == null) ref else "$ref:$range"
}

/**
 * A short human label for a place in a book, e.g. `פרק ב׳, הלכה ג׳` — shown on bookmarks and in the
 * library's "continue reading". [segmentName] is the book's name for one segment, when known.
 */
internal fun placeLabel(leaf: TocLeaf, segment: Segment?, segmentName: String?): String {
    val marker = segment?.label?.takeIf { it.isNotBlank() }?.let(HebrewNumerals::punctuate) ?: return leaf.heTitle
    return if (segmentName != null) "${leaf.heTitle}, $segmentName $marker" else "${leaf.heTitle}, $marker"
}

/** A one‑line preview of a segment for the bookmarks list. */
internal fun Segment.snippet(maxLength: Int = SNIPPET_LENGTH): String {
    val plain = (hebrew?.copyText()?.stripNikud() ?: english?.copyText().orEmpty())
        .replace('\n', ' ')
        .trim()
    return if (plain.length <= maxLength) plain else plain.take(maxLength).trimEnd() + "…"
}

private const val SNIPPET_LENGTH = 90

/** Plain text without footnote markers (superscripts), as a reader would want to paste it. */
internal fun RichText.copyText(): String =
    spans.filterNot { SpanStyleType.Superscript in it.styles }.joinToString("") { it.text }.trim()
