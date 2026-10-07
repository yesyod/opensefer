package app.opensefer.ui.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS has no system back button and a Compose‑hosted screen has no native swipe‑back;
    // every screen shows its own back arrow.
}
