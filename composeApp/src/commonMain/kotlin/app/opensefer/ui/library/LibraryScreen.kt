package app.opensefer.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opensefer.core.domain.BookDownloader
import app.opensefer.core.domain.BookmarkRepository
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.model.Bookmark
import app.opensefer.core.model.DownloadProgress
import app.opensefer.core.model.LibraryBook
import app.opensefer.ui.UiStrings
import app.opensefer.ui.components.AppTopBar
import app.opensefer.ui.components.IconAction
import app.opensefer.ui.components.SectionHeader
import app.opensefer.ui.icons.AppIcons
import app.opensefer.ui.reader.BookmarkRow
import app.opensefer.ui.theme.LocalReadingColors
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Home: the books you saved, as a shelf of designed covers — tap to continue where you left off,
 * long‑press for the book's options — with "continue reading" on top and your bookmarks below.
 */
@Composable
fun LibraryScreen(
    onContinueReading: (LibraryBook) -> Unit,
    onOpenBookPage: (LibraryBook) -> Unit,
    onOpenBookmark: (Bookmark) -> Unit,
    onAddBook: () -> Unit,
    onAbout: () -> Unit,
    libraryRepository: LibraryRepository = koinInject(),
    bookmarkRepository: BookmarkRepository = koinInject(),
    downloader: BookDownloader = koinInject(),
) {
    val viewModel = viewModel { LibraryViewModel(libraryRepository, bookmarkRepository, downloader) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var actionsFor by remember { mutableStateOf<LibraryBook?>(null) }
    val colors = LocalReadingColors.current

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(title = UiStrings.LIBRARY) {
                IconAction(AppIcons.Search, UiStrings.ADD_BOOK, onAddBook)
                IconAction(AppIcons.Info, UiStrings.ABOUT, onAbout)
            }
        },
    ) { padding ->
        if (state.books.isEmpty() && state.bookmarks.isEmpty()) {
            EmptyLibrary(onAddBook, Modifier.fillMaxSize().padding(padding))
        } else {
            LibraryGrid(
                state = state,
                padding = padding,
                onBook = onContinueReading,
                onBookActions = { actionsFor = it },
                onAddBook = onAddBook,
                onOpenBookmark = onOpenBookmark,
                onDeleteBookmark = viewModel::removeBookmark,
            )
        }
    }

    actionsFor?.let { book ->
        BookActionsSheet(
            book = book,
            download = state.downloads[book.title],
            onContinue = { onContinueReading(book) },
            onBookPage = { onOpenBookPage(book) },
            onDownload = { viewModel.download(book) },
            onRemove = {
                viewModel.remove(book)
                scope.launch {
                    val result = snackbar.showSnackbar(UiStrings.REMOVED_FROM_LIBRARY, actionLabel = UiStrings.UNDO)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove(book)
                }
            },
            onDismiss = { actionsFor = null },
        )
    }
}

@Composable
private fun LibraryGrid(
    state: LibraryUiState,
    padding: PaddingValues,
    onBook: (LibraryBook) -> Unit,
    onBookActions: (LibraryBook) -> Unit,
    onAddBook: () -> Unit,
    onOpenBookmark: (Bookmark) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
) {
    // Covers get wider (fewer per row) as the user's font size grows, so titles stay legible.
    val fontScale = LocalDensity.current.fontScale
    val minCover = if (fontScale > LARGE_FONT_SCALE) 140.dp else 104.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCover),
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.continueReading?.let { book ->
            item(key = "continue", span = { GridItemSpan(maxLineSpan) }) {
                ContinueCard(book, onClick = { onBook(book) })
            }
        }
        item(key = "shelf", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                SectionHeader(UiStrings.MY_BOOKS)
                Text(
                    UiStrings.LONG_PRESS_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalReadingColors.current.secondaryText,
                )
            }
        }
        items(state.books, key = { "book:${it.title}" }) { book ->
            CoverTile(
                book = book,
                download = state.downloads[book.title],
                onClick = { onBook(book) },
                onLongClick = { onBookActions(book) },
                modifier = Modifier.animateItem(),
            )
        }
        item(key = "add") { AddBookTile(onAddBook) }
        bookmarksSection(state.bookmarks, onOpenBookmark, onDeleteBookmark)
    }
}

private const val LARGE_FONT_SCALE = 1.3f
private const val BOOKMARKS_PREVIEW = 3

private fun LazyGridScope.bookmarksSection(
    bookmarks: List<Bookmark>,
    onOpen: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
) {
    if (bookmarks.isEmpty()) return
    item(key = "bookmarks-header", span = { GridItemSpan(maxLineSpan) }) {
        SectionHeader(UiStrings.BOOKMARKS)
    }
    item(key = "bookmarks", span = { GridItemSpan(maxLineSpan) }) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        Column {
            val shown = if (expanded) bookmarks else bookmarks.take(BOOKMARKS_PREVIEW)
            shown.forEach { bookmark ->
                BookmarkRow(bookmark, showBook = true, onOpen = { onOpen(bookmark) }, onDelete = { onDelete(bookmark) })
            }
            if (bookmarks.size > BOOKMARKS_PREVIEW) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(
                        if (expanded) UiStrings.SHOW_LESS else "${UiStrings.SHOW_ALL} (${bookmarks.size})",
                        color = LocalReadingColors.current.accent,
                    )
                }
            }
        }
    }
}

