package app.opensefer.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.domain.SearchRepository
import app.opensefer.core.model.BookSearchResult
import app.opensefer.ui.theme.LocalReadingColors
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add a book") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search Sefaria — e.g. Rambam, Teshuva") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            if (state.loading) {
                CircularProgressIndicator(Modifier.padding(16.dp), color = colors.accent)
            }
            state.error?.let { Text(it, color = colors.secondaryText, modifier = Modifier.padding(horizontal = 16.dp)) }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.results, key = { it.title }) { result ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(result) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(result.heTitle, style = MaterialTheme.typography.titleMedium, color = colors.text)
                        Text(
                            buildString {
                                append(result.title)
                                result.category?.let { append(" · $it") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText,
                        )
                    }
                    HorizontalDivider(color = colors.divider)
                }
            }
        }
    }
}
