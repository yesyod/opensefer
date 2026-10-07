package app.opensefer.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opensefer.core.domain.BookDownloader
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.domain.TextRepository
import app.opensefer.core.model.BookContents
import app.opensefer.core.model.LibraryBook
import app.opensefer.ui.UiStrings
import app.opensefer.ui.components.AppTopBar
import app.opensefer.ui.components.ErrorState
import app.opensefer.ui.components.IconAction
import app.opensefer.ui.components.SectionHeader
import app.opensefer.ui.icons.AppIcons
import app.opensefer.ui.library.BookCover
import app.opensefer.ui.theme.LocalReadingColors
import app.opensefer.ui.toc.rememberTocBrowserState
import app.opensefer.ui.toc.tocItems
import org.koin.compose.koinInject

/**
 * A book's page — reached from search (preview) and from the library: its cover and details, one tap
 * to read (or continue), one tap to save it to the library (which also keeps all of it on the device),
 * and its table of contents (a chapter grid, or the drill‑down tree of a complex book).
 */
@Composable
fun BookScreen(
    title: String,
    heTitle: String,
    onBack: () -> Unit,
    onRead: (tref: String?) -> Unit,
    onAbout: () -> Unit,
    textRepository: TextRepository = koinInject(),
    libraryRepository: LibraryRepository = koinInject(),
    downloader: BookDownloader = koinInject(),
) {
    val viewModel = viewModel { BookViewModel(textRepository, libraryRepository, downloader, title, heTitle) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalReadingColors.current

    Scaffold(
        containerColor = colors.background,
        topBar = {
            AppTopBar(title = "", onBack = onBack) {
                IconAction(AppIcons.Info, UiStrings.ABOUT_BOOK, onAbout)
            }
        },
    ) { padding ->
        val contents = state.contents
        val browser = contents?.let { rememberTocBrowserState(it.root) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            item(key = "header") { BookHeader(state, contents) }
            item(key = "actions") {
                BookActions(
                    state = state,
                    onRead = { onRead(null) },
                    onToggleSaved = viewModel::toggleSaved,
                    onDownload = viewModel::download,
                )
            }
            when {
                state.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                }
                state.error != null -> item(key = "error") { ErrorState(UiStrings.ERROR_TOC, state.error, viewModel::load) }
                browser != null -> {
                    item(key = "toc-title") { SectionHeader(UiStrings.CONTENTS) }
                    tocItems(browser, currentTref = state.saved?.lastTref, onOpen = { onRead(it.tref) })
                }
            }
        }
    }
}

@Composable
private fun BookHeader(state: BookUiState, contents: BookContents?) {
    val colors = LocalReadingColors.current
    val details = contents?.details
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.Top) {
        BookCover(
            heTitle = state.heTitle,
            category = details?.category ?: state.saved?.category,
            heCategory = details?.heCategory ?: state.saved?.heCategory,
            heAuthor = details?.heAuthor ?: state.saved?.heAuthor,
            modifier = Modifier.width(112.dp),
        )
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = state.heTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = colors.text,
            )
            if (state.title != state.heTitle) {
                Text(state.title, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
            }
            (details?.heAuthor ?: state.saved?.heAuthor)?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, color = colors.accent, modifier = Modifier.padding(top = 8.dp))
            }
            (details?.heCategory ?: state.saved?.heCategory)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
            }
            contents?.let {
                Text(
                    text = "${it.leaves.size} ${if (it.isComplex) "קטעים" else "פרקים"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun BookActions(
    state: BookUiState,
    onRead: () -> Unit,
    onToggleSaved: () -> Unit,
    onDownload: () -> Unit,
) {
    val colors = LocalReadingColors.current
    val saved: LibraryBook? = state.saved
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onRead,
                enabled = state.contents != null,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (saved?.lastReadAt?.let { it > 0 } == true) UiStrings.CONTINUE_READING else UiStrings.START_READING)
            }
            OutlinedButton(
                onClick = onToggleSaved,
                enabled = state.contents != null || saved != null,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    if (saved != null) AppIcons.LibraryAdded else AppIcons.LibraryAdd,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    if (saved != null) UiStrings.IN_LIBRARY else UiStrings.SAVE_TO_LIBRARY,
                    color = colors.accent,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (saved != null) OfflineLine(saved, state, onDownload)
    }
}

@Composable
private fun OfflineLine(saved: LibraryBook, state: BookUiState, onDownload: () -> Unit) {
    val colors = LocalReadingColors.current
    val download = state.download
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            download != null -> {
                CircularProgressIndicator(
                    progress = { download.fraction },
                    color = colors.accent,
                    trackColor = colors.divider,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "${UiStrings.DOWNLOADING} ${download.done}/${download.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            saved.offline -> {
                Icon(AppIcons.OfflinePin, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(16.dp))
                Text(
                    UiStrings.AVAILABLE_OFFLINE,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            else -> TextButton(onClick = onDownload) {
                Icon(AppIcons.Download, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Text(UiStrings.DOWNLOAD_OFFLINE, color = colors.accent, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
