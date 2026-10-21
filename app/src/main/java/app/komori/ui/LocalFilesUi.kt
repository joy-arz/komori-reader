package app.komori.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.komori.data.Download
import app.komori.data.LocalFiles
import app.komori.data.Settings

@Composable
internal fun LocalFilesSettings(vm: ReaderViewModel, settings: Settings) {
    val context = LocalContext.current
    val status by vm.localFileStatus.collectAsStateWithLifecycle()
    val archive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importComic(uri, false) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) vm.importComic(uri, true) }
    val exportFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                vm.exportFolder(uri)
            } catch (_: SecurityException) { vm.notice.value = "This provider did not grant lasting folder access. Choose another folder." }
        }
    }
    Text("Local files", style = MaterialTheme.typography.titleMedium)
    Text("Import a CBZ or an image folder, including subfolders. JPEG, PNG and WebP; at most 10,000 pages and 512 MB expanded. Files are copied into Komori; originals stay unchanged.", style = MaterialTheme.typography.bodySmall)
    Row {
        TextButton(enabled = status == null, onClick = { archive.launch(arrayOf("application/zip", "application/vnd.comicbook+zip", "application/x-cbz", "application/octet-stream")) }) { Text("Import CBZ") }
        TextButton(enabled = status == null, onClick = { folder.launch(null) }) { Text("Import folder") }
    }
    Text(if (settings.exportFolder.isEmpty()) "CBZ exports ask for a destination each time." else "CBZ exports use the selected document-provider folder.", style = MaterialTheme.typography.bodySmall)
    Row {
        TextButton(onClick = { exportFolder.launch(settings.exportFolder.takeIf { it.isNotEmpty() }?.let(Uri::parse)) }) { Text("Choose export folder") }
        if (settings.exportFolder.isNotEmpty()) TextButton(onClick = { vm.exportFolder(null) }) { Text("Reset") }
    }
    if (status != null) {
        Text(status!!, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = vm::cancelFileOperation) { Text("Stop file operation") }
    }
    Text("Local image files are not included in metadata backups. Export CBZ from a completed download's More menu to keep a portable copy.", style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun rememberComicExporter(vm: ReaderViewModel): (Download) -> Unit {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<Download?>(null) }
    val document = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.comicbook+zip")) { uri ->
        pending?.let { if (uri != null) vm.exportComic(it, uri) }
        pending = null
    }
    return { download ->
        if (settings.exportFolder.isNotEmpty()) vm.exportComic(download)
        else { pending = download; document.launch(LocalFiles.exportName(download)) }
    }
}
