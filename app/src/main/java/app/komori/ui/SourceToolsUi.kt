package app.komori.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.komori.data.*

@Composable
internal fun SaveSearchDialog(vm: ReaderViewModel, dismiss: () -> Unit) {
    var name by remember { mutableStateOf(vm.searchQuery.value.take(100)) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Save search") }, text = {
        OutlinedTextField(name, { name = it.take(100) }, label = { Text("Search name") }, singleLine = true)
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.saveCurrentSearch(name); dismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
internal fun SavedSearchDialog(vm: ReaderViewModel, dismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val rows = remember(settings.savedSearches) { savedSearches(settings.savedSearches) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Saved searches") }, text = {
        LazyColumn(Modifier.heightIn(max = 400.dp)) {
            if (rows.isEmpty()) item { Text("Save a search from Discover's source options.") }
            items(rows, key = { it.id }) { row ->
                Column {
                    Text(row.name, style = MaterialTheme.typography.titleSmall)
                    Text("${sourceNames[row.navigation.source]} · ${row.navigation.filter.searchType} · ${row.navigation.filter.sort.ifEmpty { "Default sort" }}", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { vm.restoreSearch(row); dismiss() }) { Text("Open") }
                        TextButton(onClick = { vm.removeSavedSearch(row.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Close") } })
}

@Composable
internal fun SourceDiagnostics(vm: ReaderViewModel) {
    val statuses by vm.sourceStatus.collectAsStateWithLifecycle()
    Text("Source capabilities", style = MaterialTheme.typography.titleMedium)
    Text("Toggles control Discover and automatic checks. Existing Library titles remain accessible.", style = MaterialTheme.typography.bodySmall)
    sourceNames.forEach { (key, name) ->
        Text(name, style = MaterialTheme.typography.titleSmall)
        Text("${sourceSearchTypes(key).joinToString()} · ${vm.app.source.sorts(key).joinToString()}", style = MaterialTheme.typography.bodySmall)
        Text(statuses[key] ?: "Native access has not been checked in this session.", style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(onClick = { vm.testSource(key) }) { Text("Test access") }
            VerifySourceButton(vm, key)
        }
    }
}
