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

/** One author action owns selection semantics; normal Unfollow remains a separate action. */
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
    val click = if (selecting) toggle else open
    val clickLabel = if (selecting) "Toggle selection" else "Browse author works"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = 56.dp)
                .clearAndSetSemantics {
                    contentDescription = description
                    stateDescription = "${sourceName(creator.id)} · ${creator.role}"
                    role = if (selecting) Role.Checkbox else Role.Button
                    if (selecting) {
                        this.selected = selected
                        toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
                    }
                    onClick(clickLabel) { click(); true }
                    onLongClick("Select author") { toggle(); true }
                }.combinedClickable(onLongClick = toggle, onClick = click),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(creator.name, style = MaterialTheme.typography.bodyLarge)
                Text("${sourceName(creator.id)} · ${creator.role}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selecting) Checkbox(selected, null)
        }
        if (!selecting) TextButton(onClick = unfollow) { Text("Unfollow") }
    }
}
