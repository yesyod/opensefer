package app.opensefer.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opensefer.core.domain.GetChapterUseCase
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.domain.ReadingLanguage
import app.opensefer.core.domain.ReadingPreferencesRepository
import app.opensefer.core.domain.TextRepository
import app.opensefer.core.model.ChapterText
import app.opensefer.core.model.ReadingHeading
import app.opensefer.core.model.ReadingPassage
import app.opensefer.core.model.Segment
import app.opensefer.core.model.TocLeaf
import app.opensefer.core.model.indexOfPassage
import app.opensefer.ui.UiStrings
import app.opensefer.ui.text.toAnnotatedString
import app.opensefer.ui.text.withNikud
import app.opensefer.ui.theme.LocalFontScale
import app.opensefer.ui.theme.LocalReadingColors
import app.opensefer.ui.theme.englishReadingStyle
import app.opensefer.ui.theme.hebrewReadingStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ReaderScreen(
    bookTitle: String,
    heBookTitle: String,
    startTref: String?,
    onBack: () -> Unit,
    onAboutBook: () -> Unit,
    textRepository: TextRepository = koinInject(),
    preferencesRepository: ReadingPreferencesRepository = koinInject(),
    libraryRepository: LibraryRepository = koinInject(),
    getChapter: GetChapterUseCase = koinInject(),
) {
    val viewModel = viewModel(key = bookTitle) {
        ReaderViewModel(
            textRepository,
            preferencesRepository,
            libraryRepository,
            getChapter,
            bookTitle,
            heBookTitle,
            startTref,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
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
    val scope = rememberCoroutineScope()

    // Open where the user left off (or at a section they jumped in from).
    LaunchedEffect(state.items, state.startTref) {
        val tref = state.startTref ?: return@LaunchedEffect
        val idx = state.items.indexOfPassage(tref)
        if (idx >= 0) listState.scrollToItem(idx)
    }
    // Remember the top‑most visible passage so the book reopens there next time.
    LaunchedEffect(state.items) {
        if (state.items.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { i ->
            (state.items.take((i + 1).coerceAtMost(state.items.size)).lastOrNull { it is ReadingPassage } as? ReadingPassage)
                ?.let { viewModel.rememberPosition(it.leaf.tref, it.leaf.heTitle) }
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.text,
                ),
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Back", color = colors.accent) } },
                title = {
                    val first = listState.firstVisibleItemIndex
                    val section = state.items.take((first + 1).coerceAtMost(state.items.size))
                        .lastOrNull { it is ReadingHeading } as? ReadingHeading
                    val label = section?.heTitle
                        ?: (state.items.getOrNull(first) as? ReadingPassage)?.leaf?.heTitle
                        ?: state.heBookTitle
                    TextButton(onClick = viewModel::openTree) {
                        Text(
                            text = "${state.heBookTitle} · $label ⌄",
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::openDisplaySheet) { Text("אA", color = colors.accent) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.items.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.accent)

                state.error != null && state.items.isEmpty() ->
                    ReaderError(state.error, viewModel::loadContents)

                else -> ContinuousBody(state, viewModel, listState)
            }
        }
    }

    if (state.showTree && state.contents != null) {
        TreeNavigatorSheet(
            root = state.contents.root,
            onJump = { tref ->
                val idx = state.items.indexOfPassage(tref)
                if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                viewModel.closeTree()
            },
            onAbout = {
                viewModel.closeTree()
                onAboutBook()
            },
            onDismiss = viewModel::closeTree,
        )
    }
    if (state.showDisplaySheet) {
        DisplayOptionsSheet(
            preferences = state.preferences,
            onFontScale = viewModel::setFontScale,
            onTheme = viewModel::setTheme,
            onLanguage = viewModel::setLanguage,
            onToggleNikud = viewModel::toggleNikud,
            onDismiss = viewModel::closeDisplaySheet,
        )
    }
}

@Composable
private fun ContinuousBody(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    // Hebrew is the primary reading direction; English‑only mode reads left‑to‑right. Mixed
    // Latin/numbers within a direction resolve via Compose's BiDi algorithm.
    val layoutDirection = if (state.preferences.language == ReadingLanguage.English) {
        LayoutDirection.Ltr
    } else {
        LayoutDirection.Rtl
    }
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) {
            items(state.items, key = { it.key }) { item ->
                when (item) {
                    is ReadingHeading -> HeadingRow(item)
                    is ReadingPassage -> PassageBlock(
                        leaf = item.leaf,
                        language = state.preferences.language,
                        showNikud = state.preferences.showNikud,
                        loadPassage = viewModel::passage,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeadingRow(heading: ReadingHeading) {
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
private fun PassageBlock(
    leaf: TocLeaf,
    language: ReadingLanguage,
    showNikud: Boolean,
    loadPassage: suspend (String) -> Result<ChapterText>,
) {
    val colors = LocalReadingColors.current
    val result by produceState<Result<ChapterText>?>(null, leaf.tref) { value = loadPassage(leaf.tref) }

    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp)) {
        Text(
            text = leaf.heTitle,
            style = hebrewReadingStyle().copy(color = colors.accent, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        when (val r = result) {
            null -> Text("…", style = hebrewReadingStyle().copy(color = colors.secondaryText))
            else -> r.fold(
                onSuccess = { chapter ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        chapter.segments.forEach { SegmentRow(it, language, showNikud, colors.secondaryText) }
                    }
                },
                onFailure = {
                    Text(
                        text = UiStrings.ERROR_SECTION,
                        style = hebrewReadingStyle().copy(color = colors.secondaryText),
                    )
                },
            )
        }
    }
}

@Composable
private fun SegmentRow(
    segment: Segment,
    language: ReadingLanguage,
    showNikud: Boolean,
    secondary: Color,
) {
    val colors = LocalReadingColors.current
    if (segment.isRubric) {
        RubricRow(segment, language, colors.secondaryText)
        return
    }
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = segment.label,
            style = hebrewReadingStyle().copy(color = colors.accent),
            modifier = Modifier.width(34.dp),
        )
        Column(Modifier.fillMaxWidth()) {
            if (language != ReadingLanguage.English) {
                segment.hebrew?.withNikud(showNikud)?.let { he ->
                    Text(
                        text = he.toAnnotatedString(secondary),
                        style = hebrewReadingStyle(),
                        textAlign = TextAlign.Justify,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (language != ReadingLanguage.Hebrew) {
                segment.english?.let { en ->
                    Text(
                        text = en.toAnnotatedString(secondary),
                        style = englishReadingStyle(),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** An instruction line ("say this:") — centred, italic, muted, and NOT numbered. */
@Composable
private fun RubricRow(segment: Segment, language: ReadingLanguage, muted: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        if (language != ReadingLanguage.English) {
            segment.hebrew?.let { he ->
                Text(
                    text = he.toAnnotatedString(muted),
                    style = hebrewReadingStyle().copy(color = muted, fontStyle = FontStyle.Italic),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (language != ReadingLanguage.Hebrew) {
            segment.english?.let { en ->
                Text(
                    text = en.toAnnotatedString(muted),
                    style = englishReadingStyle().copy(color = muted, fontStyle = FontStyle.Italic),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ReaderError(message: String, onRetry: () -> Unit) {
    val colors = LocalReadingColors.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(UiStrings.ERROR_BOOK, style = MaterialTheme.typography.titleMedium, color = colors.text)
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        TextButton(onClick = onRetry) { Text(UiStrings.RETRY, color = colors.accent) }
    }
}
