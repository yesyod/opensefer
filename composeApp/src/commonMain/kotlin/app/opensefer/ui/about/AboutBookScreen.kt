package app.opensefer.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.opensefer.core.domain.TextRepository
import app.opensefer.core.model.AuthorBio
import app.opensefer.core.model.BookAbout
import app.opensefer.core.model.EditionInfo
import app.opensefer.ui.UiStrings
import app.opensefer.ui.components.AppTopBar
import app.opensefer.ui.components.ErrorState
import app.opensefer.ui.components.LoadingState
import app.opensefer.ui.theme.LocalReadingColors
import app.opensefer.ui.userMessage
import org.koin.compose.koinInject

/**
 * Comprehensive "About this book" screen — reached from the reader's contents sheet and the book page.
 * Shows everything Sefaria exposes for a book, organized into sections: title & author, a
 * description, the work's facts (era, composition/publication date & place, categories), the
 * author's biography, and the editions in use with their licenses. Strings are Hebrew‑first;
 * any field Sefaria doesn't provide is simply omitted.
 */
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

    Scaffold(containerColor = colors.background, topBar = { AppTopBar(heBookTitle, onBack = onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val r = result) {
                null -> LoadingState()
                else -> r.fold(
                    onSuccess = { AboutBookContent(it) },
                    onFailure = { e ->
                        ErrorState(
                            UiStrings.ERROR_BOOK_DETAILS,
                            e.userMessage(),
                            onRetry = { reloadKey++ },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AboutBookContent(about: BookAbout) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TitleBlock(about)
        about.description?.let {
            SectionTitle("תיאור")
            Body(it)
        }
        Facts(about)
        about.authorBio?.let { AuthorSection(it) }
        if (about.editions.isNotEmpty()) Editions(about.editions)
    }
}

@Composable
private fun TitleBlock(about: BookAbout) {
    val colors = LocalReadingColors.current
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
    val authorLine = listOfNotNull(about.authorHe, about.authorEn?.takeIf { it != about.authorHe }).joinToString(" · ")
    if (authorLine.isNotBlank()) {
        Text(
            text = authorLine,
            style = MaterialTheme.typography.titleMedium,
            color = colors.accent,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Facts(about: BookAbout) {
    val facts = buildList {
        about.era?.let { add("תקופה" to it) }
        about.compDate?.let { add("נכתב" to it) }
        about.compPlace?.let { add("מקום החיבור" to it) }
        about.pubDate?.let { add("דפוס ראשון" to it) }
        about.pubPlace?.let { add("מקום ההדפסה" to it) }
        if (about.categories.isNotEmpty()) add("קטגוריה" to about.categories.joinToString(" › "))
    }
    if (facts.isEmpty()) return
    SectionTitle("פרטים")
    facts.forEach { (label, value) -> InfoRow(label, value) }
}

@Composable
private fun AuthorSection(bio: AuthorBio) {
    val colors = LocalReadingColors.current
    val uriHandler = LocalUriHandler.current
    val birth = listOfNotNull(bio.birthYear, bio.birthPlace).joinToString(", ")
    val death = listOfNotNull(bio.deathYear, bio.deathPlace).joinToString(", ")
    if (birth.isBlank() && death.isBlank() && bio.bio == null) return
    SectionTitle("אודות המחבר")
    if (birth.isNotBlank()) InfoRow("לידה", birth)
    if (death.isNotBlank()) InfoRow("פטירה", death)
    bio.bio?.let { Body(it) }
    bio.wikiLink?.takeIf { it.startsWith("http") }?.let { link ->
        Text(
            text = "ויקיפדיה ↗",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.accent,
            modifier = Modifier
                .padding(top = 2.dp)
                .minimumInteractiveComponentSize()
                .clickable(role = Role.Button) { uriHandler.openUri(link) },
        )
    }
}

@Composable
private fun Editions(editions: List<EditionInfo>) {
    val colors = LocalReadingColors.current
    SectionTitle("מהדורות")
    editions.forEach { ed ->
        InfoRow(if (ed.language == "he") "עברית" else "אנגלית", ed.title)
        ed.license?.let { InfoRow("רישיון", it) }
        ed.source?.let { Body(it, color = colors.secondaryText, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = LocalReadingColors.current.text,
        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp).semantics { heading() },
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
