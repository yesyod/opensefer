package app.opensefer.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.domain.SearchRepository
import app.opensefer.core.model.BookSearchResult
import app.opensefer.ui.UiStrings
import app.opensefer.ui.components.AppTopBar
import app.opensefer.ui.components.IconAction
import app.opensefer.ui.icons.AppIcons
import app.opensefer.ui.theme.LocalReadingColors
import org.koin.compose.koinInject

/** Well‑known books offered before the first keystroke — one tap to open, one to save. */
private val Suggestions = listOf(
    BookSearchResult("Genesis", "בראשית", "ref"),
    BookSearchResult("Psalms", "תהילים", "ref"),
    BookSearchResult("Pirkei Avot", "פרקי אבות", "ref"),
    BookSearchResult("Mishnah Berakhot", "משנה ברכות", "ref"),
    BookSearchResult("Berakhot", "ברכות", "ref"),
    BookSearchResult("Mishneh Torah, Repentance", "משנה תורה, הלכות תשובה", "ref"),
    BookSearchResult("Mesillat Yesharim", "מסילת ישרים", "ref"),
    BookSearchResult("Siddur Ashkenaz", "סידור אשכנז", "ref"),
)

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onPick: (BookSearchResult) -> Unit,
    searchRepository: SearchRepository = koinInject(),
    libraryRepository: LibraryRepository = koinInject(),
) {
    val viewModel = viewModel { SearchViewModel(searchRepository, libraryRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalReadingColors.current
    // The field's text lives here, updated synchronously — feeding a TextField from an async
    // StateFlow can drop or reorder keystrokes. The ViewModel just hears about each change.
    var query by rememberSaveable { mutableStateOf(state.query) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }
    // Scrolling the results hides the keyboard, so the whole list is reachable.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { if (it) keyboard?.hide() }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = { AppTopBar(title = UiStrings.ADD_BOOK, onBack = onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.onQueryChange(it)
                },
                placeholder = { Text(UiStrings.SEARCH_HINT) },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = colors.secondaryText) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconAction(AppIcons.Close, UiStrings.SEARCH_CLEAR, {
                            query = ""
                            viewModel.onQueryChange("")
                            focus.requestFocus()
                        }, tint = colors.secondaryText)
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.divider,
                    cursorColor = colors.accent,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
            )
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = colors.accent)
            }
            val showSuggestions = query.isBlank()
            val results = if (showSuggestions) Suggestions else state.results
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                if (showSuggestions) {
                    item(key = "suggestions") { Hint(UiStrings.SEARCH_SUGGESTIONS, alignStart = true) }
                }
                state.error?.takeUnless { showSuggestions }?.let { item(key = "error") { Hint(it) } }
                val nothingFound = state.searched && state.error == null && results.isEmpty()
                if (!showSuggestions && nothingFound) item(key = "empty") { Hint(UiStrings.NO_RESULTS) }
                items(results, key = { it.title }) { result ->
                    ResultRow(
                        result = result,
                        saved = result.title in state.savedTitles,
                        onOpen = { onPick(result) },
                        onToggleSaved = { viewModel.toggleSaved(result) },
                    )
                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String, alignStart: Boolean = false) {
    Text(
        text = text,
        color = LocalReadingColors.current.secondaryText,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = if (alignStart) TextAlign.Start else TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

@Composable
private fun ResultRow(result: BookSearchResult, saved: Boolean, onOpen: () -> Unit, onToggleSaved: () -> Unit) {
    val colors = LocalReadingColors.current
    Surface(onClick = onOpen, color = colors.background, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(result.heTitle, style = MaterialTheme.typography.titleMedium, color = colors.text)
                if (result.title != result.heTitle) {
                    Text(result.title, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
                }
            }
            Box {
                IconAction(
                    icon = if (saved) AppIcons.LibraryAdded else AppIcons.LibraryAdd,
                    description = if (saved) UiStrings.REMOVE_FROM_LIBRARY else UiStrings.SAVE_TO_LIBRARY,
                    onClick = onToggleSaved,
                    tint = if (saved) colors.accent else colors.secondaryText,
                )
            }
        }
    }
}
