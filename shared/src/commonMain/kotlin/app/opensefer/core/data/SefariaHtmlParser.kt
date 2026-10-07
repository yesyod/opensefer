package app.opensefer.core.data

import app.opensefer.core.model.RichSpan
import app.opensefer.core.model.RichText
import app.opensefer.core.model.SpanLink
import app.opensefer.core.model.SpanStyleType

/**
 * Parses the small, well‑known set of inline HTML that Sefaria embeds in text into a
 * framework‑neutral [RichText]. Deliberately dependency‑free (no Ksoup/Jsoup): Sefaria's markup
 * is shallow inline styling, so a focused scanner is smaller, faster, and risk‑free to bundle.
 *
 * Handled tags: `b`/`strong`, `i`/`em`, `big`, `small`, `sup`, `a` (with `data-ref`/`href`),
 * `br`. Footnote bodies (`<i class="footnote">…</i>`) are attached to the preceding `<sup>`
 * marker so the UI can show them in a sheet. Unknown tags are ignored but their text is kept.
 */
object SefariaHtmlParser {

    fun parse(html: String): RichText {
        if (html.isEmpty()) return RichText.Empty
        val spans = mutableListOf<RichSpan>()
        val styleStack = ArrayDeque<SpanStyleType>()
        var currentRef: String? = null
        var inFootnote = false
        var footnoteDepth = 0 // nesting of <i> tags inside a footnote body
        val footnoteBuf = StringBuilder()

        val text = StringBuilder()
        var i = 0

        fun flushText() {
            if (text.isEmpty()) return
            val decoded = decodeEntities(text.toString())
            if (decoded.isNotEmpty()) {
                spans.add(RichSpan(decoded, styleStack.toSet(), currentRef?.let { SpanLink.Ref(it) }))
            }
            text.clear()
        }

        while (i < html.length) {
            val c = html[i]
            if (c == '<') {
                val end = html.indexOf('>', i)
                if (end == -1) { text.append(c); i++; continue }
                val raw = html.substring(i + 1, end).trim()
                i = end + 1

                val isClose = raw.startsWith("/")
                val body = raw.removePrefix("/")
                val name = body.substringBefore(' ').substringBefore('/').lowercase()
                val isFootnoteOpen = !isClose && name == "i" && raw.contains("footnote")

                if (inFootnote) {
                    // Only the OUTERMOST </i> closes the footnote; nested <i> fragments
                    // (book titles, transliterations) stay part of the body.
                    if (name == "i") {
                        if (isClose) {
                            if (footnoteDepth > 0) {
                                footnoteDepth--
                            } else {
                                inFootnote = false
                                attachFootnote(spans, footnoteBuf.toString())
                                footnoteBuf.clear()
                            }
                        } else {
                            footnoteDepth++
                        }
                    }
                    continue
                }

                if (isFootnoteOpen) {
                    flushText()
                    inFootnote = true
                    footnoteDepth = 0
                    continue
                }

                when (name) {
                    "br" -> { flushText(); spans.add(RichSpan("\n")) }
                    "b", "strong" -> toggle(styleStack, SpanStyleType.Bold, isClose, ::flushText)
                    "i", "em" -> toggle(styleStack, SpanStyleType.Italic, isClose, ::flushText)
                    "big" -> toggle(styleStack, SpanStyleType.Big, isClose, ::flushText)
                    "small" -> toggle(styleStack, SpanStyleType.Small, isClose, ::flushText)
                    "sup" -> toggle(styleStack, SpanStyleType.Superscript, isClose, ::flushText)
                    "a" -> {
                        flushText()
                        currentRef = if (isClose) null else extractAttr(raw, "data-ref")
                            ?: extractAttr(raw, "href")
                    }
                    else -> { /* unknown tag: ignore, keep inner text */ }
                }
            } else if (inFootnote) {
                footnoteBuf.append(c)
                i++
            } else {
                text.append(c)
                i++
            }
        }
        flushText()
        return RichText(spans.filter { it.text.isNotEmpty() })
    }

    private inline fun toggle(
        stack: ArrayDeque<SpanStyleType>,
        style: SpanStyleType,
        isClose: Boolean,
        flush: () -> Unit,
    ) {
        flush()
        if (isClose) {
            // remove the nearest matching style
            val idx = stack.indexOfLast { it == style }
            if (idx >= 0) stack.removeAt(idx)
        } else {
            stack.addLast(style)
        }
    }

    /** Turn the last superscript span into a footnote‑carrying span. */
    private fun attachFootnote(spans: MutableList<RichSpan>, body: String) {
        val decoded = decodeEntities(body).trim()
        if (decoded.isEmpty()) return
        val lastSup = spans.indexOfLast { SpanStyleType.Superscript in it.styles }
        if (lastSup >= 0) {
            spans[lastSup] = spans[lastSup].copy(link = SpanLink.Footnote(decoded))
        } else {
            spans.add(RichSpan("*", setOf(SpanStyleType.Superscript), SpanLink.Footnote(decoded)))
        }
    }

    private fun extractAttr(tag: String, attr: String): String? {
        val key = "$attr="
        val start = tag.indexOf(key)
        if (start == -1) return null
        val after = tag.substring(start + key.length)
        val quote = after.firstOrNull() ?: return null
        return if (quote == '"' || quote == '\'') {
            val close = after.indexOf(quote, 1)
            if (close == -1) null else after.substring(1, close)
        } else {
            after.takeWhile { !it.isWhitespace() && it != '>' }
        }
    }

    private val Entity = Regex("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z]{2,8});")

    private val NamedEntities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "thinsp" to " ", "ensp" to " ", "emsp" to " ",
        "mdash" to "—", "ndash" to "–", "hellip" to "…",
        "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "laquo" to "«", "raquo" to "»",
        "shy" to "\u00AD", "lrm" to "\u200E", "rlm" to "\u200F", "zwj" to "\u200D", "zwnj" to "\u200C",
    )

    /**
     * Decodes character references in ONE pass, so the "&" that one produces can never start another:
     * "&amp;lt;" and "&#38;lt;" both stay the literal text "&lt;". Unknown or invalid ones are kept verbatim.
     */
    private fun decodeEntities(s: String): String {
        if ('&' !in s) return s
        return s.replace(Entity) { match ->
            val name = match.groupValues[1]
            val decoded = if (name[0] == '#') decodeCodePoint(name.substring(1)) else NamedEntities[name]
            decoded ?: match.value
        }
    }

    /** `&#1488;` / `&#x5D0;` → "א"; null for an invalid code point (the entity is then kept verbatim). */
    private fun decodeCodePoint(digits: String): String? {
        val code = if (digits[0] == 'x' || digits[0] == 'X') digits.drop(1).toIntOrNull(16) else digits.toIntOrNull()
        return when {
            code == null || code <= 0 || code > MAX_CODE_POINT || code in SURROGATES -> null
            code < SUPPLEMENTARY_START -> Char(code).toString()
            else -> {
                val offset = code - SUPPLEMENTARY_START
                charArrayOf(Char(HIGH_SURROGATE + (offset shr 10)), Char(LOW_SURROGATE + (offset and 0x3FF)))
                    .concatToString()
            }
        }
    }

    private const val MAX_CODE_POINT = 0x10FFFF
    private const val SUPPLEMENTARY_START = 0x10000
    private const val HIGH_SURROGATE = 0xD800
    private const val LOW_SURROGATE = 0xDC00
    private val SURROGATES = 0xD800..0xDFFF
}
