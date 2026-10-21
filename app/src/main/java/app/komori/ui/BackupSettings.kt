package app.komori.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

@Composable
internal fun BackupSettings(vm: BackupViewModel = viewModel()) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            uri?.let(vm::export)
        }
    val restore =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(vm::inspect)
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Library backup", style = MaterialTheme.typography.titleLarge)
        Text(
            "Save a JSON backup of titles, likes, reading history, followed authors and title preferences.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Image files, download queues, source sessions and device settings are excluded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                enabled = !busy && preview == null,
                onClick = {
                    val stamp =
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                    export.launch("komori-backup-$stamp.json")
                },
            ) {
                Text("Export backup")
            }
            OutlinedButton(
                enabled = !busy && preview == null,
                onClick = { restore.launch(arrayOf("*/*")) },
            ) {
                Text("Restore backup")
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let {
            Text(
                it,
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    preview?.let { pending ->
        var useBackup by rememberSaveable(pending.reviewToken) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = vm::dismissPreview,
            title = { Text("Review Library restore") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Created ${DateFormat.getDateTimeInstance().format(Date(pending.backup.createdAt))} · Komori ${pending.backup.appVersion}"
                    )
                    Text(
                        "${pending.backup.manga.size} titles · ${pending.saved} saved · ${pending.liked} liked"
                    )
                    Text(
                        "${pending.backup.progress.size} reading positions · ${pending.backup.creators.size} followed authors · ${pending.backup.preferences.size} title preferences"
                    )
                    Text(
                        "Matches on this device: ${pending.matchingTitles} titles, ${pending.matchingShelf} shelf entries, ${pending.matchingProgress} reading positions, ${pending.matchingCreators} authors, ${pending.matchingPreferences} preferences."
                    )
                    Text(
                        "New entries are added. Existing values are kept unless you choose to use the backup. No titles or files are deleted."
                    )
                    if (pending.unavailableSources > 0)
                        Text(
                            "Some entries use unavailable sources. Their metadata can be restored, but reading still requires source support."
                        )
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = useBackup,
                                enabled = !busy,
                                role = Role.Checkbox,
                                onValueChange = { useBackup = it },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = useBackup, enabled = !busy, onCheckedChange = null)
                        Text(
                            "Use backup values for matching entries",
                            Modifier.weight(1f).padding(start = 8.dp),
                        )
                    }
                    message?.let { Text(it) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = { vm.restore(useBackup) }) {
                    Text("Restore Library")
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = vm::dismissPreview) { Text("Cancel") }
            },
        )
    }
}
