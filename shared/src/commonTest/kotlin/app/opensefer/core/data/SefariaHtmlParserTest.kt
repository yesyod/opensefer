package app.opensefer.core.data

import app.opensefer.core.model.SpanLink
import app.opensefer.core.model.SpanStyleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

class SefariaHtmlParserTest {

    @Test
    fun emptyString_isEmptyRichText() {
        val rt = SefariaHtmlParser.parse("")
        assertTrue(rt.spans.isEmpty())
        assertTrue(rt.isBlank)
    }

    @Test
    fun plainText_isSingleUnstyledSpan() {
        val rt = SefariaHtmlParser.parse("hello world")
        assertEquals(1, rt.spans.size)
        val span = rt.spans[0]
        assertEquals("hello world", span.text)
        assertTrue(span.styles.isEmpty())
        assertNull(span.link)
        assertEquals("hello world", rt.plainText)
    }

    @Test
    fun boldTag_b_producesBoldSpan() {
        val rt = SefariaHtmlParser.parse("<b>strong text</b>")
        assertEquals(1, rt.spans.size)
        assertEquals("strong text", rt.spans[0].text)
        assertEquals(setOf(SpanStyleType.Bold), rt.spans[0].styles)
    }

    @Test
    fun boldTag_strong_producesBoldSpan() {
        val rt = SefariaHtmlParser.parse("<strong>bold</strong>")
        assertEquals(1, rt.spans.size)
        assertEquals("bold", rt.spans[0].text)
        assertEquals(setOf(SpanStyleType.Bold), rt.spans[0].styles)
    }

    @Test
    fun italicTag_i_producesItalicSpan() {
        val rt = SefariaHtmlParser.parse("<i>slanted</i>")
        assertEquals(1, rt.spans.size)
        assertEquals("slanted", rt.spans[0].text)
        assertEquals(setOf(SpanStyleType.Italic), rt.spans[0].styles)
    }

    @Test
    fun italicTag_em_producesItalicSpan() {
        val rt = SefariaHtmlParser.parse("<em>emphasis</em>")
        assertEquals(1, rt.spans.size)
        assertEquals("emphasis", rt.spans[0].text)
        assertEquals(setOf(SpanStyleType.Italic), rt.spans[0].styles)
    }

    @Test
    fun mixedPlainAndBold_splitsIntoSpans() {
        val rt = SefariaHtmlParser.parse("before <b>mid</b> after")
        assertEquals(3, rt.spans.size)
        assertEquals("before ", rt.spans[0].text)
        assertTrue(rt.spans[0].styles.isEmpty())
        assertEquals("mid", rt.spans[1].text)
        assertEquals(setOf(SpanStyleType.Bold), rt.spans[1].styles)
        assertEquals(" after", rt.spans[2].text)
        assertTrue(rt.spans[2].styles.isEmpty())
    }

    @Test
    fun nestedBoldItalic_carriesBothStyles() {
        val rt = SefariaHtmlParser.parse("<b><i>both</i></b>")
        assertEquals(1, rt.spans.size)
        assertEquals("both", rt.spans[0].text)
        assertEquals(setOf(SpanStyleType.Bold, SpanStyleType.Italic), rt.spans[0].styles)
    }

    @Test
    fun nestedStyles_innerCloseDropsInnerStyleOnly() {
        val rt = SefariaHtmlParser.parse("<b>a<i>b</i>c</b>")
        assertEquals(3, rt.spans.size)
        assertEquals("a", rt.spans[0].text)
        assertEquals(setOf(SpanStyleType.Bold), rt.spans[0].styles)
        assertEquals("b", rt.spans[1].text)
        assertEquals(setOf(SpanStyleType.Bold, SpanStyleType.Italic), rt.spans[1].styles)
        assertEquals("c", rt.spans[2].text)
        assertEquals(setOf(SpanStyleType.Bold), rt.spans[2].styles)
    }

