package app.komori.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.komori.KomoriApp
import app.komori.data.*
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class DownloadMaintenanceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DownloadMaintenance(application as KomoriApp)
    val storage = MutableStateFlow<DownloadStorage?>(null)
    val busy = MutableStateFlow(false)
    val status = MutableStateFlow("")
    val preview = MutableStateFlow<ReadDownloadPreview?>(null)
    private var job: Job? = null
    init { refresh() }
    fun refresh() = operation { storage.value = repository.storage() }
    fun scan() = operation {
        status.value = "Preparing integrity scan…"
        val report = repository.scan { status.value = it }
        status.value = "${report.checked} chapters checked · ${report.damaged} damaged/missing pages · ${report.skipped} active or unavailable chapters skipped. ${report.baselined} older pages received a checksum baseline."
        storage.value = repository.storage()
    }
    fun previewRead() = operation { preview.value = repository.previewRead() }
    fun dismiss() { if (!busy.value) preview.value = null }
    fun deleteRead() {
        val pending = preview.value ?: return
        operation {
            val count = repository.deleteRead(pending)
            preview.value = null
            status.value = "$count chapters deleted. ${pending.chapters.size - count} changed chapters skipped. Reading history and Library entries were kept."
            storage.value = repository.storage()
        }
    }
    fun cancel() { job?.cancel() }
    private fun operation(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        job = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { status.value = "Operation stopped. Completed changes were retained."; throw e }
            catch (e: Exception) { status.value = e.message ?: "Storage operation failed. Check free space and retry." }
            finally { busy.value = false }
        }
    }
}

internal fun storageLabel(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
    else -> "${(bytes + 1023) / 1024} KB"
}

@Composable
internal fun DownloadMaintenanceDialog(close: () -> Unit, vm: DownloadMaintenanceViewModel = viewModel()) {
    val storage by vm.storage.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Download storage") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(storage?.let { "${storageLabel(it.bytes)} in ${it.chapters} chapters" } ?: "Measuring downloaded files…")
                Text("Scan checks image decoding and saved SHA-256 checksums. Older pages without checksums receive a baseline after decoding; their original bytes cannot be independently confirmed.", style = MaterialTheme.typography.bodySmall)
                Text("Active downloads are skipped. Damaged pages are marked Needs repair; intact pages stay available.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = !busy, onClick = vm::scan) { Text("Scan downloaded files") }
                OutlinedButton(enabled = !busy, onClick = vm::previewRead) { Text("Review delete-read chapters") }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = vm::cancel) { Text("Stop") }
                }
                if (status.isNotEmpty()) Text(status, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Close") } },
    )
    preview?.let { pending ->
        AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Delete read downloads?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${pending.chapters.size} chapters · approximately ${storageLabel(pending.bytes)}")
                    Text("Only these downloaded copies will be removed. Library entries and reading history stay. Active or changed chapters are skipped.")
                    if (pending.chapters.any { sourceKey(it.mangaId) == "local" }) Text("This includes imported local copies. Keep the original CBZ or folder before deleting them.")
                    pending.chapters.take(100).forEach { Text("${pending.titles[it.mangaId]} · ${it.label} · ${sourceName(it.mangaId)}", style = MaterialTheme.typography.bodySmall) }
                    if (pending.chapters.size > 100) Text("And ${pending.chapters.size - 100} more chapters.")
                    if (pending.chapters.isEmpty()) Text("No read chapters are eligible for deletion.")
                }
            },
            confirmButton = { TextButton(enabled = !busy && pending.chapters.isNotEmpty(), onClick = vm::deleteRead) { Text("Delete downloaded copies") } },
            dismissButton = { TextButton(enabled = !busy, onClick = vm::dismiss) { Text("Cancel") } },
        )
    }
}
