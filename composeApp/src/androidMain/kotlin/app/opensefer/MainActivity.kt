package app.opensefer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Single Android entry point. Hosts the shared Compose [App] edge‑to‑edge: the app draws behind the
 * (visible) system bars and pads for them, so Back and Home stay a single gesture away on every
 * screen. In the reader the top bar slides away while reading.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
