package app.opensefer.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opensefer.core.model.Bookmark
import app.opensefer.core.model.TocBranch
import app.opensefer.core.model.TocLeaf
import app.opensefer.ui.UiStrings
import app.opensefer.ui.components.IconAction
import app.opensefer.ui.icons.AppIcons
import app.opensefer.ui.theme.LocalReadingColors
import app.opensefer.ui.toc.rememberTocBrowserState
import app.opensefer.ui.toc.tocItems

/**
 * The reader's navigator: the book's structure (a chapter grid, or a drill‑down tree opened at the
 * current passage) and, on a second tab, this book's bookmarks. Tapping either jumps the continuous
 * reader there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentsSheet(
    root: TocBranch,
    currentTref: String?,
    bookmarks: List<Bookmark>,
    startOnBookmarks: Boolean,
    onOpen: (TocLeaf) -> Unit,
    onOpenBookmark: (Bookmark) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
    onAbout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalReadingColors.current
    var showBookmarks by rememberSaveable { mutableStateOf(startOnBookmarks) }
    val browser = rememberTocBrowserState(root, currentTref)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = root.heTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconAction(AppIcons.Info, UiStrings.ABOUT_BOOK, onAbout)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                SheetTab(UiStrings.CONTENTS, selected = !showBookmarks, Modifier.weight(1f)) { showBookmarks = false }
                SheetTab(
                    text = if (bookmarks.isEmpty()) UiStrings.BOOKMARKS else "${UiStrings.BOOKMARKS} (${bookmarks.size})",
                    selected = showBookmarks,
                    modifier = Modifier.weight(1f),
                ) { showBookmarks = true }
            }
            HorizontalDivider(color = colors.divider)
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
            ) {
                if (showBookmarks) {
                    bookmarkItems(bookmarks, onOpenBookmark, onDeleteBookmark)
                } else {
                    tocItems(browser, currentTref, onOpen)
                }
            }
        }
    }
}

@Composable
private fun SheetTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalReadingColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = if (selected) colors.accent else colors.secondaryText,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.titleSmall,
        )
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(2.dp)
                .then(if (selected) Modifier.background(colors.accent) else Modifier),
        )
    }
}

private fun LazyListScope.bookmarkItems(
    bookmarks: List<Bookmark>,
    onOpen: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        item(key = "bm-empty") {
            Text(
                text = UiStrings.NO_BOOKMARKS,
                color = LocalReadingColors.current.secondaryText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
        return
    }
    items(bookmarks, key = { "bm-${it.id}" }) { bookmark ->
        BookmarkRow(bookmark, showBook = false, onOpen = { onOpen(bookmark) }, onDelete = { onDelete(bookmark) })
    }
}

/** One bookmark: where it is, a line of its text, and a delete button. Shared with the library. */
@Composable
fun BookmarkRow(bookmark: Bookmark, showBook: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalReadingColors.current
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(AppIcons.Bookmark, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (showBook) "${bookmark.heBookTitle} · ${bookmark.label}" else bookmark.label,
                    color = colors.text,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (bookmark.snippet.isNotBlank()) {
                    Text(
                        text = bookmark.snippet,
                        color = colors.secondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconAction(AppIcons.Delete, UiStrings.REMOVE_BOOKMARK, onDelete, tint = colors.secondaryText)
        }
    }
}
