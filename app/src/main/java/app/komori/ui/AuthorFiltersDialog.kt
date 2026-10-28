package app.komori.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import app.komori.data.*

@Composable
internal fun AuthorFiltersDialog(current: AuthorWorkFilter, sources: List<String>, apply: (AuthorWorkFilter) -> Unit, close: () -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = close,
        properties = DialogProperties(decorFitsSystemWindows = false),
        modifier = Modifier.imePadding(),
        title = { Text("Filter author works") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(draft.query, { draft = draft.copy(query = it.take(256)) }, label = { Text("Title or creator") }, singleLine = true)
                Text("Sources", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.sources.isEmpty(), { draft = draft.copy(sources = emptySet()) }, { Text("All") })
                    sources.forEach { key -> FilterChip(key in draft.sources, {
                        draft = draft.copy(sources = if (key in draft.sources) draft.sources - key else draft.sources + key)
                    }, { Text(sourceNames[key] ?: key) }) }
                }
                Text("Language", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.language.isEmpty(), { draft = draft.copy(language = "") }, { Text("Any") })
                    listOf("en", "jp", "sp", "fr", "kr", "ch").forEach { code ->
                        FilterChip(draft.language == code, { draft = draft.copy(language = code) }, { Text(coverLanguage(code)?.name ?: code) })
                    }
                }
                OutlinedTextField(draft.category, { draft = draft.copy(category = it.take(128)) }, label = { Text("Category") }, placeholder = { Text("Manga, doujinshi…") }, singleLine = true)
                OutlinedTextField(draft.tags, { draft = draft.copy(tags = it.take(512)) }, label = { Text("Tags or parodies") }, placeholder = { Text("Separate required tags with commas") }, singleLine = true)
                Text("Library", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All works", "Saved", "Not saved", "Liked").forEach { value ->
                        FilterChip(draft.library == value, { draft = draft.copy(library = value) }, { Text(value) })
                    }
                }
                Text("Library status", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("", "Plan to read", "Reading", "On hold", "Completed", "Dropped").forEach { value ->
                        FilterChip(draft.status == value, { draft = draft.copy(status = value) }, { Text(value.ifEmpty { "Any" }) })
                    }
                }
                Text("Metadata filters apply as each creator page loads. Load more works to keep searching. Latest and popularity follow each source’s creator ranking.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { apply(draft) }) { Text("Apply") } },
        dismissButton = { FlowRow(horizontalArrangement = Arrangement.End) { TextButton(onClick = { draft = AuthorWorkFilter() }) { Text("Reset filters") }; TextButton(onClick = close) { Text("Cancel") } } },
    )
}
