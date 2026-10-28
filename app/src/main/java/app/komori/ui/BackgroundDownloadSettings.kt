package app.komori.ui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal data class BackgroundDownloadStatus(val optimized: Boolean, val restricted: Boolean) {
    companion object {
        fun read(context: Context) = BackgroundDownloadStatus(
            !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
            android.os.Build.VERSION.SDK_INT >= 28 && context.getSystemService(ActivityManager::class.java).isBackgroundRestricted,
        )
    }
}

@SuppressLint("BatteryLife")
internal fun backgroundDownloadIntent(context: Context, status: BackgroundDownloadStatus): Intent =
    if (status.optimized && !status.restricted)
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

@Composable
internal fun BackgroundDownloadSettings() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var status by remember(context) { mutableStateOf(BackgroundDownloadStatus.read(context)) }
    var unavailable by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        status = BackgroundDownloadStatus.read(context)
    }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = BackgroundDownloadStatus.read(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BackgroundDownloadContent(status, unavailable) {
        unavailable = false
        try {
            launcher.launch(backgroundDownloadIntent(context, status))
        } catch (_: ActivityNotFoundException) {
            try {
                launcher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            } catch (_: ActivityNotFoundException) { unavailable = true }
        }
    }
}

@Composable
internal fun BackgroundDownloadContent(status: BackgroundDownloadStatus, unavailable: Boolean = false, open: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Background downloads", style = MaterialTheme.typography.titleSmall)
        Text(when {
            status.restricted -> "Android restricts background activity. Open app settings and allow background battery and data use."
            status.optimized -> "Battery optimization can pause downloads while the screen is off. Allow background downloads to keep transfers running."
            else -> "Battery optimization is disabled. Downloads can run while you read, switch apps or lock the screen."
        }, style = MaterialTheme.typography.bodyMedium)
        Text("Keep the download notification enabled. A powered-off phone or a force-stopped app cannot download; device battery restrictions can still interrupt transfers.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = open) {
            Text(if (status.optimized && !status.restricted) "Allow background downloads" else "Open app battery settings")
        }
        if (unavailable) Text("Open Android Settings → Apps → Komori → Battery to allow background activity.")
    }
}
