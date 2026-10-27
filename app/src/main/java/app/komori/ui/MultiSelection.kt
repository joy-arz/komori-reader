package app.komori.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

@Stable
internal class MultiSelection(active: Boolean = false, ids: List<String> = emptyList()) {
    var active by mutableStateOf(active)
    var ids by mutableStateOf(ids)

    fun toggle(id: String) {
        active = true
        ids = if (id in ids) ids - id else ids + id
    }

    fun clear() {
        active = false
        ids = emptyList()
    }
}

@Composable
internal fun rememberMultiSelection(available: List<String>): MultiSelection {
    val selection =
        rememberSaveable(
            saver =
                listSaver(
                    save = { listOf(if (it.active) "1" else "0") + it.ids },
                    restore = { MultiSelection(it.first() == "1", it.drop(1)) },
                )
        ) {
            MultiSelection()
        }
    LaunchedEffect(available) { selection.ids = selection.ids.filter { it in available } }
    BackHandler(selection.active) { selection.clear() }
    return selection
}

internal data class BatchAction(
    val label: String,
    val destructive: Boolean = false,
    val run: (List<String>) -> Unit,
)

@Composable
internal fun SelectionBar(
    selection: MultiSelection,
    available: List<String>,
    actions: List<BatchAction>,
) {
    if (!selection.active) return
    var menu by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<BatchAction, List<String>>?>(null) }
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${selection.ids.size} selected",
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
        )
        TextButton(
            onClick = {
                selection.ids = if (selection.ids.size == available.size) emptyList() else available
            }
        ) {
            Text(if (selection.ids.size == available.size) "Deselect all" else "Select all")
        }
        Box {
            TextButton(enabled = selection.ids.isNotEmpty(), onClick = { menu = true }) {
                Text("Actions")
            }
            DropdownMenu(menu, { menu = false }) {
                actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        onClick = {
                            menu = false
                            val ids = selection.ids.toList()
                            if (action.destructive) pending = action to ids
                            else {
                                action.run(ids)
                                selection.clear()
                            }
                        },
                    )
                }
            }
        }
        TextButton(onClick = { selection.clear() }, modifier = Modifier.semantics { contentDescription = "End selection" }) { Text("Done") }
    }
    pending?.let { (action, ids) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(action.label) },
            text = { Text("Apply to ${ids.size} selected items?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        action.run(ids)
                        pending = null
                        selection.clear()
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}
