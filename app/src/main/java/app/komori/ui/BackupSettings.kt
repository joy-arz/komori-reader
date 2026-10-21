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
    val settings by vm.settings.collectAsStateWithLifecycle(app.komori.data.Settings())
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { vm.configure(folder = it) } }
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
        Text("Automatic backups", style = MaterialTheme.typography.titleMedium)
        Text("Choose a folder outside Komori to keep metadata backups after uninstalling. Android runs scheduled work when battery and storage allow. Cloud folders also need provider connectivity.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled = !busy, onClick = { folder.launch(settings.backupFolder.takeIf { it.isNotEmpty() }?.let(android.net.Uri::parse)) }) {
            Text(if (settings.backupFolder.isEmpty()) "Choose backup folder" else "Change backup folder")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Off", 1 to "Daily", 7 to "Weekly").forEach { (days, label) ->
                FilterChip(settings.backupDays == days, { vm.configure(days = days) }, { Text(label) }, enabled = !busy && (days == 0 || settings.backupFolder.isNotEmpty()))
            }
        }
        Text("Keep recent backups", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3, 7, 14).forEach { count -> FilterChip(settings.backupRetention == count, { vm.configure(retention = count) }, { Text("$count") }, enabled = !busy) }
        }
        Text("Only Komori automatic backup files are pruned, after a new backup passes read-back verification. Manual exports remain untouched.", style = MaterialTheme.typography.bodySmall)
        Text(settings.backupStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (settings.backupLastSuccess > 0) Text("Last verified: ${DateFormat.getDateTimeInstance().format(Date(settings.backupLastSuccess))}", style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = !busy && settings.backupFolder.isNotEmpty(), onClick = vm::backupNow) { Text("Back up now") }
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
                        "${pending.backup.progress.size} reading positions · ${pending.backup.creators.size} followed authors · ${pending.backup.preferences.size} title preferences · ${pending.backup.categories.size} categories · ${pending.backup.links.size} migration links"
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
