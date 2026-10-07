package app.opensefer.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opensefer.core.domain.BookmarkRepository
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.domain.ReadingLanguage
import app.opensefer.core.domain.ReadingPreferencesRepository
import app.opensefer.core.domain.TextRepository
import app.opensefer.core.model.bookmarkId
import app.opensefer.ui.UiStrings
import app.opensefer.ui.components.ErrorState
import app.opensefer.ui.components.IconAction
import app.opensefer.ui.components.LoadingState
import app.opensefer.ui.icons.AppIcons
import app.opensefer.ui.navigation.Destination
import app.opensefer.ui.navigation.PlatformBackHandler
import app.opensefer.ui.text.toAnnotatedString
import app.opensefer.ui.text.withNikud
import app.opensefer.ui.theme.LocalFontScale
import app.opensefer.ui.theme.LocalReadingColors
import app.opensefer.ui.theme.englishReadingStyle
import app.opensefer.ui.theme.hebrewReadingStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.koinInject

@Composable
fun ReaderScreen(
    destination: Destination.Reader,
    onBack: () -> Unit,
    onAboutBook: () -> Unit,
    textRepository: TextRepository = koinInject(),
    preferencesRepository: ReadingPreferencesRepository = koinInject(),
    libraryRepository: LibraryRepository = koinInject(),
    bookmarkRepository: BookmarkRepository = koinInject(),
) {
    val viewModel = viewModel {
        ReaderViewModel(
            textRepository,
            preferencesRepository,
            libraryRepository,
            bookmarkRepository,
            destination.bookTitle,
            destination.heBookTitle,
            destination.startTref,
            destination.startSegment,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The app may be killed in the background: write the reading position the moment it leaves the screen.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flushPosition() }
    ReaderContent(state, viewModel, onBack, onAboutBook)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderContent(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onAboutBook: () -> Unit,
) {
    val colors = LocalReadingColors.current
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val english = state.preferences.language == ReadingLanguage.English

    PlatformBackHandler(enabled = state.selection.isNotEmpty(), onBack = viewModel::clearSelection)

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.consumeMessage()
    }
    LaunchedEffect(state.scrollRequest) {
        val request = state.scrollRequest ?: return@LaunchedEffect
        listState.scrollToItem(request.index, request.offset)
        viewModel.onScrollRequestHandled(request.id)
        viewModel.onScrolled(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
    }

    val label by remember(state.rows, english) {
        derivedStateOf { state.rows.labelAt(listState.firstVisibleItemIndex, english) ?: "" }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ReaderTopBar(
                state = state,
                label = label,
                scrollBehavior = scrollBehavior,
                onBack = onBack,
                onContents = { viewModel.openSheet(ReaderSheet.Contents) },
                onBookmarkHere = viewModel::toggleBookmarkHere,
                onSave = viewModel::toggleSaved,
                onDisplay = { viewModel.openSheet(ReaderSheet.Display) },
            )
        },
        bottomBar = {
            if (state.selection.isNotEmpty()) {
                SelectionBar(
                    count = state.selection.size,
                    onCopy = { viewModel.copySelection()?.let { clipboard.setText(AnnotatedString(it)) } },
                    onBookmark = viewModel::bookmarkSelection,
                    onClose = viewModel::clearSelection,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null && state.rows.isEmpty() ->
                    ErrorState(UiStrings.ERROR_BOOK, state.error, viewModel::loadContents, Modifier.align(Alignment.Center))
                else -> ReaderList(state, listState, viewModel)
            }
        }
    }

    ReaderSheets(state, viewModel, onAboutBook)
}

@Composable
private fun ReaderSheets(state: ReaderUiState, viewModel: ReaderViewModel, onAboutBook: () -> Unit) {
    when (state.sheet) {
        ReaderSheet.Contents, ReaderSheet.Bookmarks -> state.contents?.let { contents ->
            ContentsSheet(
                root = contents.root,
                currentTref = state.current?.let { contents.leaves.getOrNull(it.passage)?.tref },
                bookmarks = state.bookmarks,
                startOnBookmarks = state.sheet == ReaderSheet.Bookmarks,
                onOpen = { viewModel.jumpTo(it.tref) },
                onOpenBookmark = { viewModel.jumpTo(it.tref, it.segment) },
                onDeleteBookmark = { viewModel.removeBookmark(it.id) },
                onAbout = {
                    viewModel.closeSheet()
                    onAboutBook()
                },
                onDismiss = viewModel::closeSheet,
            )
        }
        ReaderSheet.Display -> DisplayOptionsSheet(
            preferences = state.preferences,
            onFontScale = viewModel::setFontScale,
            onTheme = viewModel::setTheme,
            onLanguage = viewModel::setLanguage,
            onToggleNikud = viewModel::toggleNikud,
            onDismiss = viewModel::closeSheet,
        )
        null -> Unit
    }
}

/** The label of the passage at row [index] (its Hebrew — or, in English mode, English — title). */
private fun List<ReaderRow>.labelAt(index: Int, english: Boolean): String? =
    (index.coerceIn(0, lastIndex.coerceAtLeast(0)) until size).firstNotNullOfOrNull { i ->
        when (val row = getOrNull(i)) {
            is PassageTitleRow -> row.leaf
            is SegmentRow -> row.leaf
            is PendingRow -> row.leaf
            is HeadingRow, null -> null
        }
    }?.let { if (english) it.title else it.heTitle }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    state: ReaderUiState,
    label: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: () -> Unit,
    onContents: () -> Unit,
    onBookmarkHere: () -> Unit,
    onSave: () -> Unit,
    onDisplay: () -> Unit,
) {
    val colors = LocalReadingColors.current
    val bookmarkedHere = state.currentBookmarkId != null && state.currentBookmarkId in state.bookmarkedIds
    Column {
        TopAppBar(
            title = {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = UiStrings.CONTENTS, onClick = onContents)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (label.isEmpty()) state.heBookTitle else "${state.heBookTitle} · $label",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(AppIcons.ExpandMore, contentDescription = null, tint = colors.secondaryText)
                }
            },
            navigationIcon = { IconAction(AppIcons.Back, UiStrings.BACK, onBack) },
            actions = {
                IconAction(
                    icon = if (bookmarkedHere) AppIcons.Bookmark else AppIcons.BookmarkBorder,
                    description = if (bookmarkedHere) UiStrings.REMOVE_BOOKMARK else UiStrings.ADD_BOOKMARK,
                    onClick = onBookmarkHere,
                )
                if (!state.saved && !state.loading) IconAction(AppIcons.LibraryAdd, UiStrings.SAVE_TO_LIBRARY, onSave)
                IconAction(AppIcons.TextSize, UiStrings.DISPLAY, onDisplay)
            },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colors.background,
                scrolledContainerColor = colors.background,
                titleContentColor = colors.text,
                navigationIconContentColor = colors.accent,
                actionIconContentColor = colors.accent,
            ),
        )
        // A hairline of reading progress through the whole book (it grows from the right, like the text).
        Box(Modifier.fillMaxWidth().height(2.dp).background(colors.divider)) {
            Box(Modifier.fillMaxWidth(state.progress).fillMaxHeight().background(colors.accent))
        }
    }
}

