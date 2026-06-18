package app.opensefer

import androidx.compose.ui.window.ComposeUIViewController

/**
 * iOS entry point. Called from Swift (`iosApp`) to obtain a UIViewController that
 * hosts the shared Compose [App].
 */
fun MainViewController() = ComposeUIViewController { App() }
