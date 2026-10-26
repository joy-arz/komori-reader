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
import androidx.compose.ui.platform.LocalFocusManager
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
internal fun Library(vm: ReaderViewModel, discover: () -> Unit) {
    val updatesVisible by vm.updatesVisible.collectAsStateWithLifecycle()
    val manga by vm.library.collectAsStateWithLifecycle()
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val counts by vm.libraryCounts.collectAsStateWithLifecycle()
    val memberships by vm.memberships.collectAsStateWithLifecycle()
    val categoryNames by vm.categories.collectAsStateWithLifecycle()
    val countById = remember(counts) { counts.associateBy { it.mangaId } }
    val mangaById = remember(manga) { manga.associateBy { it.id } }
    val shelfById = remember(shelf) { shelf.associateBy { it.mangaId } }
    var category by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    var showLibraryFilters by remember { mutableStateOf(false) }
    if (showLibraryFilters) LibraryFilterDialog(vm, category, status, unreadOnly,
        { selectedCategory, selectedStatus, unread -> category = selectedCategory; status = selectedStatus; unreadOnly = unread },
        { showLibraryFilters = false })
    val downloadedIds = downloads.filter { it.done > 0 }.map { it.mangaId }.toSet()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("Saved") }
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    fun closeSearch() {
        searchExpanded = false
        query = ""
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }
    BackHandler(searchExpanded) { closeSearch() }
    LaunchedEffect(searchExpanded) { if (searchExpanded) focus.requestFocus() }
    if (updatesVisible) {
        LibraryUpdatesScreen(vm) { vm.updatesVisible.value = false }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Your library", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            val checking by vm.libraryRefreshing.collectAsStateWithLifecycle()
            RefreshControl("Refresh library", checking, vm::checkLibraryNow)
            RoundControl(KomoriIcons.CheckCircle, "Library updates", { vm.updatesVisible.value = true })
            RoundControl(KomoriIcons.Tune, "Filter library", { showLibraryFilters = true })
            if (!searchExpanded)
                RoundControl(KomoriIcons.Search, "Search library", { searchExpanded = true })
        }
        if (category.isNotEmpty() || status.isNotEmpty() || unreadOnly) {
            Text(listOfNotNull(category.takeIf { it.isNotEmpty() }?.let { id -> categoryNames.find { it.id == id }?.name ?: "Uncategorized" },
                status.takeIf { it.isNotEmpty() }, "Unread chapters".takeIf { unreadOnly }).joinToString(" · "),
                Modifier.padding(horizontal = 18.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
        }
        val recent = shelf.firstOrNull { it.lastRelease != null }
        val book = manga.find { it.id == recent?.mangaId }
        if (book != null && settings.showContinue && query.isEmpty() && filter == "Saved") {
            Card(
                onClick = { vm.open(book, true) },
                modifier = Modifier.fillMaxWidth().padding(20.dp, 0.dp, 20.dp, 16.dp),
                colors =
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    MangaCover(
                        book,
                        null,
                        Modifier.size(52.dp, 76.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(
                            "Continue reading",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("Continue your chapter", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { vm.setting("showContinue", false) }) { Text("Dismiss") }
                }
            }
        }
        if (searchExpanded)
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
                placeholder = { Text("Find something on your shelf") },
                leadingIcon = {
                    IconButton(onClick = { closeSearch() }) {
                        Icon(KomoriIcons.ArrowBack, "Close library search")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Saved", "Liked", "History", "Downloaded").forEach { label ->
                val count =
                    if (label == "Downloaded") downloadedIds.size
                    else
                        shelf.count {
                            when (label) {
                                "Saved" -> it.saved
                                "Liked" -> it.liked
                                else -> it.lastRelease != null
                            }
                        }
                Column(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            if (filter == label) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(14.dp),
                        )
                        .selectable(filter == label, role = Role.Tab, onClick = { filter = label })
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color =
                            if (filter == label) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        run {
            val entries =
                shelf.filter {
                    when (filter) {
                        "Liked" -> it.liked
                        "History" -> it.lastRelease != null
                        else -> it.saved
                    }
                }
            val categoriesByTitle = remember(memberships) { memberships.groupBy { it.mangaId }.mapValues { (_, rows) -> rows.map { it.categoryId }.toSet() } }
            val titles =
                (if (filter == "Downloaded") manga.filter { it.id in downloadedIds }
                    else entries.mapNotNull { entry -> mangaById[entry.mangaId] })
                    .filter { it.title.contains(query, true) }
                    .filter { status.isEmpty() || shelfById[it.id]?.status == status }
                    .filter { !unreadOnly || (countById[it.id]?.unread ?: 0) > 0 }
                    .filter { title -> category.isEmpty() || if (category == "uncategorized") categoriesByTitle[title.id].isNullOrEmpty()
                        else category in categoriesByTitle[title.id].orEmpty() }
                    .let {
                        if (settings.libraryAlphabetical)
                            it.sortedBy { book -> book.title.lowercase() }
                        else it
                    }
            if (titles.isEmpty())
                EmptyState(
                    KomoriIcons.MenuBook,
                    if (filter == "Downloaded") "No downloaded chapters"
                    else "Your next story starts here",
                    if (filter == "Downloaded")
                        "Download chapters from a title to read them offline here."
                    else "Browse a source, save a title, and make it yours.",
                    "Explore sources",
                    discover,
                )
            else
                MangaGrid(
                    vm,
                    titles,
                    onOpen = { title ->
                        if (filter == "Downloaded")
                            downloads
                                .firstOrNull { it.mangaId == title.id && it.done > 0 }
                                ?.let { vm.readOffline(it, settings.direct, chooseStart = true) }
                        else vm.open(title, settings.direct)
                    },
                    onRead = { title ->
                        if (filter == "Downloaded")
                            downloads
                                .firstOrNull { it.mangaId == title.id && it.done > 0 }
                                ?.let { vm.readOffline(it, chooseStart = true) }
                        else vm.open(title, true)
                    },
                    unreadCounts = countById.mapValues { it.value.unread },
                    subtitle = { title -> shelf.find { it.mangaId == title.id }?.status ?: "" },
                )
        }
    }
}

