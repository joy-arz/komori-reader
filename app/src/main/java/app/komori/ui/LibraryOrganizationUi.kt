package app.komori.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.komori.data.LibraryCategory
import app.komori.data.Manga

@Composable
internal fun CategoryManagerDialog(vm: ReaderViewModel, dismiss: () -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val preferences by vm.settings.collectAsStateWithLifecycle()
    var startMenu by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<LibraryCategory?>(null) }
    var deleting by remember { mutableStateOf<LibraryCategory?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Your libraries") }, text = {
        Column {
            Box {
                TextButton(onClick = { startMenu = true }) {
                    Text("On open: " + if (preferences.libraryStart == "last") "Last viewed" else
                        categories.find { "library:${it.id}" == preferences.libraryStart }?.name ?: "All")
                }
                DropdownMenu(startMenu, { startMenu = false }) {
                    DropdownMenuItem(text = { Text("Remember last viewed") }, onClick = { vm.libraryView("last", true); startMenu = false })
                    DropdownMenuItem(text = { Text("All") }, onClick = { vm.libraryView("library:", true); startMenu = false })
                    categories.forEach { library ->
                        DropdownMenuItem(text = { Text(library.name) }, onClick = { vm.libraryView("library:${library.id}", true); startMenu = false })
                    }
                }
            }
            Text("Hold a handle and drag to reorder.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it.take(100) }, label = { Text(if (editing == null) "New library" else "Rename library") }, singleLine = true)
            Row {
                TextButton(enabled = name.isNotBlank(), onClick = { vm.saveCategory(name, editing?.id); name = ""; editing = null }) { Text(if (editing == null) "Add" else "Save name") }
                if (editing != null) TextButton(onClick = { name = ""; editing = null }) { Text("Cancel edit") }
            }
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                itemsIndexed(categories, key = { _, it -> it.id }) { index, category ->
                    var rowHeight by remember { mutableIntStateOf(80) }
                    ListItem(leadingContent = { ReorderHandle(category.name, rowHeight, index > 0, index < categories.lastIndex) { vm.moveCategory(category.id, it) } }, headlineContent = { Text(category.name) }, modifier = Modifier.onSizeChanged { rowHeight = it.height }.clickable { editing = category; name = category.name },
                        supportingContent = { FlowRow {
                            TextButton(onClick = { deleting = category }) { Text("Delete") }
                        } })
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } })
    deleting?.let { category ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${category.name}?") },
            text = { Text("Titles, downloads and reading progress are kept.") },
            confirmButton = { TextButton(onClick = { vm.deleteCategory(category.id); deleting = null }) { Text("Delete library") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

@Composable
internal fun CategoryAssignmentDialog(vm: ReaderViewModel, ids: List<String>, titles: List<Manga> = emptyList(), dismiss: () -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val memberships by vm.memberships.collectAsStateWithLifecycle()
    val changes = remember { mutableStateMapOf<String, Boolean>() }
    var manage by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Libraries · ${ids.size} ${if (ids.size == 1) "title" else "titles"}") }, text = {
        Column {
            if (categories.isEmpty()) Text("Create a library to organize your titles.")
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(categories, key = { it.id }) { category ->
                    val count = memberships.count { it.mangaId in ids && it.categoryId == category.id }
                    val checked = changes[category.id]
                    val state = if (checked == true || checked == null && count == ids.size) ToggleableState.On
                        else if (checked == false || count == 0) ToggleableState.Off else ToggleableState.Indeterminate
                    ListItem(headlineContent = { Text(category.name) }, leadingContent = {
                        TriStateCheckbox(state, { changes[category.id] = state != ToggleableState.On })
                    }, modifier = Modifier.clickable { changes[category.id] = state != ToggleableState.On })
                }
            }
            TextButton(onClick = { manage = true }) { Text("Manage libraries") }
        }
    }, confirmButton = { TextButton(onClick = { vm.changeCategories(ids, changes.toMap(), titles); dismiss() }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
    if (manage) CategoryManagerDialog(vm) { manage = false }
}

@Composable
internal fun LibraryFilterDialog(
    vm: ReaderViewModel,
    filters: LibraryFilters,
    collection: String,
    customLibraries: Boolean,
    apply: (LibraryFilters, String) -> Unit,
    dismiss: () -> Unit,
) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val titles by vm.library.collectAsStateWithLifecycle()
    val allowedCollections =
        (if (customLibraries) listOf("All titles") else emptyList()) + listOf("Saved", "Liked", "History")
    var collectionChoice by remember {
        mutableStateOf(
            collection.takeIf { it in allowedCollections }
                ?: if (customLibraries) "All titles" else "Saved",
        )
    }
    var filtersChoice by remember(filters) { mutableStateOf(filters) }
    var manage by remember { mutableStateOf(false) }
    val availableSources =
        remember(titles) {
            titles.map { app.komori.data.sourceKey(it.id) }
                .distinct()
                .sortedBy { app.komori.data.sourceName(it) }
        }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Filter Library") }, text = {
        LazyColumn(Modifier.heightIn(max = 460.dp)) {
            item { Text("Download status", style = MaterialTheme.typography.titleSmall) }
            items(
                listOf(
                    LibraryDownloadFilter.ALL to "All download states",
                    LibraryDownloadFilter.DOWNLOADED to "Downloaded",
                    LibraryDownloadFilter.NOT_DOWNLOADED to "Not downloaded",
                )
            ) { (key, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { filtersChoice = filtersChoice.copy(download = key) },
                ) {
                    RadioButton(filtersChoice.download == key, { filtersChoice = filtersChoice.copy(download = key) })
                    Text(label, Modifier.padding(top = 12.dp))
                }
            }
            item { ToolSwitch("Only favorites (Liked)", filtersChoice.favoritesOnly) { filtersChoice = filtersChoice.copy(favoritesOnly = it) } }
            item { ToolSwitch("Only titles with unread chapters", filtersChoice.unreadOnly) { filtersChoice = filtersChoice.copy(unreadOnly = it) } }
            item { Text("Collection", style = MaterialTheme.typography.titleSmall) }
            items(allowedCollections) { choice ->
                Row(Modifier.fillMaxWidth().clickable { collectionChoice = choice }) {
                    RadioButton(collectionChoice == choice, { collectionChoice = choice }); Text(choice, Modifier.padding(top = 12.dp))
                }
            }
            item { Text("Reading status", style = MaterialTheme.typography.titleSmall) }
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { filtersChoice = filtersChoice.copy(statuses = emptySet()) },
                ) {
                    Checkbox(filtersChoice.statuses.isEmpty(), {
                        if (it) filtersChoice = filtersChoice.copy(statuses = emptySet())
                    })
                    Text("All statuses", Modifier.padding(top = 12.dp))
                }
            }
            items(listOf("Plan to read", "Reading", "On hold", "Completed", "Dropped")) { status ->
                val selected = status in filtersChoice.statuses
                Row(
                    Modifier.fillMaxWidth().clickable {
                        filtersChoice = filtersChoice.copy(
                            statuses = if (selected) filtersChoice.statuses - status else filtersChoice.statuses + status,
                        )
                    },
                ) {
                    Checkbox(selected, {
                        filtersChoice = filtersChoice.copy(
                            statuses = if (selected) filtersChoice.statuses - status else filtersChoice.statuses + status,
                        )
                    })
                    Text(status, Modifier.padding(top = 12.dp))
                }
            }
            item { Text("Library", style = MaterialTheme.typography.titleSmall) }
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { filtersChoice = filtersChoice.copy(categories = emptySet()) },
                ) {
                    Checkbox(filtersChoice.categories.isEmpty(), { if (it) filtersChoice = filtersChoice.copy(categories = emptySet()) })
                    Text("All libraries", Modifier.padding(top = 12.dp))
                }
            }
            items(listOf(UNCATEGORIZED_LIBRARY to "Uncategorized") + categories.map { it.id to it.name }) { (key, label) ->
                val selected = key in filtersChoice.categories
                Row(
                    Modifier.fillMaxWidth().clickable {
                        filtersChoice = filtersChoice.copy(
                            categories = if (selected) filtersChoice.categories - key else filtersChoice.categories + key,
                        )
                    },
                ) {
                    Checkbox(selected, {
                        filtersChoice = filtersChoice.copy(
                            categories = if (selected) filtersChoice.categories - key else filtersChoice.categories + key,
                        )
                    })
                    Text(label, Modifier.padding(top = 12.dp))
                }
            }
            item { TextButton(onClick = { manage = true }) { Text("Manage libraries") } }
            if (availableSources.isNotEmpty()) {
                item { Text("Sources", style = MaterialTheme.typography.titleSmall) }
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { filtersChoice = filtersChoice.copy(sources = emptySet()) },
                    ) {
                        Checkbox(filtersChoice.sources.isEmpty(), { if (it) filtersChoice = filtersChoice.copy(sources = emptySet()) })
                        Text("All sources", Modifier.padding(top = 12.dp))
                    }
                }
                items(availableSources) { key ->
                    val selected = key in filtersChoice.sources
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            filtersChoice = filtersChoice.copy(
                                sources = if (selected) filtersChoice.sources - key else filtersChoice.sources + key,
                            )
                        },
                    ) {
                        Checkbox(selected, {
                            filtersChoice = filtersChoice.copy(
                                sources = if (selected) filtersChoice.sources - key else filtersChoice.sources + key,
                            )
                        })
                        Text(app.komori.data.sourceName(key), Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = { apply(filtersChoice, collectionChoice); dismiss() }) { Text("Apply") }
    }, dismissButton = {
        Row {
            TextButton(onClick = {
                apply(LibraryFilters(), if (customLibraries) "All titles" else "Saved")
                dismiss()
            }) { Text("Reset") }
            TextButton(onClick = dismiss) { Text("Cancel") }
        }
    })
    if (manage) CategoryManagerDialog(vm) { manage = false }
}
