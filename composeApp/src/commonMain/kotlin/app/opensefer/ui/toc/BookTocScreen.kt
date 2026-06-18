package app.opensefer.ui.toc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.domain.TextRepository
import app.opensefer.core.model.BookContents
import app.opensefer.core.model.LibraryBook
import app.opensefer.ui.UiStrings
import app.opensefer.ui.theme.LocalReadingColors
import org.koin.compose.koinInject

/**
 * A book's table of contents — a flat, breadcrumb'd list of every readable section. Works for
 * simple numbered books (פרק א, פרק ב…) and complex named ones (a Siddur's prayers). Reached from
 * search (preview + add) and from the library (browse). Tapping a section opens the reader.
 *
 * State is kept locally ([mutableStateOf]) rather than in a ViewModel: this is a leaf screen with no
 * shared state and no interactive sheets, so a ViewModel would be ceremony. The reader, which juggles
 * several sheets and a persisted reading position, earns one. (BLUEPRINT §10.3)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookTocScreen(
    title: String,
    heTitle: String,
    onBack: () -> Unit,
    onOpen: (tref: String) -> Unit,
    textRepository: TextRepository = koinInject(),
    libraryRepository: LibraryRepository = koinInject(),
) {
    val colors = LocalReadingColors.current
    var contents by remember { mutableStateOf<BookContents?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(title, reloadKey) {
        loading = true
        error = null
        textRepository.getContents(title).fold(
            onSuccess = { contents = it },
            onFailure = { e ->
                contents = null
                error = e.message ?: UiStrings.ERROR_TOC
            },
        )
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(heTitle, color = colors.text) },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Button(
                    onClick = { libraryRepository.add(LibraryBook(title, heTitle)) },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("+ Add to library") }
            }

            when {
                loading -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                }

                error != null -> item {
                    TocError(message = error.orEmpty(), onRetry = { reloadKey++ })
                }

                else -> items(contents?.leaves.orEmpty(), key = { it.tref }) { leaf ->
                    Surface(
                        onClick = { onOpen(leaf.tref) },
                        shape = RoundedCornerShape(10.dp),
                        color = colors.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = leaf.crumb,
                            color = colors.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Centered failure state with a retry — mirrors the reader's and about‑screen's error pattern. */
@Composable
private fun TocError(message: String, onRetry: () -> Unit) {
    val colors = LocalReadingColors.current
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = colors.text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text(UiStrings.RETRY, color = colors.accent) }
    }
}
