package app.opensefer.ui.reader

import app.opensefer.core.domain.ReadingLanguage
import app.opensefer.core.model.Attribution
import app.opensefer.core.model.ChapterText
import app.opensefer.core.model.RichSpan
import app.opensefer.core.model.RichText
import app.opensefer.core.model.Segment
import app.opensefer.core.model.SpanStyleType
import app.opensefer.core.model.TocLeaf
import kotlin.test.Test
import kotlin.test.assertEquals

class CitationTest {

    private val leaf = TocLeaf("Genesis 1", "פרק א", "פרק א", "Genesis.1")

    private fun segment(i: Int, he: String, en: String? = null, rubric: Boolean = false) = Segment(
        index = i,
        label = if (rubric) "" else listOf("א", "ב", "ג", "ד", "ה")[i],
        isRubric = rubric,
        hebrew = RichText.of(he),
        english = en?.let(RichText::of),
        enLabel = if (rubric) "" else "${i + 1}",
    )

    private val chapter = ChapterText(
        tref = "Genesis.1",
        displayTitle = "Genesis 1",
        heDisplayTitle = "בראשית א",
        segments = listOf(
            segment(0, "בְּרֵאשִׁית בָּרָא", "In the beginning"),
            segment(1, "וְהָאָרֶץ", "And the earth"),
            segment(2, "וַיֹּאמֶר", "And He said"),
        ),
        attribution = Attribution(null, null, null),
        ref = "Genesis 1",
        heRef = "בראשית א׳",
    )

    private fun part(vararg indices: Int) = CopyPart(leaf, chapter, indices.map { chapter.segments[it] })

    @Test
    fun aConsecutiveRange_isCitedAsARange() {
        assertEquals("בראשית א׳:א-ב", citation("בראשית", part(0, 1), english = false))
        assertEquals("Genesis 1:1-2", citation("בראשית", part(0, 1), english = true))
    }

    @Test
    fun scatteredSegments_areListed_andASingleOneIsJustItsNumber() {
        assertEquals("בראשית א׳:א, ג", citation("בראשית", part(0, 2), english = false))
        assertEquals("בראשית א׳:ב", citation("בראשית", part(1), english = false))
    }

    @Test
    fun hebrewCopy_honoursTheNikudSetting_andEndsWithTheSource() {
        val text = buildCopyText("בראשית", listOf(part(0)), ReadingLanguage.Hebrew, showNikud = false)

        assertEquals("בראשית ברא\n(בראשית א׳:א)", text)
    }

    @Test
    fun bilingualCopy_keepsEachSegmentsTranslationWithIt() {
        val text = buildCopyText("בראשית", listOf(part(0, 1)), ReadingLanguage.Bilingual, showNikud = true)

        assertEquals(
            "בְּרֵאשִׁית בָּרָא\nIn the beginning\n\nוְהָאָרֶץ\nAnd the earth\n(בראשית א׳:א-ב)",
            text,
        )
    }

    @Test
    fun footnoteMarkers_areLeftOutOfTheCopy() {
        val withMarker = RichText(listOf(RichSpan("טקסט"), RichSpan("1", setOf(SpanStyleType.Superscript))))
        assertEquals("טקסט", withMarker.copyText())
    }

    @Test
    fun placeLabels_nameTheSegmentWhenTheBookSaysWhatItIs() {
        assertEquals("פרק א, פסוק ב", placeLabel(leaf, chapter.segments[1], segmentName = "פסוק"))
        assertEquals("פרק א, ב", placeLabel(leaf, chapter.segments[1], segmentName = null))
        assertEquals("פרק א", placeLabel(leaf, segment(0, "x", rubric = true), segmentName = "פסוק"))
    }

    @Test
    fun snippets_areShortAndWithoutNikud() {
        val long = segment(0, "אָ".repeat(200))
        assertEquals(91, long.snippet().length) // 90 letters + "…"
        assertEquals("בראשית ברא", chapter.segments[0].snippet())
    }
}
