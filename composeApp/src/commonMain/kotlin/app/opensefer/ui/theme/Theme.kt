package app.opensefer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import app.opensefer.core.domain.ReadingTheme

/**
 * The reading surface palette. Three calm themes tuned for long‑form study — light, sepia (warm
 * paper) and dark (plus "automatic", which follows the device). Kept separate from Material's
 * [androidx.compose.material3.ColorScheme] because the *reading surface* has its own deliberate
 * colors (text, parchment, accent, divider); the Material scheme is derived from it so every
 * component (switches, fields, sheets) matches.
 */
@Immutable
data class ReadingColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color,
    val onAccent: Color,
    val divider: Color,
    val highlight: Color, // selected segments
    val isDark: Boolean,
)

private val LightReading = ReadingColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF7F5F0),
    text = Color(0xFF1A1A1A),
    secondaryText = Color(0xFF6B6B6B),
    accent = Color(0xFF8A5A2B),
    onAccent = Color(0xFFFFFFFF),
    divider = Color(0xFFE6E2D8),
    highlight = Color(0x2E8A5A2B),
    isDark = false,
)

// A deeper accent than light's: on paper, and under a selection's highlight, it keeps WCAG AA contrast.
private val SepiaReading = ReadingColors(
    background = Color(0xFFF4ECD8),
    surface = Color(0xFFEFE6CE),
    text = Color(0xFF3B2F1E),
    secondaryText = Color(0xFF6E5B3E),
    accent = Color(0xFF74491F),
    onAccent = Color(0xFFFFF8EC),
    divider = Color(0xFFDDCFB0),
    highlight = Color(0x3374491F),
    isDark = false,
)

private val DarkReading = ReadingColors(
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    text = Color(0xFFE8E4DA),
    secondaryText = Color(0xFF9A968C),
    accent = Color(0xFFC9A227),
    onAccent = Color(0xFF1A1408),
    divider = Color(0xFF2C2C2C),
    highlight = Color(0x40C9A227),
    isDark = true,
)

/** The palette for this theme; [ReadingTheme.System] resolves through [systemDark]. */
fun ReadingTheme.toColors(systemDark: Boolean): ReadingColors = when (this) {
    ReadingTheme.System -> if (systemDark) DarkReading else LightReading
    ReadingTheme.Light -> LightReading
    ReadingTheme.Sepia -> SepiaReading
    ReadingTheme.Dark -> DarkReading
}

/** Current reading palette, available to any composable under [OpenSeferTheme]. */
val LocalReadingColors = staticCompositionLocalOf { LightReading }

/** User‑controlled font scale, multiplied into reading text sizes. */
val LocalFontScale = staticCompositionLocalOf { 1f }

/**
 * Hebrew reading text — a serif at a comfortable size, scaled by the user's font preference, laid
 * out right‑to‑left whatever the surrounding layout direction. (Bundling a dedicated nikud face like
 * Frank Ruhl Libre via compose‑resources is the documented next step; the platform serif renders
 * nikud correctly in the meantime.)
 */
@Composable
fun hebrewReadingStyle(): TextStyle {
    val scale = LocalFontScale.current
    val color = LocalReadingColors.current.text
    return remember(scale, color) {
        TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = (21 * scale).sp,
            lineHeight = (36 * scale).sp,
            color = color,
            textDirection = TextDirection.Rtl,
        )
    }
}

/** English reading text — always left‑to‑right, so its punctuation and alignment stay correct in an RTL page. */
@Composable
fun englishReadingStyle(): TextStyle {
    val scale = LocalFontScale.current
    val color = LocalReadingColors.current.secondaryText
    return remember(scale, color) {
        TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = (17 * scale).sp,
            lineHeight = (28 * scale).sp,
            color = color,
            textDirection = TextDirection.Ltr,
        )
    }
}

private fun ReadingColors.toColorScheme(): ColorScheme {
    val tint = { alpha: Float -> accent.copy(alpha = alpha).compositeOver(background) }
    val error = if (isDark) Color(0xFFF2B8B5) else Color(0xFFB3261E)
    val onError = if (isDark) Color(0xFF601410) else Color.White
    return if (isDark) {
        darkColorScheme(
            primary = accent, onPrimary = onAccent,
            primaryContainer = tint(0.24f), onPrimaryContainer = text,
            inversePrimary = accent,
            secondary = accent, onSecondary = onAccent,
            secondaryContainer = tint(0.20f), onSecondaryContainer = text,
            tertiary = accent, onTertiary = onAccent,
            tertiaryContainer = tint(0.20f), onTertiaryContainer = text,
            background = background, onBackground = text,
            surface = background, onSurface = text,
            surfaceVariant = surface, onSurfaceVariant = secondaryText,
            surfaceTint = Color.Transparent,
            inverseSurface = text, inverseOnSurface = background,
            error = error, onError = onError,
            outline = secondaryText.copy(alpha = 0.6f), outlineVariant = divider,
            scrim = Color.Black,
            surfaceBright = surface, surfaceDim = background,
            surfaceContainerLowest = background, surfaceContainerLow = surface, surfaceContainer = surface,
            surfaceContainerHigh = surface, surfaceContainerHighest = tint(0.10f),
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = onAccent,
            primaryContainer = tint(0.16f), onPrimaryContainer = text,
            inversePrimary = accent,
            secondary = accent, onSecondary = onAccent,
            secondaryContainer = tint(0.14f), onSecondaryContainer = text,
            tertiary = accent, onTertiary = onAccent,
            tertiaryContainer = tint(0.14f), onTertiaryContainer = text,
            background = background, onBackground = text,
            surface = background, onSurface = text,
            surfaceVariant = surface, onSurfaceVariant = secondaryText,
            surfaceTint = Color.Transparent,
            inverseSurface = text, inverseOnSurface = background,
            error = error, onError = onError,
            outline = secondaryText.copy(alpha = 0.6f), outlineVariant = divider,
            scrim = Color.Black,
            surfaceBright = background, surfaceDim = surface,
            surfaceContainerLowest = background, surfaceContainerLow = surface, surfaceContainer = surface,
            surfaceContainerHigh = surface, surfaceContainerHighest = tint(0.08f),
        )
    }
}

@Composable
fun OpenSeferTheme(
    theme: ReadingTheme,
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    val reading = theme.toColors(systemDark = isSystemInDarkTheme())
    val scheme = remember(reading) { reading.toColorScheme() }
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
