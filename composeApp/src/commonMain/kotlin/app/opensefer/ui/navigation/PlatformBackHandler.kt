package app.opensefer.ui.navigation

import androidx.compose.runtime.Composable

/**
 * Routes the platform "back" gesture/button to in‑app navigation: Android's system back. A no‑op on
 * iOS, which has no system back and no native swipe‑back for a Compose‑hosted screen — there every
 * screen's own back arrow is the way back.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** True where the system itself offers "back" (Android), so in‑app back arrows may scroll out of view. */
expect val platformHasSystemBack: Boolean
