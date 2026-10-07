package app.opensefer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opensefer.ui.UiStrings
import app.opensefer.ui.icons.AppIcons
import app.opensefer.ui.theme.LocalReadingColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The app's top bar: reading‑surface colours, an optional back arrow, a one‑line title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalReadingColors.current
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
        navigationIcon = { if (onBack != null) IconAction(AppIcons.Back, UiStrings.BACK, onBack) },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.background,
            scrolledContainerColor = colors.surface,
            titleContentColor = colors.text,
            navigationIconContentColor = colors.accent,
            actionIconContentColor = colors.accent,
        ),
    )
}

/** A 48dp icon button with a Hebrew content description (screen readers announce the action). */
@Composable
fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = LocalReadingColors.current.accent,
) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = description, tint = tint) }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalReadingColors.current.accent)
    }
}

/** Centered failure state with a headline, the reason, and a retry. */
@Composable
fun ErrorState(
    title: String,
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReadingColors.current
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text, textAlign = TextAlign.Center)
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        TextButton(onClick = onRetry) { Text(UiStrings.RETRY, color = colors.accent) }
    }
}

/** A section title, marked as a heading for screen readers. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val colors = LocalReadingColors.current
    Box(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.text,
            modifier = Modifier.align(Alignment.CenterStart).semantics { heading() },
        )
        Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

/**
 * Shows [message] with an "undo" action — replacing any snackbar on screen rather than queueing
 * behind it, and timing out on its own (Material makes snackbars with an action wait indefinitely).
 */
fun CoroutineScope.showUndoSnackbar(snackbar: SnackbarHostState, message: String, onUndo: () -> Unit) {
    launch {
        snackbar.currentSnackbarData?.dismiss()
        val result = snackbar.showSnackbar(message, actionLabel = UiStrings.UNDO, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) onUndo()
    }
}
