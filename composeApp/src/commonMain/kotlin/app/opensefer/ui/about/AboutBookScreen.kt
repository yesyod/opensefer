package app.opensefer.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.opensefer.core.domain.TextRepository
import app.opensefer.core.model.BookAbout
import app.opensefer.ui.UiStrings
import app.opensefer.ui.theme.LocalReadingColors
import org.koin.compose.koinInject

/**
 * Comprehensive "About this book" screen — reached from the info icon in the reader's top bar.
 * Shows everything Sefaria exposes for a book, organized into sections: title & author, a
 * description, the work's facts (era, composition/publication date & place, categories), the
 * author's biography, and the editions in use with their licenses. Strings are Hebrew‑first;
 * any field Sefaria doesn't provide is simply omitted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutBookScreen(
    bookTitle: String,
    heBookTitle: String,
    onBack: () -> Unit,
    textRepository: TextRepository = koinInject(),
) {
    val colors = LocalReadingColors.current
    var reloadKey by remember { mutableStateOf(0) }
    val result by produceState<Result<BookAbout>?>(null, bookTitle, reloadKey) {
        value = null
        value = textRepository.getAbout(bookTitle)
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.text,
                ),
                title = { Text(heBookTitle, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Back", color = colors.accent) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val r = result) {
                null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.accent)
                else -> r.fold(
                    onSuccess = { AboutBookContent(it) },
                    onFailure = { AboutBookError(onRetry = { reloadKey++ }) },
                )
            }
        }
    }
}

@Composable
private fun AboutBookContent(about: BookAbout) {
    val colors = LocalReadingColors.current
    val uriHandler = LocalUriHandler.current
    // Hebrew‑primary surface → force RTL; mixed Latin/numbers resolve via BiDi (same as the reader).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(
                text = about.heTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.text,
            )
            if (about.title.isNotBlank() && about.title != about.heTitle) {
                Text(
                    text = about.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.secondaryText,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            val authorLine = listOfNotNull(
                about.authorHe,
                about.authorEn?.takeIf { it != about.authorHe },
            ).joinToString(" · ")
            if (authorLine.isNotBlank()) {
                Text(
                    text = authorLine,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.accent,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            about.description?.let {
                SectionTitle("תיאור")
                Body(it)
            }

            val facts = buildList {
                about.era?.let { add("תקופה" to it) }
                about.compDate?.let { add("נכתב" to it) }
                about.compPlace?.let { add("מקום החיבור" to it) }
                about.pubDate?.let { add("דפוס ראשון" to it) }
                about.pubPlace?.let { add("מקום ההדפסה" to it) }
                if (about.categories.isNotEmpty()) add("קטגוריה" to about.categories.joinToString(" › "))
            }
            if (facts.isNotEmpty()) {
                SectionTitle("פרטים")
                facts.forEach { (label, value) -> InfoRow(label, value) }
            }

            about.authorBio?.let { bio ->
                val birth = listOfNotNull(bio.birthYear, bio.birthPlace).joinToString(", ")
                val death = listOfNotNull(bio.deathYear, bio.deathPlace).joinToString(", ")
                if (birth.isNotBlank() || death.isNotBlank() || bio.bio != null) {
                    SectionTitle("אודות המחבר")
                    if (birth.isNotBlank()) InfoRow("לידה", birth)
                    if (death.isNotBlank()) InfoRow("פטירה", death)
                    bio.bio?.let { Body(it) }
                    bio.wikiLink?.takeIf { it.startsWith("http") }?.let { link ->
                        Text(
                            text = "ויקיפדיה ↗",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.accent,
                            modifier = Modifier.padding(top = 6.dp).clickable { uriHandler.openUri(link) },
                        )
                    }
                }
            }

            if (about.editions.isNotEmpty()) {
                SectionTitle("מהדורות")
                about.editions.forEach { ed ->
                    InfoRow(if (ed.language == "he") "עברית" else "אנגלית", ed.title)
                    ed.license?.let { InfoRow("רישיון", it) }
                    ed.source?.let { Body(it, color = colors.secondaryText, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = LocalReadingColors.current.text,
        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp),
    )
}

@Composable
private fun Body(
    text: String,
    color: Color = LocalReadingColors.current.secondaryText,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Text(text = text, style = style, color = color, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = LocalReadingColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = "$label:  ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.secondaryText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AboutBookError(onRetry: () -> Unit) {
    val colors = LocalReadingColors.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = UiStrings.ERROR_BOOK_DETAILS,
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text(UiStrings.RETRY, color = colors.accent) }
    }
}
