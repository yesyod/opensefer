package app.opensefer.core.data

import kotlin.test.Test
import kotlin.test.assertEquals

class HebrewNumeralsTest {

    @Test
    fun ones() {
        assertEquals("א", HebrewNumerals.toHebrew(1))
        assertEquals("ט", HebrewNumerals.toHebrew(9))
    }

    @Test
    fun tens() {
        assertEquals("י", HebrewNumerals.toHebrew(10))
        assertEquals("יא", HebrewNumerals.toHebrew(11))
    }

    @Test
    fun specialFifteenAndSixteen() {
        // 15 and 16 use טו / טז (not יה / יו) to avoid divine-name letter combinations.
        assertEquals("טו", HebrewNumerals.toHebrew(15))
        assertEquals("טז", HebrewNumerals.toHebrew(16))
    }

    @Test
    fun twenties() {
        assertEquals("כא", HebrewNumerals.toHebrew(21))
    }

    @Test
    fun hundreds() {
        assertEquals("ק", HebrewNumerals.toHebrew(100))
        assertEquals("קטו", HebrewNumerals.toHebrew(115))
    }

    @Test
    fun fourHundredAndBeyond() {
        assertEquals("ת", HebrewNumerals.toHebrew(400))
        assertEquals("תיא", HebrewNumerals.toHebrew(411))
    }

    @Test
    fun nonPositive_fallsBackToDecimalString() {
        assertEquals("0", HebrewNumerals.toHebrew(0))
        assertEquals("-3", HebrewNumerals.toHebrew(-3))
    }
}
