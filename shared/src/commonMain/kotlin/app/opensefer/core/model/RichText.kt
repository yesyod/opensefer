package app.opensefer.core.model

/**
 * A framework‑neutral representation of styled inline text, produced from Sefaria's embedded HTML.
 *
 * The data layer parses HTML into this; the UI layer renders it into a Compose `AnnotatedString`.
 * This keeps Compose out of the shared module and makes the parser unit‑testable without any UI.
 */
data class RichText(val spans: List<RichSpan>) {

    val isBlank: Boolean get() = spans.all { it.text.isBlank() }

    /**
     * True when the whole run is a Sefaria **rubric** (an instruction like "say this:" — emitted
     * entirely inside `<small>`), as opposed to prayer/verse text. Rubrics are shown de‑emphasized
     * and are NOT numbered.
     */
    val isRubric: Boolean
        get() = spans.isNotEmpty() && spans.all { it.text.isBlank() || SpanStyleType.Small in it.styles }

    /** Plain concatenated text — handy for accessibility labels and tests. */
    val plainText: String get() = spans.joinToString("") { it.text }

    companion object {
        val Empty = RichText(emptyList())
        fun of(text: String) = RichText(listOf(RichSpan(text)))
    }
}

/** A run of text with a set of styles and an optional link. */
data class RichSpan(
    val text: String,
    val styles: Set<SpanStyleType> = emptySet(),
    val link: SpanLink? = null,
)

enum class SpanStyleType { Bold, Italic, Big, Small, Superscript }

/** A link carried by a span. Footnotes and internal cross‑references are the two Sefaria uses. */
sealed interface SpanLink {
    /** A footnote whose body is shown in a sheet/popover on tap. */
    data class Footnote(val body: String) : SpanLink

    /** An internal reference to another Sefaria text (Phase 2 navigation target). */
    data class Ref(val tref: String) : SpanLink
}
