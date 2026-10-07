package app.opensefer.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Front covers are a little taller than wide, like a real volume. */
const val COVER_ASPECT_RATIO = 0.68f

/** Hebrew books bind on the right: a gentle curve on the spine side, a crisp fore‑edge on the left. */
private val CoverShape = AbsoluteRoundedCornerShape(topLeft = 2.dp, bottomLeft = 2.dp, topRight = 6.dp, bottomRight = 6.dp)

/**
 * A generated front cover — no image assets: a leather board in the book's category colour (Sefaria's
 * palette, deepened so gold lettering reads), the spine on the right, a double gold frame with corner
 * ornaments, and the title in gold, auto‑sized to fit. Everything is drawn in one cached draw pass
 * (no sub‑composition), so a grid of covers scrolls smoothly.
 *
 * [compact] covers (thumbnails) show only the title.
 */
@Composable
fun BookCover(
    heTitle: String,
    category: String?,
    heCategory: String?,
    heAuthor: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    Box(
        modifier
            .aspectRatio(COVER_ASPECT_RATIO)
            .shadow(elevation = if (compact) 3.dp else 6.dp, shape = CoverShape)
            .clip(CoverShape)
            .semantics { contentDescription = listOfNotNull(heTitle, heAuthor).joinToString(", ") }
            .drawWithCache {
                val palette = CoverPalette.of(category, heTitle)
                val design = CoverLayout(size, this, measurer, heTitle, heCategory, heAuthor, compact)
                onDrawBehind {
                    drawBoard(palette)
                    drawFrame(design.frameInset, design.spineWidth, compact)
                    design.draw(this)
                }
            },
    )
}

/** The board colours for one book. */
private class CoverPalette(val base: Color, val light: Color, val dark: Color, val spine: Color) {
    companion object {
        private val cache = HashMap<String, CoverPalette>()

        fun of(category: String?, title: String): CoverPalette {
            val key = "${category.orEmpty()}|${title.hashCode() % VARIANTS}"
            return cache.getOrPut(key) { build(category, title) }
        }

        private fun build(category: String?, title: String): CoverPalette {
            val hue = category?.let { CategoryColors[it] } ?: FallbackColors[abs(title.hashCode()) % FallbackColors.size]
            // Deepen to a dark leather tone (Sefaria's lighter hues can't carry gold lettering)…
            val deep = darkenTo(hue, TARGET_LUMINANCE)
            // …then a small, title‑derived variation so two books of one category aren't identical twins.
            val variation = ((abs(title.hashCode()) % VARIANTS) - VARIANTS / 2) / (VARIANTS * 12f)
            val base = if (variation >= 0) lerp(deep, Color.White, variation) else lerp(deep, Color.Black, -variation)
            return CoverPalette(
                base = base,
                light = lerp(base, Color.White, 0.10f),
                dark = lerp(base, Color.Black, 0.28f),
                spine = lerp(base, Color.Black, 0.22f),
            )
        }

        private fun darkenTo(color: Color, target: Float): Color {
            var k = 0f
            var out = color
            while (out.luminance() > target && k < MAX_DARKEN) {
                k += DARKEN_STEP
                out = lerp(color, Color.Black, k)
            }
            return out
        }

        private const val VARIANTS = 7
        private const val TARGET_LUMINANCE = 0.055f
        private const val MAX_DARKEN = 0.8f
        private const val DARKEN_STEP = 0.04f

        /** Sefaria's category palette (top‑level categories). */
        private val CategoryColors = mapOf(
            "Tanakh" to Color(0xFF004E5F),
            "Mishnah" to Color(0xFF5A99B7),
            "Talmud" to Color(0xFFCCB479),
            "Midrash" to Color(0xFF5D956F),
            "Halakhah" to Color(0xFF802F3E),
            "Kabbalah" to Color(0xFF594176),
            "Liturgy" to Color(0xFFAB4E66),
            "Jewish Thought" to Color(0xFF7F85A9),
            "Chasidut" to Color(0xFF97B386),
            "Musar" to Color(0xFF7C416F),
            "Responsa" to Color(0xFFCB6158),
            "Reference" to Color(0xFFD4896C),
            "Tosefta" to Color(0xFF00827F),
            "Second Temple" to Color(0xFFC6A7B4),
            "Targum" to Color(0xFF3B5849),
        )

        private val FallbackColors = listOf(Color(0xFF18345D), Color(0xFF3B5849), Color(0xFF6B2D3A), Color(0xFF4A3B2A))
    }
}

private val GoldBrush = listOf(Color(0xFF8C6A1E), Color(0xFFE9CF7A), Color(0xFFB08A35), Color(0xFFF3DE95))
private val Gold = Color(0xFFE6C878)

