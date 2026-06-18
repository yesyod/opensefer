package app.opensefer.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.opensefer.ui.theme.LocalReadingColors

/**
 * Credits & attribution. Sefaria's data terms require crediting the source/edition; this screen
 * carries that, plus the project's own license note. (BLUEPRINT §7.4 / §13)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val colors = LocalReadingColors.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Paragraph("OpenSefer", MaterialTheme.typography.headlineSmall, colors.text)
            Paragraph(
                "A minimalist, open‑source reader for Jewish texts, built with Kotlin Multiplatform " +
                    "and Compose Multiplatform.",
                MaterialTheme.typography.bodyMedium,
                colors.secondaryText,
            )
            Paragraph("Texts", MaterialTheme.typography.titleMedium, colors.text)
            Paragraph(
                "Texts are provided by Sefaria (sefaria.org) and remain © their publishers under " +
                    "their respective Creative Commons licenses (CC0 / CC‑BY / CC‑BY‑SA, per edition). " +
                    "OpenSefer is not affiliated with or endorsed by Sefaria.",
                MaterialTheme.typography.bodySmall,
                colors.secondaryText,
            )
            Paragraph("License", MaterialTheme.typography.titleMedium, colors.text)
            Paragraph(
                "Application code is licensed under Apache‑2.0. See the project repository for source, " +
                    "attribution details, and how to contribute.",
                MaterialTheme.typography.bodySmall,
                colors.secondaryText,
            )
        }
    }
}

@Composable
private fun Paragraph(text: String, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color) {
    Text(text = text, style = style, color = color, modifier = Modifier.padding(top = 14.dp))
}
