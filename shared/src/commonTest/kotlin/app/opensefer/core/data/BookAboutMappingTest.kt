package app.opensefer.core.data

import app.opensefer.core.network.dto.IndexDto
import app.opensefer.core.network.dto.TopicDto
import app.opensefer.core.network.dto.VersionMetaDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookAboutMappingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // A Rambam‑shaped index: authors as OBJECTS, *String fields as {en, he}, heDesc empty so the
    // description falls back to enDesc, era as a code, heCategories present.
    private val indexFixture = """
        {
          "title": "Mishneh Torah, Foundations of the Torah",
          "heTitle": "משנה תורה, הלכות יסודי התורה",
          "categories": ["Halakhah", "Mishneh Torah", "Sefer Madda"],
          "heCategories": ["הלכה", "משנה תורה", "ספר מדע"],
          "authors": [{"en": "Moses ben Maimon (Rambam)", "he": "רמב\"ם", "slug": "rambam"}],
          "enDesc": "The Mishneh Torah is a monumental legal code.",
          "heDesc": "",
          "era": "RI",
          "compDateString": {"en": " (c.1176 – c.1178 CE)", "he": " (1176 – 1178 לספירה בקירוב)"},
          "compPlaceString": {"en": "Middle-Age Egypt", "he": "מצרים של ימי הביניים"},
          "pubDateString": {"en": " (1474 CE)", "he": " (1474 לספירה)"},
          "pubPlaceString": {"en": "Rome", "he": "רומא"}
        }
    """.trimIndent()

    // Author topic page: property values are wrapped {value, dataSource}; value may be int or string.
    private val topicFixture = """
        {
          "slug": "rambam",
          "primaryTitle": {"en": "Moses ben Maimon (Rambam)", "he": "רמב\"ם"},
          "description": {"en": "Rabbi Moses ben Maimon…", "he": "רבי משה בן מימון…"},
          "properties": {
            "birthYear": {"value": 1137, "dataSource": "sefaria"},
            "deathYear": {"value": 1204, "dataSource": "sefaria"},
            "birthPlace": {"value": "Cordoba, Spain", "dataSource": "sefaria"},
            "deathPlace": {"value": "Fustat, Egypt", "dataSource": "sefaria"},
            "heBio": {"value": "ביוגרפיה קצרה", "dataSource": "sefaria"},
            "enWikiLink": {"value": "https://en.wikipedia.org/wiki/Maimonides", "dataSource": "sefaria"}
          }
        }
    """.trimIndent()

    // /api/texts/versions response: a JSON array; pick the PRIMARY he + primary en.
    private val versionsFixture = """
        [
          {"versionTitle": "Torat Emet 363", "language": "he", "license": "Public Domain", "isPrimary": true, "versionSource": "https://he.example"},
          {"versionTitle": "Sefaria Community Translation", "language": "en", "license": "CC0", "isPrimary": false},
          {"versionTitle": "Mishneh Torah, trans. Eliyahu Touger", "language": "en", "license": "CC-BY-NC", "isPrimary": true}
        ]
    """.trimIndent()

    @Test
    fun index_mapsHebrewFirst_withEnDescFallbackAndEraLabel() {
        val about = json.decodeFromString<IndexDto>(indexFixture).toBookAbout()

        assertEquals("משנה תורה, הלכות יסודי התורה", about.heTitle)
        assertEquals("רמב\"ם", about.authorHe)
        assertEquals("Moses ben Maimon (Rambam)", about.authorEn)
        // heDesc is blank → falls back to enDesc.
        assertEquals("The Mishneh Torah is a monumental legal code.", about.description)
        // era code "RI" → Hebrew label, and *String fields resolve Hebrew‑first (trimmed).
        assertEquals("ראשונים", about.era)
        assertEquals("(1176 – 1178 לספירה בקירוב)", about.compDate)
        assertEquals("מצרים של ימי הביניים", about.compPlace)
        assertEquals("(1474 לספירה)", about.pubDate)
        assertEquals("רומא", about.pubPlace)
        // heCategories preferred over categories.
        assertEquals(listOf("הלכה", "משנה תורה", "ספר מדע"), about.categories)
        // The index mapper alone leaves enrichments empty (filled by the repository).
        assertNull(about.authorBio)
        assertTrue(about.editions.isEmpty())
    }

    @Test
    fun topic_mapsAuthorBio_readingWrappedValues() {
        val bio = json.decodeFromString<TopicDto>(topicFixture).toAuthorBio()

        assertEquals("1137", bio.birthYear) // numeric value rendered as text
        assertEquals("1204", bio.deathYear)
        assertEquals("Cordoba, Spain", bio.birthPlace)
        assertEquals("Fustat, Egypt", bio.deathPlace)
        assertEquals("ביוגרפיה קצרה", bio.bio) // heBio preferred
        assertEquals("https://en.wikipedia.org/wiki/Maimonides", bio.wikiLink) // heWikiLink absent → enWikiLink
    }

    @Test
    fun versions_pickPrimaryHebrewAndEnglishEditions() {
        val editions = json.decodeFromString<List<VersionMetaDto>>(versionsFixture).toEditions()

        assertEquals(2, editions.size)
        assertEquals("he", editions[0].language)
        assertEquals("Torat Emet 363", editions[0].title)
        assertEquals("Public Domain", editions[0].license)
        assertEquals("en", editions[1].language)
        // The PRIMARY English edition (Touger), not the first one in the list.
        assertEquals("Mishneh Torah, trans. Eliyahu Touger", editions[1].title)
        assertEquals("CC-BY-NC", editions[1].license)
    }
}
