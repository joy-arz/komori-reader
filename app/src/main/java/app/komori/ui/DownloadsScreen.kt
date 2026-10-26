@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package app.komori.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.komori.BuildConfig
import app.komori.SourceVerificationActivity
import app.komori.data.*
import app.komori.domain.DownloadChoice
import app.komori.domain.DownloadPolicy
import coil.compose.AsyncImage
import org.json.JSONArray

@Composable
internal fun Downloads(vm: ReaderViewModel) {
    val transfers by vm.downloadTransfers.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val manga by vm.library.collectAsStateWithLifecycle()
    val downloadIds = downloads.map { it.releaseId }
    val selection = rememberMultiSelection(downloadIds)
    var deleting by remember { mutableStateOf<Download?>(null) }
    val active = downloads.count { it.state in DownloadPolicy.activeStates }
    val complete = downloads.count { it.state == "Complete" }
    val canResume =
        downloads.any {
            it.state in listOf("Paused", "Failed", "Verification required", "Needs repair")
        }
    val exportComic = rememberComicExporter(vm)
    var queueMenu by remember { mutableStateOf(false) }
    var storageDialog by remember { mutableStateOf(false) }
    if (storageDialog) DownloadMaintenanceDialog({ storageDialog = false })
    var groupByTitle by rememberSaveable { mutableStateOf(true) }
    val environment by remember(vm.app) { downloadEnvironment(vm.app) }.collectAsStateWithLifecycle(DownloadEnvironment())
    val mangaById = remember(manga) { manga.associateBy { it.id } }
    val ordered = downloads.sortedWith(compareBy<Download> { it.state == "Complete" }.thenBy { it.queuePosition }.thenBy { it.releaseId })
    val groups = if (groupByTitle) ordered.groupBy { it.mangaId } else linkedMapOf("" to ordered)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Downloads", style = MaterialTheme.typography.titleLarge)
                if (downloads.isNotEmpty())
                    Text(
                        "$active in queue · $complete ready offline",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            Box {
                    RoundControl(KomoriIcons.More, "Queue options", { queueMenu = true })
                    DropdownMenu(queueMenu, { queueMenu = false }) {
                        DropdownMenuItem(text = { Text("Storage & integrity") }, onClick = { queueMenu = false; storageDialog = true })
                        DropdownMenuItem(text = { Text(if (groupByTitle) "Show queue order" else "Group by title") },
                            onClick = { groupByTitle = !groupByTitle; queueMenu = false })
                        if (active > 0)
                            DropdownMenuItem(
                                text = { Text("Pause queue") },
                                onClick = {
                                    vm.pauseAll()
                                    queueMenu = false
                                },
                            )
                        if (canResume)
                            DropdownMenuItem(
                                text = { Text("Resume / retry all") },
                                onClick = {
                                    vm.resumeAll()
                                    queueMenu = false
                                },
                            )
                    }
                }
        }
        if (downloads.isEmpty())
            EmptyState(
                KomoriIcons.Download,
                "Take a few chapters with you",
                "Download from a title's chapter list. Finished downloads remain available offline.",
            )
        else
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (selection.active) stickyHeader {
                    SelectionBar(
                        selection,
                        downloadIds,
                        listOf(
                            BatchAction("Pause selected") { vm.batchDownloads(it, "Pause") },
                            BatchAction("Resume selected") { vm.batchDownloads(it, "Resume") },
                            BatchAction("Cancel selected") { vm.batchDownloads(it, "Cancel") },
                            BatchAction("Delete downloads", true) {
                                vm.batchDownloads(it, "Delete downloads")
                            },
                        ),
                    )
                }
                groups.forEach { (mangaId, chapters) ->
                if (groupByTitle) item(key = "title-$mangaId") {
                    Text(mangaById[mangaId]?.title ?: "Downloaded title", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).combinedClickable(
                            onLongClick = {
                                selection.active = true
                                selection.ids = (selection.ids + chapters.map { it.releaseId }).distinct()
                            },
                            onClick = {
                                if (selection.active) {
                                    val groupIds = chapters.map { it.releaseId }
                                    selection.ids = if (groupIds.all { it in selection.ids }) selection.ids - groupIds.toSet()
                                        else (selection.ids + groupIds).distinct()
                                }
                            }).padding(top = 8.dp))
                }
                items(
                    chapters,
                    key = { it.releaseId },
                ) { download ->
                    val displayState = downloadDisplayState(download, environment, settings.wifi)
                    var itemMenu by remember { mutableStateOf(false) }
                    OutlinedCard(
                        modifier =
                            Modifier.fillMaxWidth().combinedClickable(
                                onLongClick = { selection.toggle(download.releaseId) },
                                onClick = {
                                    if (selection.active) selection.toggle(download.releaseId)
                                },
                            ),
                        colors =
                            CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            if (selection.active)
                                Checkbox(
                                    download.releaseId in selection.ids,
                                    { selection.toggle(download.releaseId) },
                                    Modifier.semantics {
                                        contentDescription = "Select download ${download.label}"
                                    },
                                )
                            Text(
                                manga.find { it.id == download.mangaId }?.title ?: download.label,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${download.label} · $displayState",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            val transfer = transfers[download.releaseId]
                            val receiving = download.state == "Downloading" && transfer != null
                            if (download.state != "Complete") {
                                if (
                                    receiving && (transfer!!.resolving || transfer.totalBytes <= 0)
                                ) {
                                    LinearProgressIndicator(
                                        Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        progress = {
                                            val fraction =
                                                if (receiving && transfer!!.totalBytes > 0)
                                                    (transfer.bytes.toFloat() / transfer.totalBytes)
                                                        .coerceIn(0f, 1f)
                                                else 0f
                                            if (download.total == 0) 0f
                                            else
                                                (((if (receiving) transfer!!.completedPages
                                                    else download.done) + fraction) /
                                                        download.total)
                                                    .coerceIn(0f, 1f)
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    )
                                }
                            }
                            Text(
                                when {
                                    receiving && transfer!!.resolving -> "Finding chapter images…"
                                    receiving ->
                                        "Page ${transfer!!.page} · ${transfer.bytes / 1024} KB" +
                                            if (transfer.totalBytes > 0)
                                                " / ${transfer.totalBytes / 1024} KB"
                                            else " received"
                                    displayState.startsWith("Waiting") -> displayState
                                    download.state == "Queued" -> "Queued · position ${ordered.indexOfFirst { it.releaseId == download.releaseId } + 1}"
                                    download.state == "Retrying" ->
                                        "Retry scheduled · ${download.done} pages saved"
                                    else -> "${download.done} / ${download.total} pages"
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                            if (download.error.isNotEmpty())
                                Text(
                                    download.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            if (download.state == "Verification required")
                                VerifySourceButton(vm, sourceKey(download.mangaId))
                            Row {
                                if (
                                    download.state in
                                        listOf(
                                            "Paused",
                                            "Failed",
                                            "Verification required",
                                            "Needs repair",
                                            "Cancelled",
                                        )
                                )
                                    TextButton(onClick = { vm.resume(download) }) {
                                        Text(
                                            if (download.state == "Needs repair") "Repair"
                                            else "Resume"
                                        )
                                    }
                                else if (download.state != "Complete")
                                    TextButton(onClick = { vm.pause(download) }) { Text("Pause") }
                                if (download.done > 0)
                                    TextButton(onClick = { vm.readOffline(download) }) {
                                        Text(
                                            if (download.state == "Complete") "Read offline"
                                            else "Read saved pages"
                                        )
                                    }
                                Box {
                                    TextButton(onClick = { itemMenu = true }) { Text("More") }
                                    DropdownMenu(itemMenu, { itemMenu = false }) {
                                        if (download.state in DownloadPolicy.activeStates) {
                                            DropdownMenuItem(text = { Text("Download now") }, onClick = { vm.moveDownload(download); itemMenu = false })
                                            DropdownMenuItem(text = { Text("Move up") }, onClick = { vm.moveDownload(download, -1); itemMenu = false })
                                            DropdownMenuItem(text = { Text("Move down") }, onClick = { vm.moveDownload(download, 1); itemMenu = false })
                                            DropdownMenuItem(text = { Text("Cancel, keep pages") }, onClick = { vm.cancelDownload(download); itemMenu = false })
                                        }
                                        if (download.state == "Complete") DropdownMenuItem(text = { Text("Export CBZ") }, onClick = { exportComic(download); itemMenu = false })
                                        DropdownMenuItem(text = { Text("Delete downloads") }, onClick = { deleting = download; itemMenu = false })
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete downloaded chapter?") },
            text = { Text("Your reading progress and library entry will stay saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteDownload(item)
                        deleting = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

