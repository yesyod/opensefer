package app.opensefer.core.data

import app.opensefer.core.model.TocBranch
import app.opensefer.core.network.dto.IndexDto
import app.opensefer.core.network.dto.V3TextResponseDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Book shapes beyond plain chapters: Talmud dapim, default nodes, chaptered sections, commentaries. */
class StructureMappingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun contents(fixture: String) = json.decodeFromString<IndexDto>(fixture).toBookContents()

    @Test
    fun talmudTractate_isAddressedByDafAndAmud_fromDafTwo() {
        // Berakhot ends on 64a: lengths[0] counts amudim from 1a, so 64a is section 127.
        val c = contents(
            """{"title": "Berakhot", "heTitle": "ברכות",
                "schema": {"depth": 2, "addressTypes": ["Talmud", "Integer"], "sectionNames": ["Daf", "Line"],
                           "heSectionNames": ["דף", "שורה"], "lengths": [127, 4000]}}""",
        )

        assertEquals("Berakhot.2a", c.leaves.first().tref)
        assertEquals("Berakhot.2b", c.leaves[1].tref)
        assertEquals("Berakhot.64a", c.leaves.last().tref)
        assertEquals(125, c.leaves.size)
        assertEquals("דף ב׳ ע״א", c.leaves.first().heTitle)
        assertEquals("דף ב׳ ע״ב", c.leaves[1].heTitle)
    }

    @Test
    fun defaultNode_addsNothingToTheRef_andItsChaptersSitUnderTheBook() {
        val c = contents(
            """{"title": "Mesillat Yesharim", "heTitle": "מסילת ישרים",
                "schema": {"nodes": [
                  {"title": "Introduction", "heTitle": "הקדמה", "depth": 1},
                  {"title": "", "default": true, "depth": 2, "sectionNames": ["Chapter", "Paragraph"],
                   "heSectionNames": ["פרק", "פסקה"], "lengths": [26, 300]}
                ]}}""",
        )

        assertEquals("Mesillat Yesharim, Introduction", c.leaves.first().tref)
        assertEquals("Mesillat_Yesharim.1", c.leaves[1].tref) // never "Mesillat Yesharim, "
        assertEquals(27, c.leaves.size)
        assertTrue(c.root.children.none { it is TocBranch && it.title.isBlank() })
        assertTrue(c.leaves.none { it.tref.endsWith(", ") })
    }

    @Test
    fun aChapteredSectionOfAComplexBook_expandsIntoItsChapters() {
        val c = contents(
            """{"title": "Tanya", "heTitle": "תניא",
                "schema": {"nodes": [
                  {"title": "Part I; Likkutei Amarim", "heTitle": "חלק ראשון; לקוטי אמרים", "depth": 2,
                   "sectionNames": ["Chapter", "Paragraph"], "heSectionNames": ["פרק", "פסקה"], "lengths": [53, 500]}
                ]}}""",
        )

        val part = c.root.children.single() as TocBranch
        assertEquals(53, part.children.size)
        assertEquals("Tanya,_Part_I;_Likkutei_Amarim.1", c.leaves.first().tref)
        assertEquals("חלק ראשון; לקוטי אמרים › פרק א׳", c.leaves.first().crumb)
    }

    @Test
    fun commentary_alignsHebrewAndEnglishByPosition_andLabelsVerseColonComment() {
        // Rashi‑shaped chapter: [verse][comment]. Hebrew has two comments on verse 1, English one.
        val dto = json.decodeFromString<V3TextResponseDto>(
            """{"ref": "Rashi on Genesis 1", "heRef": "רש״י על בראשית א׳", "versions": [
                 {"language": "he", "versionTitle": "he", "text": [["בראשית — אמר", "ברא — שני"], ["והארץ"]]},
                 {"language": "en", "versionTitle": "en", "text": [["In the beginning"], ["And the earth"]]}
               ]}""",
        )

        val chapter = dto.toChapterText("Rashi_on_Genesis.1")

        assertEquals(listOf("א:א", "א:ב", "ב:א"), chapter.segments.map { it.label })
        assertEquals(listOf("1:1", "1:2", "2:1"), chapter.segments.map { it.enLabel })
        assertEquals("In the beginning", chapter.segments[0].english?.plainText)
        assertEquals(null, chapter.segments[1].english) // the second comment has no translation
        assertEquals("And the earth", chapter.segments[2].english?.plainText) // still on the right comment
    }

    @Test
    fun aMissingVerse_doesNotRenumberTheVersesAfterIt() {
        val dto = json.decodeFromString<V3TextResponseDto>(
            """{"versions": [{"language": "he", "versionTitle": "he", "text": ["א", "", "ג"]}]}""",
        )

        val chapter = dto.toChapterText("Book.1")

        assertEquals(listOf("א", "ג"), chapter.segments.map { it.label }) // verse 3 stays 3
        assertEquals(listOf(0, 2), chapter.segments.map { it.index })
    }

    @Test
    fun sourceVersionInAramaic_fillsTheOriginalLanguageSlot() {
        val dto = json.decodeFromString<V3TextResponseDto>(
            """{"versions": [
                 {"language": "he", "versionTitle": "William Davidson Edition - Aramaic", "text": ["מתני׳ מאימתי"]},
                 {"language": "en", "versionTitle": "William Davidson Edition - English", "text": ["MISHNA: From when"]}
               ]}""",
        )

        val segment = dto.toChapterText("Berakhot.2a").segments.single()
        assertEquals("מתני׳ מאימתי", segment.hebrew?.plainText)
        assertEquals("MISHNA: From when", segment.english?.plainText)
    }

    @Test
    fun numericEntities_areDecoded() {
        assertEquals("א' — \"x\"", SefariaHtmlParser.parse("&#1488;&#39; &#x2014; &quot;x&quot;").plainText)
        assertEquals("&#39;", SefariaHtmlParser.parse("&amp;#39;").plainText) // double‑escaped stays literal
        assertEquals("&lt;b&gt;", SefariaHtmlParser.parse("&#38;lt;b&amp;gt;").plainText) // one pass, never two
        assertEquals("a — b… &bogus;", SefariaHtmlParser.parse("a &mdash; b&hellip; &bogus;").plainText)
    }
}
