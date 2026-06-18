package app.opensefer.ui.text

import app.opensefer.core.model.RichText

/** Hebrew combining marks (nikud + cantillation): U+0591–U+05C7. */
private val NikudRegex = Regex("[\\u0591-\\u05C7]")

fun String.stripNikud(): String = replace(NikudRegex, "")

/** Returns the text with vowel/cantillation marks removed when [show] is false (client‑side toggle,
 *  BLUEPRINT §7.3) — more reliable than depending on a consonantal edition existing per book. */
fun RichText.withNikud(show: Boolean): RichText =
    if (show) this else RichText(spans.map { it.copy(text = it.text.stripNikud()) })
