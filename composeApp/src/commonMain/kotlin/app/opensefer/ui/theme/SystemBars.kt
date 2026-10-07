package app.opensefer.ui.theme

import androidx.compose.runtime.Composable

/**
 * Keeps the system bar icons legible on the reading theme's background (dark icons on light/sepia,
 * light icons on dark) — the app draws edge‑to‑edge, so the bars sit over its own colours.
 */
@Composable
expect fun SystemBarsEffect(darkIcons: Boolean)