@Composable
private fun ReaderList(state: ReaderUiState, listState: LazyListState, viewModel: ReaderViewModel) {
    val language = state.preferences.language
    val showNikud = state.preferences.showNikud
    val selection = remember(state.selection) { state.selection.toHashSet() }
    val selectionMode = state.selection.isNotEmpty()
    val rows = state.rows

    // Report the position (debounced + saved by the ViewModel) and the visible window (to preload).
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> viewModel.onScrolled(index, offset) }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            (visible.firstOrNull()?.index ?: 0) to (visible.lastOrNull()?.index ?: 0)
        }.distinctUntilChanged().collect { (first, last) -> viewModel.onVisibleRange(first, last) }
    }
    val anchor = remember { RowsAnchor() }
    SideEffect { anchor.keepPlace(rows, listState) }

    // Hebrew is the primary reading direction; English‑only mode reads left‑to‑right.
    val direction = if (language == ReadingLanguage.English) LayoutDirection.Ltr else LayoutDirection.Rtl
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
        ) {
            items(rows, key = { it.key }, contentType = { it::class }) { row ->
                when (row) {
                    is HeadingRow -> SectionHeading(row)
                    is PassageTitleRow -> PassageTitle(row, english = language == ReadingLanguage.English)
                    is SegmentRow -> {
                        val ref = SegmentRef(row.passage, row.segment.index)
                        SegmentItem(
                            row = row,
                            language = language,
                            showNikud = showNikud,
                            selected = ref in selection,
                            selectionMode = selectionMode,
                            bookmarked = segmentBookmarkId(row) in state.bookmarkedIds,
                            onToggle = { viewModel.toggleSelection(ref) },
                        )
                    }
                    is PendingRow -> PendingItem(row, onRetry = { viewModel.retry(row.passage) })
                }
            }
        }
    }
}

