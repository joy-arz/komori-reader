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
internal fun Toggle(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = change)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked, null)
    }
}

@Composable
internal fun SettingsScreen(vm: ReaderViewModel, settings: Settings) {
    var section by rememberSaveable { mutableStateOf("Reading") }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Reading", "Sources", "App", "Support").forEach { label ->
                FilterChip(
                    section == label,
                    { section = label },
                    label = { Text(label) },
                    shape = RoundedCornerShape(50),
                )
            }
        }
        key(section) {
            if (section == "Support") SupportPage()
            else LazyColumn(
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (section == "Sources") {
                    item {
                        Text("Sources", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Top source opens first in Discover. MangaDex library entries stay available.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        sourceOrder(settings.sources).forEachIndexed { index, key ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .padding(start = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${index + 1}. ${sourceNames.getValue(key)}${if(key == enabledSourceOrder(settings).firstOrNull()) " · Default" else ""}",
                                    Modifier.weight(1f),
                                )
                                Switch(key in settings.enabledSources.split(','), { vm.enableSource(key, it) },
                                    Modifier.semantics { contentDescription = "Enable ${sourceNames[key]}" })
                                TextButton(
                                    enabled = index > 0,
                                    onClick = { vm.moveSource(key, -1) },
                                ) {
                                    Text("Up")
                                }
                                TextButton(
                                    enabled = index < sourceNames.size - 1,
                                    onClick = { vm.moveSource(key, 1) },
                                ) {
                                    Text("Down")
                                }
                            }
                        }
                    }
                    item {
                        Toggle(
                            "Continue reading banner",
                            "Show the most recently read title in Library",
                            settings.showContinue,
                        ) {
                            vm.setting("showContinue", it)
                        }
                    }
                    item { SourceDiagnostics(vm) }
                    item {
                        TextButton(onClick = { vm.resetSourceOrder() }) {
                            Text("Reset source order")
                        }
                    }
                }
                if (section == "Reading") {
                    item { Text("Reading", style = MaterialTheme.typography.titleLarge) }
                    item { ReaderCacheSettings(vm, settings) }
                    item {
                        Toggle(
                            "Continuous chapters",
                            "Scroll into the next webtoon chapter automatically",
                            settings.continuousChapters,
                        ) {
                            vm.setting("continuousChapters", it)
                        }
                    }
                    item {
                        Text(
                            "Retry failed reader images",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "Retry the failed image up to three times while it is visible.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0, 3, 5, 10, 30).forEach { seconds ->
                                FilterChip(
                                    settings.retrySeconds == seconds,
                                    { vm.retryInterval(seconds) },
                                    label = { Text(if (seconds == 0) "Off" else "${seconds}s") },
                                )
                            }
                        }
                    }
                    item {
                        Toggle(
                            "Default to webtoon scrolling",
                            "Titles without a reader override use continuous scrolling",
                            settings.webtoon,
                        ) {
                            vm.setting("webtoon", it)
                        }
                    }
                    item {
                        Toggle(
                            "Default to right-to-left paging",
                            "Used when webtoon is off and a title has no override",
                            settings.rtl,
                        ) {
                            vm.setting("rtl", it)
                        }
                    }
                    item {
                        Toggle(
                            "Keep screen awake while reading",
                            "Only active inside the reader",
                            settings.keepScreenOn,
                        ) {
                            vm.setting("keepScreenOn", it)
                        }
                    }
                    item {
                        Toggle(
                            "Resume your page",
                            "Off starts the current chapter from page one",
                            settings.resume,
                        ) {
                            vm.setting("resume", it)
                        }
                    }
                    item {
                        Toggle(
                            "Open library titles in reader",
                            "Tap a cover to read immediately",
                            settings.direct,
                        ) {
                            vm.setting("direct", it)
                        }
                    }
                    item {
                        Toggle(
                            "Smart Chapters",
                            "Collapse matching numbered releases",
                            settings.smart,
                        ) {
                            vm.setting("smart", it)
                        }
                    }
                    item {
                        Text("Chapter language", style = MaterialTheme.typography.titleSmall)
                        var languageDialog by remember { mutableStateOf(false) }
                        TextButton(onClick = { languageDialog = true }) {
                            Text(languageName(settings.language))
                        }
                        if (languageDialog)
                            LanguagePicker(
                                settings.language,
                                emptyList(),
                                { languageDialog = false },
                            ) {
                                vm.language(it)
                                languageDialog = false
                            }
                    }
                    item {
                        Toggle(
                            "Alphabetical library",
                            "Off sorts your shelf by last activity",
                            settings.libraryAlphabetical,
                        ) {
                            vm.setting("libraryAlphabetical", it)
                        }
                    }
                }
                if (section == "App") {
                    item { BackupSettings() }
                    item {
                        HorizontalDivider()
                        Text(
                            "Downloads & appearance",
                            Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    item {
                        Toggle(
                            "Data-saving images",
                            "Use smaller images for new reads and downloads",
                            settings.saver,
                        ) {
                            vm.setting("saver", it)
                        }
                    }
                    item { BackgroundDownloadSettings() }
                    item {
                        Toggle(
                            "Wi-Fi-only downloads",
                            "Applies to newly queued or resumed downloads",
                            settings.wifi,
                        ) {
                            vm.setting("wifi", it)
                        }
                    }
                    item {
                        Toggle(
                            "Compact layout",
                            "Smaller covers fit more titles on screen",
                            settings.compact,
                        ) { vm.setting("compact", it) }
                        Text("Appearance", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("system", "dark", "light").forEach { choice ->
                                FilterChip((if (settings.theme == "legacy") if (settings.dark) "dark" else "light" else settings.theme) == choice,
                                    { vm.choiceSetting("theme", choice) }, { Text(choice.replaceFirstChar { it.uppercase() }) })
                            }
                        }
                    }
                    item {
                        HorizontalDivider()
                        LocalFilesSettings(vm, settings)
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        LibraryCheckSettings(vm, settings)
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        UpdateSettings()
                        Toggle(
                            "Download updates on Wi-Fi",
                            "Automatically download verified updates on unmetered connections. Android confirms installation.",
                            settings.autoUpdateDownload,
                        ) {
                            vm.setting("autoUpdateDownload", it)
                        }
                        Text(
                            "Komori ${BuildConfig.VERSION_NAME}",
                            Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "An independent, ad-free reader. Manga and chapter metadata are provided by the selected source. Translations belong to their credited scanlation groups. Library, likes, creator follows and reading progress are stored on this device.",
                            Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

