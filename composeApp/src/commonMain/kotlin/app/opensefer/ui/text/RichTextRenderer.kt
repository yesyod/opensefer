package app.opensefer.ui.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.em
import app.opensefer.core.model.RichSpan
import app.opensefer.core.model.RichText
import app.opensefer.core.model.SpanLink
import app.opensefer.core.model.SpanStyleType

/**
 * Renders a framework‑neutral [RichText] into a Compose [AnnotatedString]. This is the only place
 * Sefaria's parsed markup meets Compose. Footnote bodies are shown inline (small italic) for the
 * MVP; promoting them to a tap‑to‑open sheet is a clean follow‑up (the data already carries them).
 */
fun RichText.toAnnotatedString(secondaryColor: Color): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        pushStyle(span.spanStyle())
        append(span.text)
        pop()
        (span.link as? SpanLink.Footnote)?.let { footnote ->
            pushStyle(SpanStyle(fontSize = 0.82.em, fontStyle = FontStyle.Italic, color = secondaryColor))
            append(" (")
            append(footnote.body)
            append(")")
            pop()
        }
    }
}

private fun RichSpan.spanStyle(): SpanStyle {
    var weight: FontWeight? = null
    var style: FontStyle? = null
    var size = 1.0f
    var baseline = BaselineShift.None

    styles.forEach { s ->
        when (s) {
            SpanStyleType.Bold -> weight = FontWeight.Bold
            SpanStyleType.Italic -> style = FontStyle.Italic
            SpanStyleType.Big -> size *= 1.15f
            SpanStyleType.Small -> size *= 0.85f
            SpanStyleType.Superscript -> {
                size *= 0.75f
                baseline = BaselineShift.Superscript
            }
        }
    }
    return SpanStyle(
        fontWeight = weight,
        fontStyle = style,
        fontSize = size.em,
        baselineShift = baseline,
    )
}
