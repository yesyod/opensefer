package app.opensefer.ui.navigation

import androidx.compose.runtime.Composable

/** Routes the platform "back" gesture/button to in‑app navigation (Android system back; no‑op on iOS,
 *  where the edge‑swipe is handled natively). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
