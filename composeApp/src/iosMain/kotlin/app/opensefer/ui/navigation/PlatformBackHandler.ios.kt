package app.opensefer.ui.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS uses the native edge‑swipe gesture; nothing to intercept here for the MVP.
}
