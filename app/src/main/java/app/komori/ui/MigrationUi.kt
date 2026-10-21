@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.komori.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.komori.data.*

@Composable
internal fun MigrationDialog(reader: ReaderViewModel, source: Manga, dismiss: () -> Unit, vm: MigrationViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var sourceMenu by remember { mutableStateOf(false) }
    var keepOriginal by remember { mutableStateOf(true) }
    var copyPage by remember { mutableStateOf(false) }
    var mappingId by remember { mutableStateOf<String?>(null) }
    var chapterQuery by remember { mutableStateOf("") }
    LaunchedEffect(source.id) { vm.begin(source) }
    DisposableEffect(vm) { onDispose { vm.close() } }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Review another edition", style = MaterialTheme.typography.headlineSmall)
                Text("From ${sourceName(source.id)} · ${source.title}", style = MaterialTheme.typography.bodySmall)
                Text("Titles are never merged by name. Review the destination and chapter mapping before applying.", style = MaterialTheme.typography.bodySmall)
            }
            if (state.selected == null) {
                item {
                    OutlinedTextField(state.query, { vm.edit(query = it) }, label = { Text("Search destination title") }, singleLine = true)
                    Row {
                        Box {
                            TextButton(onClick = { sourceMenu = true }) { Text(sourceNames[state.sourceKey].orEmpty()) }
                            DropdownMenu(sourceMenu, { sourceMenu = false }) {
                                sourceNames.forEach { (key, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { vm.edit(sourceKey = key); sourceMenu = false }) }
                            }
                        }
                        TextButton(enabled = !state.loading, onClick = { vm.search() }) { Text("Search") }
                    }
                }
                items(state.results, key = { it.id }) { candidate ->
                    ListItem(headlineContent = { Text(candidate.title) }, supportingContent = { Text(sourceName(candidate.id)) },
                        leadingContent = { MangaCover(candidate, null, Modifier.size(48.dp, 64.dp)) },
                        modifier = Modifier.clickable(enabled = !state.loading && candidate.id != source.id) { vm.select(candidate) })
                }
                if (state.hasMore) item { TextButton(enabled = !state.loading, onClick = { vm.search(true) }) { Text("More results") } }
            } else {
                item {
                    Text("To ${sourceName(state.selected!!.id)} · ${state.selected!!.title}", style = MaterialTheme.typography.titleMedium)
                    Text("${state.targets.size} readable releases. ${state.plan.count { it.target != null }} / ${state.plan.size} saved reading records mapped.", style = MaterialTheme.typography.bodySmall)
                    Text("Automatic suggestions require equal chapter, volume and language, with one destination release. Ambiguous or unnumbered records require your choice. Existing completed destination chapters are preserved.", style = MaterialTheme.typography.bodySmall)
                    ToolSwitch("Keep original in Library", keepOriginal) { keepOriginal = it }
                    ToolSwitch("Copy current page number (same edition)", copyPage) { copyPage = it }
                    Text("Page copying is off by default. Enable it only after confirming the same edition and page order. Scroll offsets are not copied; original progress stays intact.", style = MaterialTheme.typography.bodySmall)
                }
                items(state.plan, key = { it.position.releaseId }) { entry ->
                    ListItem(headlineContent = { Text(entry.source?.label ?: "Saved reading record") },
                        supportingContent = { Text("${if (entry.position.completed) "Completed" else "Page ${entry.position.page + 1}"} → ${entry.target?.let { "${it.label} · ${it.groupNames}" } ?: "Not mapped"}") },
                        modifier = Modifier.clickable { mappingId = entry.position.releaseId; chapterQuery = "" })
                }
                item {
                    Button(enabled = !state.loading, onClick = { vm.apply(keepOriginal, copyPage) { target -> dismiss(); reader.open(target) } }) { Text("Apply reviewed transfer") }
                    TextButton(enabled = !state.loading, onClick = { vm.edit() }) { Text("Choose another edition") }
                }
            }
            state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { TextButton(onClick = dismiss) { Text("Close") } }
        }
    }
    mappingId?.let { position ->
        AlertDialog(onDismissRequest = { mappingId = null }, title = { Text("Choose destination release") }, text = {
            Column {
                OutlinedTextField(chapterQuery, { chapterQuery = it }, label = { Text("Chapter or group") }, singleLine = true)
                TextButton(onClick = { vm.map(position, null); mappingId = null }) { Text("Leave unmapped") }
                LazyColumn(Modifier.heightIn(max = 350.dp)) {
                    items(state.targets.filter { it.label.contains(chapterQuery, true) || it.groupNames.contains(chapterQuery, true) }, key = { it.id }) { release ->
                        ListItem(headlineContent = { Text(release.label) }, supportingContent = { Text("${release.language.uppercase()} · ${release.groupNames}") },
                            modifier = Modifier.clickable { vm.map(position, release); mappingId = null })
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { mappingId = null }) { Text("Cancel") } })
    }
}
