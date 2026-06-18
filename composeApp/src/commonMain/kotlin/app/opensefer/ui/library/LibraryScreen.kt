package app.opensefer.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.model.LibraryBook
import app.opensefer.ui.theme.LocalReadingColors
import org.koin.compose.koinInject

/**
 * Home: only the books the user chose. Tapping a book resumes reading from the last position
 * (Kindle‑style); long‑press opens its table of contents; the `+` adds a book. (BLUEPRINT §10.3)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onContinueReading: (LibraryBook) -> Unit,
    onBrowse: (LibraryBook) -> Unit,
    onAddBook: () -> Unit,
    onAbout: () -> Unit,
    libraryRepository: LibraryRepository = koinInject(),
) {
    val books by libraryRepository.books.collectAsStateWithLifecycle()
    val colors = LocalReadingColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenSefer") },
                actions = {
                    TextButton(onClick = onAbout) { Text("About") }
                    TextButton(onClick = onAddBook) { Text("+ Add") }
                },
            )
        },
    ) { padding ->
        if (books.isEmpty()) {
            EmptyLibrary(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(books, key = { it.title }) { book ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onContinueReading(book) },
                                onLongClick = { onBrowse(book) },
                            ),
                    ) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                            Text(
                                text = book.heTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.text,
                            )
                            Text(
                                text = book.lastTref
                                    ?.let { "Continue · ${book.lastLabel.orEmpty()}".trimEnd(' ', '·') }
                                    ?: "Tap to browse",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier) {
    val colors = LocalReadingColors.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Your library is empty",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            Text(
                "Tap + to add a book and start reading.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
