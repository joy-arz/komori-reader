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
internal fun Details(
    vm: ReaderViewModel,
    manga: Manga,
    browseTag: (SourceTag) -> Unit,
    browseCreator: (Creator) -> Unit,
) {
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    val followed by vm.creators.collectAsStateWithLifecycle()
    val raw by vm.chapters.collectAsStateWithLifecycle()
    val chapterStatus by vm.chapterStatus.collectAsStateWithLifecycle()
    val sourceLanguages by vm.sourceLanguages.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val duplicates = duplicateTitles(manga, library, shelf)
    var confirmDuplicate by remember(manga.id) { mutableStateOf(false) }
    if (confirmDuplicate)
        AlertDialog(
            onDismissRequest = { confirmDuplicate = false },
            title = { Text("Already in your library?") },
            text = {
                Text(
                    "A matching title is saved from ${duplicates.joinToString { sourceName(it.id) }}. Keep both if these are different editions or translations."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.toggle(manga)
                        confirmDuplicate = false
                    }
                ) {
                    Text("Add anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDuplicate = false }) { Text("Cancel") }
            },
        )
    val entry = shelf.find { it.mangaId == manga.id }
    val pref = preferences.find { it.mangaId == manga.id }
    val selected = remember(raw, preferences, settings, shelf) { vm.selectedChapters() }
    var showBulkDownloads by remember { mutableStateOf(false) }
    val bulkStatus by vm.bulkStatus.collectAsStateWithLifecycle()
    if (showBulkDownloads) BulkDownloadDialog(vm) { showBulkDownloads = false }
    var showLanguages by remember { mutableStateOf(false) }
    var chapterFilter by remember(manga.id) { mutableStateOf("All") }
    val visibleChapters =
        (if (chapterFilter == "Downloaded")
                app.komori.domain.ChapterPolicy.readingOrder(
                    raw.filter { chapter ->
                        downloads.any { it.releaseId == chapter.id && it.state == "Complete" }
                    }
                )
            else selected)
            .filter { chapter ->
                when (chapterFilter) {
                    "Unread" -> progress.none { it.chapterKey == chapter.key && it.completed }
                    "Downloaded" ->
                        downloads.any { it.releaseId == chapter.id && it.state == "Complete" }
                    else -> true
                }
            }
            .let { if (settings.newestFirst) it.reversed() else it }
    val chapterIds = visibleChapters.map { it.id }
    val chapterSelection = rememberMultiSelection(chapterIds)
    var showGroups by remember { mutableStateOf(false) }
    var alternateFor by remember { mutableStateOf<app.komori.domain.Release?>(null) }
    var showMigration by remember { mutableStateOf(false) }
    val migrationLinks by vm.migrationLinks.collectAsStateWithLifecycle()
    var showStatus by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val creators =
        remember(manga.creatorsJson) {
            JSONArray(manga.creatorsJson)
                .objects()
                .map { Creator(it.getString("id"), it.getString("name"), it.getString("role")) }
                .distinctBy { it.id }
        }
    LazyColumn(
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MangaCover(
                    manga,
                    null,
                    Modifier.width(112.dp)
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        manga.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        manga.status.replaceFirstChar { it.uppercase() },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(sourceName(manga.id), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    onClick = { vm.startReading() },
                    enabled = selected.any { it.readable } && !loading,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(KomoriIcons.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (entry?.lastRelease != null) "Continue" else "Start reading")
                }
                FilledTonalIconButton(
                    onClick = {
                        if (entry?.saved != true && duplicates.isNotEmpty()) confirmDuplicate = true
                        else vm.toggle(manga)
                    }
                ) {
                    Icon(
                        if (entry?.saved == true) KomoriIcons.BookmarkAdded
                        else KomoriIcons.BookmarkAdd,
                        if (entry?.saved == true) "Remove from library" else "Add to library",
                    )
                }
                FilledTonalIconButton(onClick = { vm.toggle(manga, true) }) {
                    Icon(
                        KomoriIcons.FavoriteBorder,
                        if (entry?.liked == true) "Unlike" else "Like",
                        tint =
                            if (entry?.liked == true) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row {
                TextButton(onClick = { vm.startReading(true) }) { Text("Read from first chapter") }
                TextButton(onClick = { showStatus = true }) { Text(entry?.status ?: "Set status") }
            }
        }
        item {
            Text(
                manga.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item {
            val metadata = remember(manga.tagsJson) { manga.metadata() }
            listOf("parody" to "Parodies", "language" to "Languages", "category" to "Categories", "tag" to "Tags")
                .forEach { (type, heading) ->
                    val entries = metadata.filter { it.type == type }
                    if (entries.isNotEmpty()) {
                        Text(heading, style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            entries.forEach { tag ->
                                AssistChip(onClick = { browseTag(tag) }, label = { Text(tag.name) })
                            }
                        }
                    }
                }
        }
        items(creators, key = { it.id }) { creator ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { browseCreator(creator) }) {
                    Text(creator.name, fontWeight = FontWeight.Medium)
                    Text(
                        creator.role.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                TextButton(onClick = { vm.follow(creator) }) {
                    Text(
                        if (followed.any { it.id == creator.id }) "Following" else "Follow locally"
                    )
                }
            }
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Chapters",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showGroups = true }) {
                    Icon(KomoriIcons.FilterList, null)
                    Text("Filters")
                }
            }
            Text(
                "${visibleChapters.size} shown / ${raw.size} available · ${languageName(vm.chapterLanguage())} · ${if (settings.smart) "Smart Chapters" else "All releases"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(chapterStatus, style = MaterialTheme.typography.bodySmall)
            if (pref?.strict == true && !pref.groupId.isBlank())
                Text(
                    "Only preferred group is enabled; other groups are hidden.",
                    style = MaterialTheme.typography.bodySmall,
                )
            Row {
                TextButton(onClick = { showLanguages = true }) { Text("Language") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Unread", "Downloaded").forEach { label ->
                    FilterChip(
                        chapterFilter == label,
                        { chapterFilter = label },
                        label = { Text(label) },
                    )
                }
            }
            Row {
                TextButton(onClick = { vm.setting("newestFirst", !settings.newestFirst) }) {
                    Text(if (settings.newestFirst) "Newest first ↓" else "Oldest first ↑")
                }
                TextButton(
                    onClick = { showBulkDownloads = true },
                    enabled = !loading && bulkStatus == null,
                ) {
                    Text("Download chapters")
                }
            }
            bulkStatus?.let {
                Text(it, style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (visibleChapters.isEmpty())
            item {
                Text(
                    if (loading) "Loading chapters…"
                    else if (raw.isEmpty())
                        "No chapters are available for this language. Check the refresh status above, or choose another language. Smart Chapters cannot restore source-removed chapters."
                    else
                        "Chapters are hidden by your filters. Choose All, disable Only preferred group, or show unavailable chapters in Filters.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        if (chapterSelection.active) stickyHeader {
            SelectionBar(
                chapterSelection,
                chapterIds,
                listOf(
                    BatchAction("Download selected") { ids ->
                        vm.downloadBatch(visibleChapters.filter { it.id in ids })
                    },
                    BatchAction("Mark read") { ids ->
                        vm.batchChapters(visibleChapters.filter { it.id in ids }, true)
                    },
                    BatchAction("Mark unread") { ids ->
                        vm.batchChapters(visibleChapters.filter { it.id in ids }, false)
                    },
                    BatchAction("Delete downloads", true) { ids ->
                        vm.batchDownloads(ids, "Delete downloads")
                    },
                ),
            )
        }
        items(visibleChapters, key = { it.id }) { chapter ->
            val downloaded = downloads.find { it.releaseId == chapter.id }
            val read = progress.any { it.chapterKey == chapter.key && it.completed }
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .combinedClickable(
                        onLongClick = { chapterSelection.toggle(chapter.id) },
                        onClick = {
                            if (chapterSelection.active) chapterSelection.toggle(chapter.id)
                            else if (
                                !chapter.local &&
                                    !chapter.unavailable &&
                                    chapter.externalUrl?.startsWith("https://") == true
                            )
                                uri.openUri(chapter.externalUrl)
                            else vm.read(chapter)
                        },
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (read) KomoriIcons.CheckCircle else KomoriIcons.MenuBook,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(chapter.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${chapter.language.uppercase()} · ${chapter.groupNames}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (chapter.local)
                        Text(
                            "Downloaded · Read offline",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    else if (chapter.unavailable)
                        Text("Unavailable on MangaDex", style = MaterialTheme.typography.labelSmall)
                    else if (chapter.externalUrl != null)
                        Text("Open external source", style = MaterialTheme.typography.labelSmall)
                    if (downloaded != null && downloaded.state != "Complete")
                        Text(
                            if (downloaded.total > 0)
                                "${downloaded.state} · ${downloaded.done}/${downloaded.total} pages"
                            else downloaded.state,
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                if (downloaded.state == "Failed") MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                        )
                }
                if (chapterSelection.active)
                    Checkbox(
                        chapter.id in chapterSelection.ids,
                        { chapterSelection.toggle(chapter.id) },
                        Modifier.semantics { contentDescription = "Select ${chapter.label}" },
                    )
                else {
                    if (raw.count { it.key == chapter.key } > 1)
                        TextButton(onClick = { alternateFor = chapter }) { Text("Releases") }
                    ChapterDownloadControl(vm, chapter, downloaded)
                }
            }
        }
        item {
            TextButton(onClick = { showMigration = true }) { Text("Find another edition / source") }
            migrationLinks.filter { it.fromId == manga.id || it.toId == manga.id }.forEach { link ->
                val other = if (link.fromId == manga.id) link.toId else link.fromId
                library.find { it.id == other }?.let { linked ->
                    TextButton(onClick = { vm.open(linked) }) { Text("Linked: ${sourceName(other)} · ${linked.title}") }
                }
            }
            if (sourceKey(manga.id) != "local") TextButton(onClick = { uri.openUri(sourcePageUrl(manga.id)) }) {
                Text("View on ${sourceName(manga.id)}")
            }
            Text(
                "Chapters credited to the scanlation groups shown above.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (showStatus)
        AlertDialog(
            onDismissRequest = { showStatus = false },
            title = { Text("Reading status") },
            text = {
                Column {
                    listOf("Plan to read", "Reading", "On hold", "Completed", "Dropped").forEach {
                        value ->
                        TextButton(
                            onClick = {
                                vm.status(manga, value)
                                showStatus = false
                            }
                        ) {
                            Text(value)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    if (showMigration) MigrationDialog(vm, manga, dismiss = { showMigration = false })
    alternateFor?.let { chosen ->
        val alternatives = raw.filter { it.key == chosen.key }
        AlertDialog(onDismissRequest = { alternateFor = null }, title = { Text("${chosen.label} releases") },
            text = { Column {
                Text(if (!settings.smart) "All releases are shown."
                    else if (shelf.any { it.mangaId == manga.id && it.lastRelease == chosen.id }) "The current release stays selected."
                    else if (preferences.find { it.mangaId == manga.id }?.groupId in chosen.groups) "Your preferred scanlation group is available."
                    else "Smart Chapters picks a readable release, then scanlation continuity and a stable fallback.", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(alternatives, key = { it.id }) { release ->
                        ListItem(headlineContent = { Text(release.groupNames) },
                            supportingContent = { Text("${release.language.uppercase()} · ${if (release.readable) if (release.local) "Downloaded" else "Readable" else "Unavailable"}") },
                            leadingContent = { RadioButton(release.id == chosen.id, null) },
                            modifier = Modifier.clickable(enabled = release.readable) { alternateFor = null; vm.read(release) })
                    }
                }
            } }, confirmButton = { TextButton(onClick = { alternateFor = null }) { Text("Close") } })
    }
    if (showLanguages)
        LanguagePicker(vm.chapterLanguage(), sourceLanguages, { showLanguages = false }, titleOnly = true) {
            vm.titleLanguage(it)
            showLanguages = false
        }
    if (showGroups)
        ModalBottomSheet(onDismissRequest = { showGroups = false }) {
            LazyColumn(Modifier.padding(20.dp)) {
                item {
                    Text("Chapter filters", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Your current release stays selected while you read. Group preferences apply to other chapters.",
                        Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    Toggle(
                        "Smart Chapters",
                        "Show one release per numbered chapter",
                        settings.smart,
                    ) {
                        vm.setting("smart", it)
                    }
                }
                item {
                    Toggle(
                        "Show unavailable chapters",
                        "Display source-unavailable entries with an explanation",
                        settings.showUnavailable,
                    ) {
                        vm.setting("showUnavailable", it)
                    }
                }
                item {
                    TextButton(
                        onClick = {
                            vm.group("")
                            showGroups = false
                        }
                    ) {
                        Text("Automatic selection")
                    }
                }
                items(
                    raw.flatMap { r -> r.groups.map { it to r.groupNames } }.distinctBy { it.first }
                ) { (id, name) ->
                    TextButton(
                        onClick = {
                            vm.group(id, pref?.strict ?: false)
                            showGroups = false
                        }
                    ) {
                        Text(if (pref?.groupId == id) "✓ $name" else name)
                    }
                }
                item {
                    Toggle(
                        "Only preferred group",
                        "Leave gaps instead of using another scanlation",
                        pref?.strict ?: false,
                    ) {
                        vm.group(pref?.groupId ?: "", it)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
}

