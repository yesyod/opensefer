package app.opensefer.core.data

import app.opensefer.core.model.ReadingHeading
import app.opensefer.core.model.ReadingPassage
import app.opensefer.core.model.indexOfPassage
import app.opensefer.core.model.toReadingItems
import app.opensefer.core.network.dto.IndexDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadingListTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val simpleIndex = """
        {
          "title": "Mishneh Torah, Repentance",
          "heTitle": "משנה תורה, הלכות תשובה",
          "schema": { "depth": 2, "sectionNames": ["Chapter", "Halacha"],
                      "heSectionNames": ["פרק", "הלכה"], "lengths": [3, 70] }
        }
    """.trimIndent()

    private val complexIndex = """
        {
          "title": "Siddur Ashkenaz", "heTitle": "סידור אשכנז",
          "schema": { "title": "Siddur Ashkenaz", "heTitle": "סידור אשכנז", "nodes": [
            { "title": "Weekday", "heTitle": "ימי חול", "nodes": [
              { "title": "Shacharit", "heTitle": "שחרית", "nodes": [
                { "title": "Modeh Ani", "heTitle": "מודה אני", "nodeType": "JaggedArrayNode",
                  "depth": 1, "addressTypes": ["Integer"], "sectionNames": ["Paragraph"] },
                { "title": "Asher Yatzar", "heTitle": "אשר יצר", "nodeType": "JaggedArrayNode",
                  "depth": 1, "addressTypes": ["Integer"], "sectionNames": ["Paragraph"] }
              ] }
            ] }
          ] } }
    """.trimIndent()

    private fun items(fixture: String) =
        json.decodeFromString<IndexDto>(fixture).toBookContents().toReadingItems()

    @Test
    fun simpleBook_isAllPassages_noHeadings() {
        val items = items(simpleIndex)
        assertEquals(3, items.size)
        assertTrue(items.all { it is ReadingPassage })
        assertEquals("Mishneh_Torah,_Repentance.1", (items.first() as ReadingPassage).leaf.tref)
    }

    @Test
    fun complexBook_interleavesHeadingsAndPassagesInReadingOrder() {
        val items = items(complexIndex)
        // ימי חול, שחרית (headings) then the two prayers (passages) — root branch skipped.
        assertEquals(4, items.size)
        assertEquals(listOf("ימי חול", "שחרית"), items.filterIsInstance<ReadingHeading>().map { it.heTitle })
        assertEquals(
            listOf("מודה אני", "אשר יצר"),
            items.filterIsInstance<ReadingPassage>().map { it.leaf.heTitle },
        )
        // The jump index resolves a leaf's tref to its position in the stream.
        assertEquals(
            3,
            items.indexOfPassage("Siddur Ashkenaz, Weekday, Shacharit, Asher Yatzar"),
        )
    }
}
