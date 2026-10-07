package app.opensefer.ui.text

import app.opensefer.core.model.RichText

/**
 * Hebrew vowel points and cantillation marks — U+0591–U+05C7 *except* the punctuation that lives in
 * the same block: maqaf ־ (U+05BE), paseq ׀ (U+05C0), sof pasuq ׃ (U+05C3) and nun hafukha ׆ (U+05C6).
 * Stripping the maqaf would glue words together ("אֶת־הָאוֹר" → "אתהאור").
 */
private val NikudRegex = Regex("[\\u0591-\\u05BD\\u05BF\\u05C1\\u05C2\\u05C4\\u05C5\\u05C7]")

fun String.stripNikud(): String = replace(NikudRegex, "")

/** Returns the text with vowel/cantillation marks removed when [show] is false (client‑side toggle,
 *  BLUEPRINT §7.3) — more reliable than depending on a consonantal edition existing per book. */
fun RichText.withNikud(show: Boolean): RichText =
    if (show) this else RichText(spans.map { it.copy(text = it.text.stripNikud()) })
