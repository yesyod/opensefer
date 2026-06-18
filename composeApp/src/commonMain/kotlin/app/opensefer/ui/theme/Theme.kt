package app.opensefer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import app.opensefer.core.domain.ReadingTheme

/**
 * The reading surface palette. Three calm themes tuned for long‑form study — light, sepia (warm
 * paper) and dark. Kept separate from Material's [androidx.compose.material3.ColorScheme] because
 * the *reading surface* has its own deliberate colors (text, parchment, accent, divider).
 */
@Immutable
data class ReadingColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color,
    val divider: Color,
    val isDark: Boolean,
)

private val LightReading = ReadingColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF7F5F0),
    text = Color(0xFF1A1A1A),
    secondaryText = Color(0xFF6B6B6B),
    accent = Color(0xFF8A5A2B),
    divider = Color(0xFFE6E2D8),
    isDark = false,
)

private val SepiaReading = ReadingColors(
    background = Color(0xFFF4ECD8),
    surface = Color(0xFFEFE6CE),
    text = Color(0xFF3B2F1E),
    secondaryText = Color(0xFF6E5B3E),
    accent = Color(0xFF8A5A2B),
    divider = Color(0xFFDDCFB0),
    isDark = false,
)

private val DarkReading = ReadingColors(
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    text = Color(0xFFE8E4DA),
    secondaryText = Color(0xFF9A968C),
    accent = Color(0xFFC9A227),
    divider = Color(0xFF2C2C2C),
    isDark = true,
)

fun ReadingTheme.toColors(): ReadingColors = when (this) {
    ReadingTheme.Light -> LightReading
    ReadingTheme.Sepia -> SepiaReading
    ReadingTheme.Dark -> DarkReading
}

/** Current reading palette, available to any composable under [OpenSeferTheme]. */
val LocalReadingColors = staticCompositionLocalOf { LightReading }

/** User‑controlled font scale, multiplied into reading text sizes. */
val LocalFontScale = staticCompositionLocalOf { 1f }

/**
 * Hebrew reading text — a serif at a comfortable size, scaled by the user's font preference.
 * (Bundling a dedicated nikud face like Frank Ruhl Libre via compose‑resources is the documented
 * next step; the platform serif renders nikud correctly in the meantime.)
 */
@Composable
fun hebrewReadingStyle(): TextStyle {
    val scale = LocalFontScale.current
    return TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = (21 * scale).sp,
        lineHeight = (36 * scale).sp,
        color = LocalReadingColors.current.text,
    )
}

@Composable
fun englishReadingStyle(): TextStyle {
    val scale = LocalFontScale.current
    return TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = (17 * scale).sp,
        lineHeight = (28 * scale).sp,
        color = LocalReadingColors.current.secondaryText,
    )
}

@Composable
fun OpenSeferTheme(
    theme: ReadingTheme,
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    val reading = theme.toColors()
    val scheme = if (reading.isDark) {
        darkColorScheme(
            primary = reading.accent,
            background = reading.background,
            surface = reading.surface,
            onBackground = reading.text,
            onSurface = reading.text,
        )
    } else {
        lightColorScheme(
            primary = reading.accent,
            background = reading.background,
            surface = reading.surface,
            onBackground = reading.text,
            onSurface = reading.text,
        )
    }
    CompositionLocalProvider(
        LocalReadingColors provides reading,
        LocalFontScale provides fontScale,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            content = content,
        )
    }
}
