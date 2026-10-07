package app.opensefer.ui.theme

import androidx.compose.runtime.Composable

@Composable
actual fun SystemBarsEffect(darkIcons: Boolean) {
    // The status bar is hidden on iOS (Info.plist: UIStatusBarHidden) — there's nothing to colour.
}
