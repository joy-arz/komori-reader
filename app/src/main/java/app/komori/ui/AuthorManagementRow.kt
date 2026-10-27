package app.komori.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import app.komori.data.Creator
import app.komori.data.sourceName

/** The row owns its action/selection node; the checkbox is a visual indicator, not another target. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun AuthorManagementRow(
    creator: Creator,
    selecting: Boolean,
    selected: Boolean,
    toggle: () -> Unit,
    open: () -> Unit,
    unfollow: () -> Unit,
) {
    val description = if (selecting) "Select ${creator.name}" else "Manage author ${creator.name}"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                role = if (selecting) Role.Checkbox else Role.Button,
                onLongClickLabel = "Select author",
                onLongClick = toggle,
                onClickLabel = if (selecting) "Toggle selection" else "Browse author works",
                onClick = if (selecting) toggle else open,
            ).semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = selected
                if (selecting) toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
            }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(creator.name, style = MaterialTheme.typography.bodyLarge)
            Text("${sourceName(creator.id)} · ${creator.role}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selecting) Checkbox(selected, null, Modifier.clearAndSetSemantics { })
        else TextButton(onClick = unfollow) { Text("Unfollow") }
    }
}
