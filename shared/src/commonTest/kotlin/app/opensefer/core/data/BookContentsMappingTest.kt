package app.opensefer.core.data

import app.opensefer.core.network.dto.IndexDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookContentsMappingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // A SIMPLE (JaggedArrayNode) book: depth 2, 10 chapters.
    private val simpleIndex = """
        {
          "title": "Mishneh Torah, Repentance",
          "heTitle": "משנה תורה, הלכות תשובה",
          "schema": {
            "depth": 2,
            "sectionNames": ["Chapter", "Halacha"],
            "heSectionNames": ["פרק", "הלכה"],
            "lengths": [10, 70]
          }
        }
    """.trimIndent()

    // A COMPLEX (SchemaNode) book — a Siddur‑shaped tree of named nodes down to a leaf.
    private val complexIndex = """
        {
          "title": "Siddur Ashkenaz",
          "heTitle": "סידור אשכנז",
          "schema": {
            "title": "Siddur Ashkenaz",
            "heTitle": "סידור אשכנז",
            "nodes": [
              {
                "title": "Weekday", "heTitle": "ימי חול",
                "nodes": [
                  {
                    "title": "Shacharit", "heTitle": "שחרית",
                    "nodes": [
                      {
                        "title": "Modeh Ani", "heTitle": "מודה אני",
                        "nodeType": "JaggedArrayNode", "depth": 1,
                        "addressTypes": ["Integer"], "sectionNames": ["Paragraph"]
                      }
                    ]
                  }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    private fun contents(fixture: String) =
        json.decodeFromString<IndexDto>(fixture).toBookContents()

    @Test
    fun simpleBook_yieldsNumberedChapterLeaves() {
        val c = contents(simpleIndex)
        assertFalse(c.isComplex)
        assertEquals(10, c.leaves.size)
        val first = c.leaves.first()
        assertEquals("Mishneh_Torah,_Repentance.1", first.tref)
        assertEquals("פרק א", first.heTitle)
    }

    @Test
    fun complexBook_leafCarriesFullCommaJoinedRefPath() {
        val c = contents(complexIndex)
        assertTrue(c.isComplex)
        assertEquals(1, c.leaves.size)
        val leaf = c.leaves.single()
        // The full ancestor path is required by Sefaria (verified against the live API).
        assertEquals("Siddur Ashkenaz, Weekday, Shacharit, Modeh Ani", leaf.tref)
        assertEquals("מודה אני", leaf.heTitle)
        assertEquals("ימי חול › שחרית › מודה אני", leaf.crumb)
    }
}
