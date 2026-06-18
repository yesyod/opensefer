package app.opensefer.core.data

/**
 * Converts integers to Hebrew numerals (gematria) for segment/chapter labels — e.g. 1 → "א",
 * 15 → "טו", 16 → "טז", 21 → "כא". Handles the special 15/16 cases (טו/טז, not יה/יו).
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
}
