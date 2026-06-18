package app.opensefer.core.data

import app.opensefer.core.model.SpanStyleType
import app.opensefer.core.network.dto.V3TextResponseDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChapterMappingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val tref = "Mishneh_Torah,_Foundations_of_the_Torah.1"

    // Mirrors the real Sefaria /api/v3/texts shape: he + en versions, jagged `text` arrays.
    private val fixture = """
        {
          "ref": "Mishneh Torah, Foundations of the Torah 1",
          "heRef": "משנה תורה, הלכות יסודי התורה א",
          "title": "Mishneh Torah, Foundations of the Torah",
          "heTitle": "משנה תורה, הלכות יסודי התורה",
          "versions": [
            {
              "language": "he",
              "versionTitle": "Torat Emet 363",
              "license": "Public Domain",
              "text": ["<b>יְסוֹד</b> הַיְסוֹדוֹת", "וִידִיעַת"]
            },
            {
              "language": "en",
              "versionTitle": "Mishneh Torah, trans. by Eliyahu Touger",
              "text": ["The <b>foundation</b>", "And to know"]
            }
          ]
        }
    """.trimIndent()

    private fun decode(): V3TextResponseDto = json.decodeFromString(fixture)

    @Test
    fun fixtureDeserializes_withTwoVersions() {
        val dto = decode()
        assertEquals(2, dto.versions.size)
        assertEquals("he", dto.versions[0].language)
        assertEquals("en", dto.versions[1].language)
    }

    @Test
    fun toChapterText_producesTwoSegments_andCarriesTref() {
        val chapter = decode().toChapterText(tref)
        assertEquals(2, chapter.segments.size)
        assertEquals(tref, chapter.tref)
    }

    @Test
    fun firstSegment_hasHebrewLabelAleph() {
        val chapter = decode().toChapterText(tref)
        assertEquals("א", chapter.segments[0].label)
        assertEquals("ב", chapter.segments[1].label)
        assertEquals(0, chapter.segments[0].index)
        assertEquals(1, chapter.segments[1].index)
    }

    @Test
    fun firstSegment_hebrewParsedWithLeadingBoldSpan() {
        val chapter = decode().toChapterText(tref)
        val hebrew = chapter.segments[0].hebrew
        assertNotNull(hebrew)
        val firstSpan = hebrew.spans.first()
        assertEquals("יְסוֹד", firstSpan.text)
        assertTrue(SpanStyleType.Bold in firstSpan.styles)
        // Plain text should include the trailing non-bold portion too.
        assertEquals("יְסוֹד הַיְסוֹדוֹת", hebrew.plainText)
    }

    @Test
    fun firstSegment_englishIsPresentAndParsed() {
        val chapter = decode().toChapterText(tref)
        val english = chapter.segments[0].english
        assertNotNull(english)
        assertEquals("The foundation", english.plainText)
        assertTrue(english.spans.any { it.text == "foundation" && SpanStyleType.Bold in it.styles })
    }

    @Test
    fun attribution_carriesVersionTitles() {
        val chapter = decode().toChapterText(tref)
        assertEquals("Torat Emet 363", chapter.attribution.hebrewVersion)
        assertEquals("Mishneh Torah, trans. by Eliyahu Touger", chapter.attribution.englishVersion)
        assertEquals("Public Domain", chapter.attribution.license)
    }

    @Test
    fun displayTitles_comeFromResponse() {
        val chapter = decode().toChapterText(tref)
        assertEquals("Mishneh Torah, Foundations of the Torah", chapter.displayTitle)
        assertEquals("משנה תורה, הלכות יסודי התורה", chapter.heDisplayTitle)
    }

    // A Siddur leaf interleaves `<small>` instruction rubrics with `<b>`-opening prayers.
    private val rubricFixture = """
        {
          "title": "Siddur", "heTitle": "סידור",
          "versions": [
            { "language": "he", "versionTitle": "v", "text": [
              "<small>מיד כשיעור משנתו, בעודו על משכבו, יאמר:</small>",
              "<b>מוֹדֶה אֲנִי</b> לְפָנֶיךָ",
              "<small>לאחר שנטל ידיו יאמר:</small>",
              "<b>רֵאשִׁית חָכְמָה</b> יִרְאַת יְהֹוָה"
            ] }
          ]
        }
    """.trimIndent()

    @Test
    fun rubrics_areUnnumbered_andPrayersNumberConsecutively() {
        val chapter = json.decodeFromString<V3TextResponseDto>(rubricFixture).toChapterText("Siddur.1")
        assertEquals(4, chapter.segments.size)
        // Instruction rubrics: flagged, de-emphasised, and NOT numbered.
        assertTrue(chapter.segments[0].isRubric)
        assertEquals("", chapter.segments[0].label)
        assertTrue(chapter.segments[2].isRubric)
        assertEquals("", chapter.segments[2].label)
        // Prayers number consecutively, skipping the rubrics in between.
        assertEquals("א", chapter.segments[1].label)
        assertEquals("ב", chapter.segments[3].label)
    }
}
