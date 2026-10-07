package app.opensefer.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Re‑applies edge‑to‑edge with the *reading theme's* light/dark rather than the system's: the right
 * icon colours on every API level, and — on Android 7–9, where the navigation bar can't be made
 * transparent — a scrim to match, so its buttons stay visible on the dark theme.
 */
@Composable
actual fun SystemBarsEffect(darkIcons: Boolean) {
    val view = LocalView.current
    DisposableEffect(darkIcons) {
        view.context.findActivity()?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { !darkIcons },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { !darkIcons },
        )
        onDispose {}
    }
}

// The scrims enableEdgeToEdge() itself uses where the navigation bar can't be transparent.
private val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
