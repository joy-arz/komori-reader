@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.komori.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.komori.data.Settings
import app.komori.domain.Release
import kotlin.math.roundToInt

@Composable
internal fun ReaderTools(vm: ReaderViewModel, settings: Settings) {
    Text("Display and navigation", style = MaterialTheme.typography.titleMedium)
    ToolSwitch("Use volume keys to turn pages", settings.volumeKeys) { vm.setting("volumeKeys", it) }
    ToolSwitch("Warn before chapter gaps", settings.gapWarnings) { vm.setting("gapWarnings", it) }
    ToolSwitch("Detect tall pages as webtoons", settings.detectWebtoon) { vm.setting("detectWebtoon", it) }
    Text("Brightness: ${if (settings.brightness < 0) "System" else "${settings.brightness}%"}")
    SettingSlider(settings.brightness, -1..100, "Reader brightness") { vm.numberSetting("brightness", it) }
    Text("Dim artwork: ${settings.dim}%")
    SettingSlider(settings.dim, 0..80, "Dim artwork") { vm.numberSetting("dim", it) }
    Text("Orientation")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("auto", "portrait", "landscape", "locked").forEach { value ->
            FilterChip(settings.orientation == value, { vm.choiceSetting("orientation", value) },
                { Text(value.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) })
        }
    }
    ReaderCacheSettings(vm, settings)
}

@Composable
internal fun ReaderCacheSettings(vm: ReaderViewModel, settings: Settings) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Preload pages: ${settings.preloadPages}")
        SettingSlider(settings.preloadPages, 0..10, "Pages to preload", steps = 9) { vm.numberSetting("preloadPages", it) }
        Text("Reader disk cache: ${settings.readerCacheMb} MB")
        SettingSlider(settings.readerCacheMb, 32..256, "Reader disk cache in megabytes", steps = 6) { vm.numberSetting("readerCacheMb", it) }
        val reader by vm.reader.collectAsStateWithLifecycle()
        val clearing by vm.clearingCache.collectAsStateWithLifecycle()
        TextButton(enabled = reader == null && !clearing, onClick = vm::clearReaderCache) { Text(if (clearing) "Clearing cached pages…" else "Clear cached pages") }
        Text(if (reader != null) "Close the reader to clear its temporary cache." else "Downloaded chapters and imported comics are kept.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun ToolSwitch(label: String, selected: Boolean, choose: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(selected, choose, Modifier.semantics { contentDescription = label })
    }
}

@Composable
internal fun SettingSlider(value: Int, range: IntRange, label: String, steps: Int = 0, choose: (Int) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Slider(draft, { draft = it }, modifier = Modifier.semantics { contentDescription = label }, valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = steps, onValueChangeFinished = { choose(draft.roundToInt()) })
}

@Composable
internal fun ReaderJumpDialog(vm: ReaderViewModel, current: Release, page: Int, count: Int, dismiss: () -> Unit) {
    var target by remember { mutableStateOf(current) }
    var pageText by remember { mutableStateOf((page + 1).toString()) }
    val targetCount = if (target.id == current.id) count else target.pages
    fun validRequestedPage(): Int? {
        val pages = if (target.id == current.id) count else target.pages
        return pageText.toIntOrNull()?.takeIf { it >= 1 && (pages <= 0 || it <= pages) }
    }
    val validPage = validRequestedPage()
    val chapters = vm.selectedChapters().filter { it.readable }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Go to chapter / page") },
        text = {
            Column {
                OutlinedTextField(pageText, { pageText = it.filter(Char::isDigit).take(5) },
                    label = { Text(if (targetCount > 0) "Page (1–$targetCount)" else "Page") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, isError = validPage == null)
                if (targetCount > 1) {
                    var seek by remember(target.id, targetCount, pageText) {
                        mutableFloatStateOf((validPage ?: 1).toFloat())
                    }
                    Slider(seek, { seek = it }, modifier = Modifier.semantics { contentDescription = "Destination page" }, valueRange = 1f..targetCount.toFloat(),
                        onValueChangeFinished = { pageText = seek.roundToInt().toString() })
                }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(chapters, key = { it.id }) { release ->
                        ListItem(
                            headlineContent = { Text(release.label) },
                            supportingContent = { Text(release.groupNames) },
                            leadingContent = { RadioButton(target.id == release.id, null) },
                            modifier = Modifier.clickable { target = release; pageText = "1" },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = validPage != null, onClick = {
            validRequestedPage()?.let { page -> vm.jumpTo(target, page - 1); dismiss() }
        }) { Text("Go") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}


@Composable
internal fun ReaderWindowEffects(enabled: Boolean, settings: Settings) {
    val activity = androidx.compose.ui.platform.LocalView.current.context as? android.app.Activity
    if (enabled && activity != null) {
        DisposableEffect(activity) {
            onDispose {
                activity.window.attributes = activity.window.attributes.apply { screenBrightness = -1f }
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        SideEffect {
            activity.window.attributes = activity.window.attributes.apply {
                screenBrightness = if (settings.brightness < 0) -1f else (settings.brightness / 100f).coerceAtLeast(.01f)
            }
            val orientation = when (settings.orientation) {
                "portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                "landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                "locked" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
                else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            if (activity.requestedOrientation != orientation) activity.requestedOrientation = orientation
        }
    }
}

@Composable
internal fun ReaderVolumeKeys(vm: ReaderViewModel, enabled: Boolean) {
    val activity = androidx.compose.ui.platform.LocalView.current.context as? app.komori.MainActivity
    DisposableEffect(activity, enabled, vm) {
        val handler: (Int) -> Unit = vm::navigateReader
        if (enabled) activity?.volumeNavigation = handler
        onDispose { if (activity?.volumeNavigation === handler) activity?.volumeNavigation = null }
    }
}
