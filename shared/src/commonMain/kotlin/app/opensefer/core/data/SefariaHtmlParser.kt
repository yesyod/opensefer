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

    private fun decodeEntities(s: String): String {
        if ('&' !in s) return s
        // Resolve &amp; LAST so a double-escaped "&amp;lt;" stays "&lt;" instead of becoming "<".
        return s
            .replace("&nbsp;", " ")
            .replace("&thinsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }
}
