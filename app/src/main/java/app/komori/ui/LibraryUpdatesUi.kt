package app.komori.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.komori.data.Settings

@Composable
internal fun LibraryCheckSettings(vm: ReaderViewModel, settings: Settings) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        vm.setting("notifyChapterUpdates", allowed)
        if (!allowed) vm.notice.value = "Notifications are disabled. Updates remain available in Library."
    }
    Text("Library updates", style = MaterialTheme.typography.titleMedium)
    Text(settings.libraryCheckStatus, style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0 to "Off", 24 to "Daily", 12 to "Every 12h").forEach { (hours, label) ->
            FilterChip(settings.libraryCheckHours == hours, { vm.numberSetting("libraryCheckHours", hours) }, { Text(label) })
        }
    }
    ToolSwitch("Checks on Wi-Fi only", settings.checkWifi) { vm.setting("checkWifi", it) }
    ToolSwitch("Check followed authors", settings.checkAuthors) { vm.setting("checkAuthors", it) }
    Text("Author checks rotate through up to three followed creators per cycle. The first check establishes a baseline; notifications require a later new work.", style = MaterialTheme.typography.bodySmall)
    ToolSwitch("Notify about new chapters and works", settings.notifyChapterUpdates) { enabled ->
        if (enabled && Build.VERSION.SDK_INT >= 33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        else vm.setting("notifyChapterUpdates", enabled)
    }
    Row {
        TextButton(onClick = vm::checkLibraryNow) { Text("Check now") }
        TextButton(onClick = { vm.currentTab.value = 0; vm.updatesVisible.value = true }) { Text("View Updates") }
    }
}

@Composable
internal fun LibraryUpdatesScreen(vm: ReaderViewModel, close: () -> Unit) {
    val updates by vm.libraryUpdates.collectAsStateWithLifecycle()
    val manga by vm.library.collectAsStateWithLifecycle()
    val books = remember(manga) { manga.associateBy { it.id } }
    val groups = remember(updates) { updates.groupBy { it.mangaId }.entries.sortedByDescending { entry -> entry.value.maxOf { it.detectedAt } } }
    BackHandler(onBack = close)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RoundControl(KomoriIcons.ArrowBack, "Close Updates", close)
            Text("Updates", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = vm::markAllUpdatesSeen) { Text("Mark seen") }
        }
        if (groups.isEmpty()) Text("No new chapters yet. Enable checks or run Check now in Settings. The first check establishes a baseline.", Modifier.padding(24.dp))
        LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(groups, key = { it.key }) { group ->
                var expanded by remember(group.key) { mutableStateOf(false) }
                OutlinedCard(Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text(books[group.key]?.title ?: "Unavailable title") },
                        supportingContent = { Text("${group.value.count { !it.seen }} unseen · ${group.value.size} updates") },
                        trailingContent = { TextButton(onClick = { vm.markUpdatesSeen(group.key) }) { Text("Seen") } })
                    (if (expanded) group.value else group.value.take(5)).forEach { update ->
                        ListItem(headlineContent = { Text(update.label) },
                            supportingContent = { Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(update.detectedAt))) },
                            modifier = Modifier.clickable { vm.openLibraryUpdate(update) })
                    }
                    if (group.value.size > 5) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Show all ${group.value.size}") }
                }
            }
        }
    }
}