private fun DrawScope.drawBoard(palette: CoverPalette) {
    val spineWidth = size.width * SPINE_FRACTION
    // Board: a soft diagonal light, top‑right to bottom‑left, plus a darker vignette at the edges.
    drawRect(Brush.linearGradient(listOf(palette.light, palette.base, palette.dark), Offset(size.width, 0f), Offset(0f, size.height)))
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)),
            center = Offset(size.width * 0.45f, size.height * 0.45f),
            radius = size.maxDimension * 0.75f,
        ),
    )
    // Spine (right): darker leather, a hinge highlight and shadow, and gold bands top and bottom.
    val spineLeft = size.width - spineWidth
    drawRect(palette.spine, topLeft = Offset(spineLeft, 0f), size = Size(spineWidth, size.height))
    val hinge = 6.dp.toPx()
    drawRect(
        Brush.horizontalGradient(
            listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent),
            startX = spineLeft - hinge,
            endX = spineLeft,
        ),
        topLeft = Offset(spineLeft - hinge, 0f),
        size = Size(hinge, size.height),
    )
    val highlightX = spineLeft + 1.dp.toPx()
    drawLine(Color.White.copy(alpha = 0.14f), Offset(highlightX, 0f), Offset(highlightX, size.height), 1.dp.toPx())
    val bandBrush = Brush.verticalGradient(GoldBrush)
    val bandSize = Size(spineWidth - 4.dp.toPx(), 1.2.dp.toPx())
    listOf(0.08f, 0.11f, 0.89f, 0.92f).forEach { y ->
        drawRect(bandBrush, topLeft = Offset(spineLeft + 2.dp.toPx(), size.height * y), size = bandSize)
    }
}

private fun DrawScope.drawFrame(inset: Float, spineWidth: Float, compact: Boolean) {
    val gold = Brush.linearGradient(GoldBrush, Offset.Zero, Offset(size.width, size.height))
    val right = size.width - spineWidth
    val outer = Size(right - inset * 2, size.height - inset * 2)
    drawRoundRect(gold, Offset(inset, inset), outer, CornerRadius(2.dp.toPx()), style = Stroke(1.2.dp.toPx()))
    if (compact) return
    val gap = 3.dp.toPx()
    drawRoundRect(
        gold,
        Offset(inset + gap, inset + gap),
        Size(outer.width - gap * 2, outer.height - gap * 2),
        CornerRadius(1.dp.toPx()),
        style = Stroke(0.6.dp.toPx()),
    )
    // Corner ornaments: small gold diamonds on the outer rule's corners.
    val r = 3.dp.toPx()
    val bottom = size.height - inset
    listOf(Offset(inset, inset), Offset(right - inset, inset), Offset(inset, bottom), Offset(right - inset, bottom))
        .forEach { c -> drawPath(diamond(c, r), gold) }
}

private fun diamond(center: Offset, r: Float) = Path().apply {
    moveTo(center.x, center.y - r)
    lineTo(center.x + r, center.y)
    lineTo(center.x, center.y + r)
    lineTo(center.x - r, center.y)
    close()
}

private const val SPINE_FRACTION = 0.09f

/**
 * The cover's text, measured once per size: `משנה תורה, הלכות תשובה` → a small series line ("משנה
 * תורה") above a large title ("הלכות תשובה"), the category above and the author below. The title is
 * the largest size (within a range) that fits in four lines.
 */
