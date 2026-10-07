package app.opensefer.ui.toc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opensefer.core.model.TocBranch
import app.opensefer.core.model.TocLeaf
import app.opensefer.core.model.TocNode
import app.opensefer.ui.UiStrings
import app.opensefer.ui.icons.AppIcons
import app.opensefer.ui.theme.LocalReadingColors

/**
 * Where a drill‑down through a book's structure is: a stack of branches from the root. Opens on the
 * branch holding [initialTref] (the passage being read), so the reader's place is one glance away.
 */
@Stable
class TocBrowserState(root: TocBranch, initialTref: String?) {
    val stack = mutableStateListOf<TocBranch>().apply { addAll(pathTo(root, initialTref) ?: listOf(root)) }
    val current: TocBranch get() = stack.last()
    val canGoUp: Boolean get() = stack.size > 1

    fun open(branch: TocBranch) {
        stack.add(branch)
    }

    fun up() {
        if (canGoUp) stack.removeAt(stack.lastIndex)
    }
}

@Composable
fun rememberTocBrowserState(root: TocBranch, initialTref: String? = null): TocBrowserState =
    remember(root) { TocBrowserState(root, initialTref) }

/** The branches from [root] down to the one directly containing [tref]; null if it isn't in the book. */
private fun pathTo(root: TocBranch, tref: String?): List<TocBranch>? {
    if (tref == null) return null
    fun walk(branch: TocBranch, path: List<TocBranch>): List<TocBranch>? {
        val here = path + branch
        branch.children.forEach { child ->
            when (child) {
                is TocLeaf -> if (child.tref == tref) return here
                is TocBranch -> walk(child, here)?.let { return it }
            }
        }
        return null
    }
    return walk(root, emptyList())
}

private const val GRID_COLUMNS = 6

/**
 * The table of contents as lazy‑list items: a breadcrumb while inside a section, then either a compact
 * chapter **grid** (a branch of plain numbered chapters — א ב ג…) or a list of named entries.
 * [currentTref] is highlighted.
 */
fun LazyListScope.tocItems(
    state: TocBrowserState,
    currentTref: String?,
    onOpen: (TocLeaf) -> Unit,
) {
    val branch = state.current
    if (state.canGoUp) {
        item(key = "toc-crumb-${state.stack.size}") { Breadcrumb(state, onOpen) }
    }
    val leaves = branch.children.filterIsInstance<TocLeaf>()
    val isChapterGrid = leaves.size == branch.children.size && leaves.size > 1 &&
        leaves.all { it.shortLabel.length <= SHORT_LABEL_MAX }
    if (isChapterGrid) {
        items(leaves.chunked(GRID_COLUMNS), key = { "toc-grid-${it.first().tref}" }) { rowLeaves ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowLeaves.forEach { leaf ->
                    ChapterChip(leaf, current = leaf.tref == currentTref, onClick = { onOpen(leaf) }, Modifier.weight(1f))
                }
                repeat(GRID_COLUMNS - rowLeaves.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    } else {
        // Index in the key: sibling sections may share a name, and lazy keys must be unique.
        itemsIndexed(branch.children, key = { i, child -> "toc-$i-${child.keyOf()}" }) { _, child ->
            TocRow(
                node = child,
                current = child is TocLeaf && child.tref == currentTref,
                onClick = {
                    when (child) {
                        is TocBranch -> state.open(child)
                        is TocLeaf -> onOpen(child)
                    }
                },
            )
        }
    }
}

private const val SHORT_LABEL_MAX = 4

private fun TocNode.keyOf(): String = when (this) {
    is TocLeaf -> tref
    is TocBranch -> "b:$title:$heTitle:${children.size}"
}

@Composable
private fun Breadcrumb(state: TocBrowserState, onOpen: (TocLeaf) -> Unit) {
    val colors = LocalReadingColors.current
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = state::up) {
            Icon(AppIcons.Back, contentDescription = null, tint = colors.accent)
            Text(
                text = state.stack[state.stack.size - 2].heTitle,
                color = colors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            text = state.current.heTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        firstLeaf(state.current)?.let { leaf ->
            TextButton(onClick = { onOpen(leaf) }) { Text(UiStrings.READ_FROM_HERE, color = colors.accent) }
        }
    }
}

@Composable
private fun ChapterChip(leaf: TocLeaf, current: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalReadingColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (current) colors.accent else colors.surface,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = leaf.shortLabel,
                color = if (current) colors.onAccent else colors.text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun TocRow(node: TocNode, current: Boolean, onClick: () -> Unit) {
    val colors = LocalReadingColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (current) colors.highlight else colors.surface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = node.heTitle,
                color = colors.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            if (node is TocBranch) Icon(AppIcons.Forward, contentDescription = null, tint = colors.secondaryText)
        }
    }
}

private fun firstLeaf(node: TocNode): TocLeaf? = when (node) {
    is TocLeaf -> node
    is TocBranch -> node.children.firstNotNullOfOrNull { firstLeaf(it) }
}