private fun segmentBookmarkId(row: SegmentRow): String = bookmarkId(row.leaf.tref, row.segment.index)

/**
 * Keeps the text the reader is looking at still when rows change under it. The list already anchors
 * on the first visible row's key; this covers the one case it can't — that row itself vanishing
 * (a passage's loading placeholder replaced by its text) — by pinning the next visible row that
 * survives to its current spot, in the same frame (`requestScrollToItem` before the next measure).
 */
private class RowsAnchor {
    private var rows: List<ReaderRow>? = null

    fun keepPlace(newRows: List<ReaderRow>, listState: LazyListState) {
        val changed = rows != null && rows !== newRows
        rows = newRows
        val visible = listState.layoutInfo.visibleItemsInfo
        val first = visible.firstOrNull()
        // Common case: the top row is still there (same index, or moved — the list follows its key).
        if (!changed || first == null || newRows.getOrNull(first.index)?.key == first.key) return
        val newIndex = HashMap<Any, Int>(newRows.size * 2).apply { newRows.forEachIndexed { i, row -> put(row.key, i) } }
        val survivor = visible.firstOrNull { it.key in newIndex }
        if (first.key !in newIndex && survivor != null) {
            listState.requestScrollToItem(newIndex.getValue(survivor.key), -survivor.offset)
        }
    }
}

@Composable
private fun SectionHeading(heading: HeadingRow) {
    val colors = LocalReadingColors.current
    val base = when (heading.depth) {
        1 -> 24.sp
        2 -> 20.sp
        else -> 17.sp
    }
    val scale = LocalFontScale.current
    Text(
        text = heading.heTitle,
        color = colors.text,
        fontWeight = FontWeight.Bold,
        fontSize = base * scale,
        // Scale the line height with the font (otherwise large headings collide / clip their nikud).
        lineHeight = base * 1.4f * scale,
        modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 4.dp),
    )
}