/** The book you were last reading, one tap from where you stopped. */
@Composable
private fun ContinueCard(book: LibraryBook, onClick: () -> Unit) {
    val colors = LocalReadingColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(
                heTitle = book.heTitle,
                category = book.category,
                heCategory = book.heCategory,
                heAuthor = book.heAuthor,
                compact = true,
                modifier = Modifier.width(56.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(UiStrings.CONTINUE_READING, style = MaterialTheme.typography.labelMedium, color = colors.accent)
                Text(
                    text = book.heTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                book.lastLabel?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText, maxLines = 1)
                }
                ProgressLine(book.progress, Modifier.padding(top = 8.dp))
            }
            Icon(AppIcons.Forward, contentDescription = null, tint = colors.accent)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverTile(
    book: LibraryBook,
    download: DownloadProgress?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReadingColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClickLabel = UiStrings.CONTINUE_READING,
                onLongClickLabel = UiStrings.BOOK_OPTIONS,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        BookCover(
            heTitle = book.heTitle,
            category = book.category,
            heCategory = book.heCategory,
            heAuthor = book.heAuthor,
            modifier = Modifier.fillMaxWidth(),
        )
        ProgressLine(if (book.lastReadAt > 0) book.progress else 0f, Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = book.lastLabel?.takeIf { book.lastReadAt > 0 } ?: UiStrings.NEW_BOOK,
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            OfflineBadge(book.offline, download)
        }
    }
}

/** ✓ when the whole book is on the device; a small ring while it downloads. */
@Composable
private fun OfflineBadge(offline: Boolean, download: DownloadProgress?) {
    val colors = LocalReadingColors.current
    when {
        download != null -> CircularProgressIndicator(
            progress = { download.fraction },
            color = colors.accent,
            trackColor = colors.divider,
            strokeWidth = 2.dp,
            modifier = Modifier.size(14.dp),
        )
        offline -> Icon(
            AppIcons.OfflinePin,
            contentDescription = UiStrings.AVAILABLE_OFFLINE,
            tint = colors.secondaryText,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** A thin reading‑progress line (grows from the right, with the text). */
@Composable
private fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val colors = LocalReadingColors.current
    Box(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(colors.divider)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(colors.accent))
    }
}

/** The last cell of the shelf: an empty, dashed‑looking cover that opens search. */
@Composable
private fun AddBookTile(onClick: () -> Unit) {
    val colors = LocalReadingColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(6.dp),
            color = colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_ASPECT_RATIO)
                .border(1.dp, colors.divider, RoundedCornerShape(6.dp)),
        ) {
            Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(AppIcons.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.size(32.dp))
                Text(
                    UiStrings.ADD_BOOK,
                    color = colors.accent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onAddBook: () -> Unit, modifier: Modifier) {
    val colors = LocalReadingColors.current
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                UiStrings.EMPTY_LIBRARY_TITLE,
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            Text(
                UiStrings.EMPTY_LIBRARY_BODY,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            Button(
                onClick = onAddBook,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            ) {
                Icon(AppIcons.Search, contentDescription = null)
                Text(UiStrings.ADD_BOOK, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Long‑press options for a saved book. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookActionsSheet(
    book: LibraryBook,
    download: DownloadProgress?,
    onContinue: () -> Unit,
    onBookPage: () -> Unit,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalReadingColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BookCover(book.heTitle, book.category, book.heCategory, book.heAuthor, Modifier.width(48.dp), compact = true)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(book.heTitle, style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
                    book.heAuthor?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText) }
                }
            }
            SheetAction(AppIcons.Forward, if (book.lastReadAt > 0) UiStrings.CONTINUE_READING else UiStrings.START_READING) {
                onDismiss()
                onContinue()
            }
            SheetAction(AppIcons.Contents, UiStrings.CONTENTS) {
                onDismiss()
                onBookPage()
            }
            when {
                download != null -> SheetAction(AppIcons.Download, "${UiStrings.DOWNLOADING} ${download.done}/${download.total}") {}
                book.offline -> SheetAction(AppIcons.OfflinePin, UiStrings.AVAILABLE_OFFLINE) {}
                else -> SheetAction(AppIcons.Download, UiStrings.DOWNLOAD_OFFLINE) {
                    onDismiss()
                    onDownload()
                }
            }
            SheetAction(AppIcons.Delete, UiStrings.REMOVE_FROM_LIBRARY, destructive = true) {
                onDismiss()
                onRemove()
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    text: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalReadingColors.current
    val tint = if (destructive) MaterialTheme.colorScheme.error else colors.text
    Surface(onClick = onClick, color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (destructive) tint else colors.accent)
            Text(text, color = tint, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
        }
    }
}
