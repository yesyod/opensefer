package app.opensefer.core.data

/**
 * Converts integers to Hebrew numerals (gematria) for segment/chapter labels — e.g. 1 → "א",
 * 15 → "טו", 16 → "טז", 21 → "כא". Handles the special 15/16 cases (טו/טז, not יה/יו). Bare letters
 * suit the margin and the chapter grid; [punctuate] gives the form used in running text ("כ״א").
 */
object HebrewNumerals {

    private val ones = listOf("", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט")
    private val tens = listOf("", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ")
    private val hundreds = listOf("", "ק", "ר", "ש", "ת")

    fun toHebrew(value: Int): String {
        if (value <= 0) return value.toString()
        val sb = StringBuilder()
        var n = value

        while (n >= 400) {
            sb.append("ת")
            n -= 400
        }
        if (n >= 100) {
            sb.append(hundreds[n / 100])
            n %= 100
        }
        when (n) {
            15 -> { sb.append("טו"); n = 0 }
            16 -> { sb.append("טז"); n = 0 }
        }
        if (n >= 10) {
            sb.append(tens[n / 10])
            n %= 10
        }
        if (n in 1..9) {
            sb.append(ones[n])
        }
        return sb.toString()
    }

    /**
     * A bare numeral as written in running text: a geresh after a single letter (א׳), gershayim
     * before the last of several (ל״א, קכ״ו) — so "פרק לא" (chapter 31) can't be misread as "no".
     * Each part of a multi‑level label is marked on its own ("א:ב" → "א׳:ב׳"); anything that isn't a
     * Hebrew numeral is returned as it is.
     */
    fun punctuate(numeral: String): String = numeral.split(':').joinToString(":") { part ->
        when {
            part.isEmpty() || part.any { it !in HEBREW_LETTERS } -> part
            part.length == 1 -> part + GERESH
            else -> part.dropLast(1) + GERSHAYIM + part.last()
        }
    }

    private val HEBREW_LETTERS = '\u05D0'..'\u05EA'
    private const val GERESH = '\u05F3'
    private const val GERSHAYIM = '\u05F4'
}