@Composable
private fun PassageTitle(row: PassageTitleRow, english: Boolean) {
    val colors = LocalReadingColors.current
    val style = if (english) englishReadingStyle() else hebrewReadingStyle()
    Text(
        text = if (english) row.leaf.title else row.leaf.heTitle,
        style = style.copy(color = colors.accent, fontWeight = FontWeight.Bold),
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun SegmentItem(
    row: SegmentRow,
    language: ReadingLanguage,
    showNikud: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    bookmarked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalReadingColors.current
    val segment = row.segment
    val secondary = colors.secondaryText
    val hebrew = remember(segment, showNikud, secondary) {
        segment.hebrew?.withNikud(showNikud)?.toAnnotatedString(secondary)
    }
    val english = remember(segment, secondary) { segment.english?.toAnnotatedString(secondary) }
    val showHebrew = language != ReadingLanguage.English && hebrew != null
    val showEnglish = language != ReadingLanguage.Hebrew && english != null

    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(if (selected) colors.highlight else Color.Transparent)
        .then(if (selectionMode) Modifier.clickable(onClickLabel = UiStrings.SELECT, onClick = onToggle) else Modifier)
        .padding(vertical = 4.dp)

    // In selection mode a tap toggles the whole segment; otherwise long‑press selects words to copy.
    val texts: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            if (showHebrew && hebrew != null) {
                Text(
                    text = hebrew,
                    style = if (segment.isRubric) rubricStyle(hebrewReadingStyle()) else hebrewReadingStyle(),
                    textAlign = if (segment.isRubric) TextAlign.Center else TextAlign.Justify,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showEnglish && english != null) {
                Text(
                    text = english,
                    style = if (segment.isRubric) rubricStyle(englishReadingStyle()) else englishReadingStyle(),
                    textAlign = if (segment.isRubric) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(top = if (showHebrew) 6.dp else 0.dp),
                )
            }
        }
    }

    Row(rowModifier) {
        if (!segment.isRubric) {
            SegmentNumber(
                label = if (language == ReadingLanguage.English) segment.enLabel else segment.label,
                bookmarked = bookmarked,
                onClick = onToggle,
            )
        } else {
            Spacer(Modifier.widthIn(min = 40.dp))
        }
        Box(Modifier.weight(1f)) {
            if (selectionMode) texts() else SelectionContainer { texts() }
        }
    }
}

@Composable
private fun rubricStyle(base: TextStyle) =
    base.copy(color = LocalReadingColors.current.secondaryText, fontStyle = FontStyle.Italic)

/**
 * The verse/halacha number — a tap target that starts "select to copy / bookmark". A small ribbon
 * marks a bookmarked segment.
 */
@Composable
private fun SegmentNumber(label: String, bookmarked: Boolean, onClick: () -> Unit) {
    val colors = LocalReadingColors.current
    val scale = LocalFontScale.current
    Column(
        Modifier
            .widthIn(min = 40.dp)
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = UiStrings.SELECT, onClick = onClick)
            .semantics { contentDescription = if (bookmarked) "$label · ${UiStrings.BOOKMARK}" else label }
            .padding(top = 6.dp, end = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = colors.accent,
            fontSize = (16 * scale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
        if (bookmarked) {
            Icon(AppIcons.Bookmark, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun LazyItemScope.PendingItem(row: PendingRow, onRetry: () -> Unit) {
    val colors = LocalReadingColors.current
    // Tall on purpose: only one placeholder fits on screen, so a fast fling can't start a burst of loads.
    Box(Modifier.fillParentMaxHeight(0.7f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (row.error == null) {
            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        } else {
            ErrorState(UiStrings.ERROR_SECTION, row.error, onRetry)
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onCopy: () -> Unit, onBookmark: () -> Unit, onClose: () -> Unit) {
    val colors = LocalReadingColors.current
    Surface(color = colors.surface, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconAction(AppIcons.Close, UiStrings.CLOSE, onClose, tint = colors.secondaryText)
            Column(Modifier.weight(1f)) {
                Text(UiStrings.selectedCount(count), color = colors.text, style = MaterialTheme.typography.titleSmall)
                Text(
                    UiStrings.SELECTION_HINT,
                    color = colors.secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onBookmark) {
                Icon(AppIcons.BookmarkBorder, contentDescription = null, tint = colors.accent)
                Text(UiStrings.BOOKMARK, color = colors.accent, modifier = Modifier.padding(start = 4.dp))
            }
            TextButton(onClick = onCopy) {
                Icon(AppIcons.Copy, contentDescription = null, tint = colors.accent)
                Text(UiStrings.COPY, color = colors.accent, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