private class CoverLayout(
    private val area: Size,
    density: Density,
    measurer: TextMeasurer,
    heTitle: String,
    heCategory: String?,
    heAuthor: String?,
    compact: Boolean,
) {
    val spineWidth: Float
    val frameInset: Float
    private val textWidth: Int
    private val top: TextLayoutResult?
    private val series: TextLayoutResult?
    private val title: TextLayoutResult
    private val bottom: TextLayoutResult?

    init {
        with(density) {
            spineWidth = area.width * SPINE_FRACTION
            frameInset = (if (compact) 4.dp else 7.dp).toPx()
            val padding = (if (compact) 4.dp else 10.dp).toPx()
            textWidth = (area.width - spineWidth - frameInset * 2 - padding * 2).toInt().coerceAtLeast(1)

            val (seriesText, titleText) = splitTitle(heTitle)
            val small = if (compact) null else (area.width / 13f).toSp().value.coerceIn(SMALL_MIN_SP, SMALL_MAX_SP).sp
            top = if (small != null && heCategory != null) measure(measurer, heCategory, small, alpha = 0.8f) else null
            bottom = if (small != null && heAuthor != null) measure(measurer, heAuthor, small, alpha = 0.85f) else null
            series = if (seriesText != null) measure(measurer, seriesText, (small ?: COMPACT_SERIES_SP.sp) * 1.15f, 0.9f) else null

            val reserved = listOfNotNull(top, bottom, series).sumOf { it.size.height } + frameInset * 4
            val maxHeight = (area.height - reserved).toInt().coerceAtLeast(1)
            val largest = (area.width / (if (compact) 6f else 5.5f)).toSp()
            val smallest = (area.width / (if (compact) 14f else 13f)).toSp()
            title = fitTitle(measurer, titleText, largest, smallest, maxHeight)
        }
    }

    /**
     * A small line (category, series, author): shrunk until every word fits whole — never "משנ/ה" —
     * or left out (null) on a cover too small for it.
     */
    private fun Density.measure(measurer: TextMeasurer, text: String, size: TextUnit, alpha: Float): TextLayoutResult? {
        val style = { sp: Float -> coverStyle(sp.sp, alpha, bold = false) }
        val layout = fitting(measurer, text, size.value, SMALL_MIN_SP, maxLines = 2, style = style)
        return layout.takeIf { wordsFit(measurer, text, layout.layoutInput.style) }
    }

    /** The title: the largest size, down to [smallest], at which it fits four lines and [maxHeight]. */
    private fun Density.fitTitle(
        measurer: TextMeasurer,
        text: String,
        largest: TextUnit,
        smallest: TextUnit,
        maxHeight: Int,
    ): TextLayoutResult = fitting(measurer, text, largest.value, smallest.value, TITLE_MAX_LINES, maxHeight) {
        coverStyle(it.sp, alpha = 1f, bold = true)
    }

    private fun Density.fitting(
        measurer: TextMeasurer,
        text: String,
        largestSp: Float,
        smallestSp: Float,
        maxLines: Int,
        maxHeight: Int = Int.MAX_VALUE,
        style: (Float) -> TextStyle,
    ): TextLayoutResult {
        val words = text.words()
        var size = largestSp
        while (true) {
            val textStyle = style(size)
            val layout = measurer.measure(
                text = text,
                style = textStyle,
                constraints = Constraints(maxWidth = textWidth),
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                density = this,
            )
            // A word wider than the line would be broken mid‑word: that doesn't count as fitting.
            val fits = wordsFit(measurer, words, textStyle) && !layout.hasVisualOverflow && layout.size.height <= maxHeight
            if (fits || size <= smallestSp) return layout
            size -= SIZE_STEP_SP
        }
    }

    private fun Density.wordsFit(measurer: TextMeasurer, text: String, style: TextStyle) =
        wordsFit(measurer, text.words(), style)

    private fun Density.wordsFit(measurer: TextMeasurer, words: List<String>, style: TextStyle) = words.all { word ->
        measurer.measure(word, style, softWrap = false, maxLines = 1, density = this).size.width <= textWidth
    }

    private fun String.words() = split(' ').filter { it.isNotEmpty() }

    fun draw(scope: DrawScope) = with(scope) {
        val boardWidth = area.width - spineWidth
        fun centeredX(layout: TextLayoutResult) = (boardWidth - layout.size.width) / 2f
        top?.let { drawText(it, topLeft = Offset(centeredX(it), frameInset * 2.2f)) }
        bottom?.let { drawText(it, topLeft = Offset(centeredX(it), area.height - frameInset * 2.2f - it.size.height)) }
        val blockHeight = (series?.size?.height ?: 0) + title.size.height
        var y = (area.height - blockHeight) / 2f
        series?.let {
            drawText(it, topLeft = Offset(centeredX(it), y))
            y += it.size.height
        }
        drawText(title, topLeft = Offset(centeredX(title), y))
        if (top != null || bottom != null) {
            // A small ornament under the title: ◆ between two short rules.
            val cy = y + title.size.height + 6.dp.toPx()
            val cx = boardWidth / 2f
            val rule = boardWidth * 0.12f
            val gold = Brush.horizontalGradient(GoldBrush)
            drawLine(gold, Offset(cx - rule - 5.dp.toPx(), cy), Offset(cx - 5.dp.toPx(), cy), 0.8.dp.toPx())
            drawLine(gold, Offset(cx + 5.dp.toPx(), cy), Offset(cx + rule + 5.dp.toPx(), cy), 0.8.dp.toPx())
            drawPath(diamond(Offset(cx, cy), 2.5.dp.toPx()), gold)
        }
    }

    private companion object {
        const val TITLE_MAX_LINES = 4
        const val SMALL_MIN_SP = 6f
        const val SMALL_MAX_SP = 11f
        const val COMPACT_SERIES_SP = 7f
        const val SIZE_STEP_SP = 0.5f
    }
}

private fun coverStyle(size: TextUnit, alpha: Float, bold: Boolean) = TextStyle(
    color = Gold.copy(alpha = alpha),
    fontSize = size,
    lineHeight = size * 1.25f,
    fontFamily = FontFamily.Serif,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    textAlign = TextAlign.Center,
    textDirection = TextDirection.Rtl,
    shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1.5f), blurRadius = 2f),
)

/** "משנה תורה, הלכות תשובה" → ("משנה תורה", "הלכות תשובה"); a title without a comma has no series. */
internal fun splitTitle(heTitle: String): Pair<String?, String> {
    val comma = heTitle.indexOf(',')
    if (comma <= 0 || comma >= heTitle.lastIndex) return null to heTitle
    return heTitle.substring(0, comma).trim() to heTitle.substring(comma + 1).trim()
}