    @Test
    fun supTag_producesSuperscriptSpan() {
        val rt = SefariaHtmlParser.parse("x<sup>2</sup>")
        assertEquals(2, rt.spans.size)
        assertEquals("x", rt.spans[0].text)
        assertTrue(rt.spans[0].styles.isEmpty())
        assertEquals("2", rt.spans[1].text)
        assertEquals(setOf(SpanStyleType.Superscript), rt.spans[1].styles)
    }

    @Test
    fun anchorWithDataRef_producesSpanLinkRef() {
        val rt = SefariaHtmlParser.parse("""see <a data-ref="Genesis 1:1">here</a>""")
        assertEquals(2, rt.spans.size)
        assertEquals("see ", rt.spans[0].text)
        assertNull(rt.spans[0].link)
        val link = rt.spans[1].link
        assertIs<SpanLink.Ref>(link)
        assertEquals("Genesis 1:1", link.tref)
        assertEquals("here", rt.spans[1].text)
    }

    @Test
    fun anchorWithHref_fallsBackToHrefAsRef() {
        val rt = SefariaHtmlParser.parse("""<a href="/Exodus.2">link</a>""")
        assertEquals(1, rt.spans.size)
        val link = rt.spans[0].link
        assertIs<SpanLink.Ref>(link)
        assertEquals("/Exodus.2", link.tref)
    }

    @Test
    fun anchorClose_clearsRefForFollowingText() {
        val rt = SefariaHtmlParser.parse("""<a data-ref="Genesis 1:1">linked</a> plain""")
        assertEquals(2, rt.spans.size)
        assertIs<SpanLink.Ref>(rt.spans[0].link)
        assertNull(rt.spans[1].link)
        assertEquals(" plain", rt.spans[1].text)
    }

    @Test
    fun brTag_producesNewlineSpan() {
        val rt = SefariaHtmlParser.parse("line one<br>line two")
        assertEquals(3, rt.spans.size)
        assertEquals("line one", rt.spans[0].text)
        assertEquals("\n", rt.spans[1].text)
        assertEquals("line two", rt.spans[2].text)
    }

    @Test
    fun htmlEntities_areDecoded() {
        val rt = SefariaHtmlParser.parse("a &amp; b&nbsp;c &#39;quote&#39; &quot;dq&quot;")
        assertEquals("a & b c 'quote' \"dq\"", rt.plainText)
    }

    @Test
    fun footnotePattern_attachesFootnoteToSupSpan() {
        val html = """text<sup class="footnote-marker">1</sup><i class="footnote">the note</i>"""
        val rt = SefariaHtmlParser.parse(html)
        assertEquals(2, rt.spans.size)

        assertEquals("text", rt.spans[0].text)
        assertNull(rt.spans[0].link)

        val supSpan = rt.spans[1]
        assertEquals("1", supSpan.text)
        assertTrue(SpanStyleType.Superscript in supSpan.styles)
        val link = supSpan.link
        assertIs<SpanLink.Footnote>(link)
        assertEquals("the note", link.body)
    }

    @Test
    fun footnoteBody_isNotEmittedAsVisibleText() {
        val html = """word<sup class="footnote-marker">a</sup><i class="footnote">hidden body</i>tail"""
        val rt = SefariaHtmlParser.parse(html)
        // "hidden body" must not appear in any visible span text.
        assertTrue(rt.spans.none { it.text.contains("hidden body") })
        assertEquals("a", rt.spans.first { SpanStyleType.Superscript in it.styles }.text)
        assertEquals("tail", rt.spans.last().text)
    }

    @Test
    fun unknownTag_isIgnoredButInnerTextKept() {
        val rt = SefariaHtmlParser.parse("keep <span class=\"x\">inner</span> text")
        // <span> is unknown -> dropped, inner text preserved. Adjacent text may coalesce or split,
        // but the concatenation must contain everything in order.
        assertEquals("keep inner text", rt.plainText)
        assertTrue(rt.spans.all { it.styles.isEmpty() })
    }

    @Test
    fun unclosedAngleBracket_isTreatedAsLiteralText() {
        // No closing '>' -> the parser keeps the '<' as literal text.
        val rt = SefariaHtmlParser.parse("3 < 5")
        assertEquals("3 < 5", rt.plainText)
    }
}
