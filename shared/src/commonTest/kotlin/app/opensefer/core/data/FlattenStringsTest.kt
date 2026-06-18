package app.opensefer.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class FlattenStringsTest {

    @Test
    fun nullElement_isEmpty() {
        val element: kotlinx.serialization.json.JsonElement? = null
        assertEquals(emptyList(), element.flattenStrings())
    }

    @Test
    fun jsonNull_isEmpty() {
        assertEquals(emptyList(), JsonNull.flattenStrings())
    }

    @Test
    fun stringPrimitive_isSingletonList() {
        assertEquals(listOf("hello"), JsonPrimitive("hello").flattenStrings())
    }

    @Test
    fun blankPrimitive_isPreservedForAlignment() {
        // Blank segments are kept verbatim at the flatten stage so Hebrew/English lists stay
        // positionally aligned (verse i stays at index i). The mapper trims them downstream.
        assertEquals(listOf(""), JsonPrimitive("").flattenStrings())
        assertEquals(listOf("   "), JsonPrimitive("   ").flattenStrings())
    }

    @Test
    fun flatArrayOfStrings_preservesOrder() {
        val arr = buildJsonArray {
            add(JsonPrimitive("a"))
            add(JsonPrimitive("b"))
            add(JsonPrimitive("c"))
        }
        assertEquals(listOf("a", "b", "c"), arr.flattenStrings())
    }

    @Test
    fun nestedArray_isFlattenedDepthFirst() {
        // [["a","b"], ["c"]] -> ["a","b","c"]
        val nested = Json.parseToJsonElement("""[["a","b"],["c"]]""")
        assertEquals(listOf("a", "b", "c"), nested.flattenStrings())
    }

    @Test
    fun arrayWithBlanksAndNulls_preservesPositions() {
        // Blanks stay as "", and a JSON null inside an array becomes "" — both preserve index
        // alignment. Dropping them here would shift later verses onto the wrong numbers.
        val arr = Json.parseToJsonElement("""["keep", "", null, "again"]""")
        assertEquals(listOf("keep", "", "", "again"), arr.flattenStrings())
    }

    @Test
    fun jsonObject_isEmpty() {
        val obj = buildJsonObject { }
        assertEquals(emptyList(), obj.flattenStrings())
    }
}
