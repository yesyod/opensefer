package app.opensefer.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.domain.OfflineStorage
import app.opensefer.ui.UiStrings
import app.opensefer.ui.components.AppTopBar
import app.opensefer.ui.theme.LocalReadingColors
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Credits & attribution. Sefaria's data terms require crediting the source/edition; this screen
 * carries that, the project's own license note, and the on‑device text storage (with a way to free it).
 * (BLUEPRINT §7.4 / §13)
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    storage: OfflineStorage = koinInject(),
    library: LibraryRepository = koinInject(),
) {
    val colors = LocalReadingColors.current
    val scope = rememberCoroutineScope()
    var savedBytes by remember { mutableStateOf<Long?>(null) }
    var confirmFreeSpace by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { savedBytes = storage.sizeBytes() }

    Scaffold(containerColor = colors.background, topBar = { AppTopBar(UiStrings.ABOUT, onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Paragraph(UiStrings.APP_NAME, MaterialTheme.typography.headlineSmall, colors.text)
            Paragraph(
                "קורא מינימליסטי בקוד פתוח לספרי הקודש — בלי חשבון ובלי הסחות דעת. נבנה ב‑Kotlin Multiplatform " +
                    "וב‑Compose Multiplatform.",
                MaterialTheme.typography.bodyMedium,
                colors.secondaryText,
            )
            Paragraph("הטקסטים", MaterialTheme.typography.titleMedium, colors.text)
            Paragraph(
                "הטקסטים מגיעים מספריא (sefaria.org) ושמורים לבעליהם, תחת רישיונות Creative Commons " +
                    "(CC0 / CC‑BY / CC‑BY‑SA, לפי מהדורה). OpenSefer אינו קשור לספריא ואינו מטעמה.",
                MaterialTheme.typography.bodySmall,
                colors.secondaryText,
            )
            Paragraph(UiStrings.SAVED_TEXTS, MaterialTheme.typography.titleMedium, colors.text)
            Paragraph(
                "כל ספר שפתחתם נשמר במכשיר, כדי שייפתח מיד וגם בלי אינטרנט. ספרים שבספרייה נשמרים במלואם." +
                    (savedBytes?.let { "\nבשימוש כעת: ${formatSize(it)}" } ?: ""),
                MaterialTheme.typography.bodySmall,
                colors.secondaryText,
            )
            OutlinedButton(
                onClick = { confirmFreeSpace = true },
                enabled = (savedBytes ?: 0L) > 0L,
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(UiStrings.FREE_SPACE, color = colors.accent) }
            Paragraph("רישיון", MaterialTheme.typography.titleMedium, colors.text)
            Paragraph(
                "קוד האפליקציה מופץ ברישיון Apache‑2.0. בקוד המקור תמצאו פרטי ייחוס והנחיות לתרומה.",
                MaterialTheme.typography.bodySmall,
                colors.secondaryText,
            )
        }
    }

    if (confirmFreeSpace) {
        FreeSpaceDialog(
            onConfirm = {
                confirmFreeSpace = false
                scope.launch {
                    // The saved books stay whole on the device; only what was merely read goes.
                    storage.clearExcept(library.books.value.mapTo(HashSet()) { it.title })
                    savedBytes = storage.sizeBytes()
                }
            },
            onDismiss = { confirmFreeSpace = false },
        )
    }
}

@Composable
private fun FreeSpaceDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalReadingColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(UiStrings.FREE_SPACE_TITLE) },
        text = { Text(UiStrings.FREE_SPACE_BODY) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(UiStrings.FREE_SPACE, color = colors.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(UiStrings.CANCEL, color = colors.accent) } },
        containerColor = colors.surface,
        titleContentColor = colors.text,
        textContentColor = colors.secondaryText,
    )
}

private const val KB = 1024.0
private const val MB = KB * 1024

internal fun formatSize(bytes: Long): String = when {
    bytes >= MB -> "${(bytes / MB * 10).toLong() / 10.0} MB"
    bytes >= KB -> "${(bytes / KB).toLong()} KB"
    else -> "$bytes B"
}

@Composable
private fun Paragraph(text: String, style: TextStyle, color: Color) {
    Text(text = text, style = style, color = color, modifier = Modifier.padding(top = 14.dp))
}
