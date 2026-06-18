package app.opensefer.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.opensefer.core.model.TocBranch
import app.opensefer.core.model.TocLeaf
import app.opensefer.core.model.TocNode
import app.opensefer.ui.theme.LocalReadingColors

/**
 * A drill‑down tree over a book's structure. Tap a topic (a [TocBranch]) to go deeper; tap a
 * passage (a [TocLeaf]) — or "read from here" on a topic — to jump the continuous reader to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreeNavigatorSheet(
    root: TocBranch,
    onJump: (tref: String) -> Unit,
    onAbout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalReadingColors.current
    val stack = remember { mutableStateListOf(root) }
    val current = stack.last()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(bottom = 12.dp)) {
            // Book title — pinned at the top — with the "about this book" affordance beside it.
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = root.heTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onAbout) {
                    Icon(Icons.Outlined.Info, contentDescription = "About this book", tint = colors.accent)
                }
            }
            HorizontalDivider(color = colors.secondaryText.copy(alpha = 0.2f))

            // Drill‑down breadcrumb — shown only while inside a sub‑section (at the root it would
            // just repeat the pinned book title above).
            if (stack.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { stack.removeAt(stack.lastIndex) }) {
                        Text("‹ ${stack[stack.size - 2].heTitle}", color = colors.accent)
                    }
                    Text(
                        text = current.heTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }

            if (stack.size > 1) {
                TextButton(
                    onClick = { firstLeafTref(current)?.let(onJump) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text("↓ קרא מכאן", color = colors.accent) }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                items(current.children) { child ->
                    Surface(
                        onClick = {
                            when (child) {
                                is TocBranch -> stack.add(child)
                                is TocLeaf -> onJump(child.tref)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = colors.surface,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(child.heTitle, color = colors.text, style = MaterialTheme.typography.bodyLarge)
                            if (child is TocBranch) Text("›", color = colors.secondaryText)
                        }
                    }
                }
            }
        }
    }
}

private fun firstLeafTref(node: TocNode): String? = when (node) {
    is TocLeaf -> node.tref
    is TocBranch -> node.children.firstNotNullOfOrNull { firstLeafTref(it) }
}
