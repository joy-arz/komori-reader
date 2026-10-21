package app.komori.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.komori.BuildConfig

@Composable
fun UpdateDialog(vm: UpdateViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun install() {
        val file = vm.state.value.file ?: return
        try {
            context.startActivity(vm.updater.installIntent(file))
        } catch (_: Exception) {
            vm.message(
                "Android could not open the installer. Retry or install the APK from GitHub."
            )
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (context.packageManager.canRequestPackageInstalls()) install()
            else vm.message("Allow Komori to install updates, then tap Install update again.")
        }
    if (state.show && state.update != null)
        AlertDialog(
            onDismissRequest = vm::snooze,
            title = { Text("Komori ${state.update!!.version}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.message)
                    if (state.update!!.releaseNotes.isNotBlank()) {
                        Text("Release notes", style = MaterialTheme.typography.titleSmall)
                        Text(state.update!!.releaseNotes, Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.downloading) {
                        LinearProgressIndicator(
                            progress = { state.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${state.percent}%", style = MaterialTheme.typography.labelMedium)
                    }
                    if (state.file == null && !state.downloading && !BuildConfig.DEBUG)
                        Text(
                            "Download size: ${"%.1f".format(state.update!!.size / 1_000_000.0)} MB. Your library and downloads stay on this device.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                }
            },
            confirmButton = {
                if (!state.downloading)
                    TextButton(
                        onClick = {
                            if (state.file == null) vm.download()
                            else if (context.packageManager.canRequestPackageInstalls()) install()
                            else
                                try {
                                    permission.launch(vm.updater.permissionIntent())
                                } catch (_: Exception) {
                                    vm.message(
                                        "Open Android Settings and allow Komori to install unknown apps."
                                    )
                                }
                        },
                        enabled = !BuildConfig.DEBUG,
                    ) {
                        Text(if (state.file == null) "Download update" else "Install update")
                    }
            },
            dismissButton = { TextButton(onClick = vm::snooze) { Text("Snooze 24h") } },
        )
}

@Composable
fun UpdateSettings(vm: UpdateViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Text("App updates", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("stable" to "Stable", "preview" to "Preview").forEach { (key, label) ->
            FilterChip(state.channel == key, { vm.channel(key) }, { Text(label) }, enabled = !state.checking && !state.downloading)
        }
    }
    Text("Preview includes prereleases. Automatic checks use HTTP cache validators and a six-hour cooldown.", style = MaterialTheme.typography.bodySmall)
    Text(state.message, style = MaterialTheme.typography.bodyMedium)
    Row {
        TextButton(onClick = { vm.check(true) }, enabled = !state.checking && !state.downloading) {
            Text("Check for updates")
        }
        if (state.update != null)
            TextButton(onClick = vm::show) {
                Text(if (state.file != null) "Install update" else "View update")
            }
    }
}

@Composable
fun UpdateBanner(vm: UpdateViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.update != null && !state.show && state.snoozedUntil <= System.currentTimeMillis()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    if (state.downloading) "Updating · ${state.percent}%"
                    else "Komori ${state.update!!.version} available",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                )
                TextButton(onClick = vm::show) {
                    Text(if (state.file != null) "Install" else "Review")
                }
            }
        }
    }
}
