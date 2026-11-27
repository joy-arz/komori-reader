@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
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
import kotlinx.coroutines.flow.first

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
    var librarySection by rememberSaveable { mutableStateOf(false) }
    var imagesSection by rememberSaveable { mutableStateOf(false) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    val libraryTabs = rememberLazyListState()
    val libraryGrid = rememberLazyGridState()
    var filter by rememberSaveable { mutableStateOf("Saved") }
    var libraryCollection by rememberSaveable { mutableStateOf("All titles") }
    var filters by rememberSaveable(stateSaver = LibraryFiltersSaver) { mutableStateOf(LibraryFilters()) }
    val activeCollection = if (librarySection) libraryCollection else filter
    var showLibraryFilters by remember { mutableStateOf(false) }
    var manageLibraries by remember { mutableStateOf(false) }
    if (manageLibraries) CategoryManagerDialog(vm) { manageLibraries = false }
    var knownLibraries by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(categoryNames, filters.categories) {
        val current = categoryNames.map { it.id }.toSet()
        // Wait for the first library load before pruning restored selections, but still
        // remove filters for libraries that were deleted while the app was running.
        val stale =
            if (current.isNotEmpty() || knownLibraries.isNotEmpty())
                filters.categories.filter { it != UNCATEGORIZED_LIBRARY && it !in current }.toSet()
            else emptySet()
        if (stale.isNotEmpty()) filters = filters.copy(categories = filters.categories - stale)
        knownLibraries = current
    }
    if (showLibraryFilters) LibraryFilterDialog(vm, filters, activeCollection, librarySection,
        { selected, collection ->
            filters = selected
            if (librarySection) libraryCollection = collection else filter = collection
        },
        { showLibraryFilters = false })
    val downloadedIds = remember(downloads) { downloadedMangaIds(downloads) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    fun openLibraryTitle(title: Manga, immediately: Boolean) {
        val offline =
            if (filters.download == LibraryDownloadFilter.DOWNLOADED)
                downloads.firstOrNull { it.mangaId == title.id && it.done > 0 }
            else null
        if (offline != null) vm.readOffline(offline, immediately, chooseStart = true)
        else vm.open(title, immediately)
    }
    LaunchedEffect(Unit) {
        if (!initialized) {
            val saved = vm.app.settings.flow.first().let { if (it.libraryStart == "last") it.lastLibraryView else it.libraryStart }
            librarySection = saved.startsWith("library:")
            if (librarySection) {
                val id = saved.removePrefix("library:")
                filters = filters.copy(categories = id.takeIf { it.isNotEmpty() && (it == UNCATEGORIZED_LIBRARY || vm.app.dao.backupCategories().any { row -> row.id == it }) }?.let(::setOf).orEmpty())
            } else {
                filter = saved.removePrefix("collection:").takeIf { it in listOf("Saved", "Liked", "History") } ?: "Saved"
            }
            initialized = true
        }
    }
    val persistedLibrarySelection = filters.categories.singleOrNull().orEmpty()
    LaunchedEffect(initialized, librarySection, persistedLibrarySelection, filter) {
        if (initialized) vm.libraryView(if (librarySection) "library:$persistedLibrarySelection" else "collection:$filter")
    }
    LaunchedEffect(initialized, librarySection, persistedLibrarySelection, categoryNames) {
        if (initialized && librarySection) {
            val index = categoryNames.indexOfFirst { it.id == persistedLibrarySelection }.let { if (it < 0) 0 else it + 1 }
            libraryTabs.scrollToItem(index)
        }
    }
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
    BackHandler(imagesSection) { imagesSection = false }
    BackHandler(searchExpanded && !imagesSection) { closeSearch() }
    LaunchedEffect(searchExpanded) { if (searchExpanded) focus.requestFocus() }
    if (!initialized) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
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
            if (!imagesSection) {
                RoundControl(KomoriIcons.Tune, "Filter library", { showLibraryFilters = true })
                if (!searchExpanded)
                    RoundControl(KomoriIcons.Search, "Search library", { searchExpanded = true })
            }
        }
        if (!imagesSection) {
            val activeFilters = buildList {
                if (librarySection && libraryCollection != "All titles")
                    add(libraryCollection to { libraryCollection = "All titles" })
                if (!librarySection && filter != "Saved")
                    add(filter to { filter = "Saved" })
                if (filters.download != LibraryDownloadFilter.ALL)
                    add(filters.download.label to { filters = filters.copy(download = LibraryDownloadFilter.ALL) })
                filters.statuses.forEach { status -> add(status to { filters = filters.copy(statuses = filters.statuses - status) }) }
                filters.categories.forEach { id ->
                    val label = if (id == UNCATEGORIZED_LIBRARY) "Uncategorized" else categoryNames.find { it.id == id }?.name ?: "Library"
                    add(label to { filters = filters.copy(categories = filters.categories - id) })
                }
                filters.sources.forEach { key ->
                    add(app.komori.data.sourceName(key) to { filters = filters.copy(sources = filters.sources - key) })
                }
                if (filters.favoritesOnly) add("Favorites" to { filters = filters.copy(favoritesOnly = false) })
                if (filters.unreadOnly) add("Unread chapters" to { filters = filters.copy(unreadOnly = false) })
            }
            if (activeFilters.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    activeFilters.forEach { (label, remove) ->
                        InputChip(
                            selected = true,
                            onClick = remove,
                            label = { Text(label) },
                            trailingIcon = { Icon(KomoriIcons.Close, "Remove $label", Modifier.size(16.dp)) },
                        )
                    }
                    TextButton(onClick = {
                        filters = LibraryFilters()
                        if (librarySection) libraryCollection = "All titles" else filter = "Saved"
                    }) { Text("Clear all") }
                }
            }
        }
        if (!imagesSection && searchExpanded)
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
        PrimaryTabRow(selectedTabIndex = if (imagesSection) 2 else if (librarySection) 1 else 0) {
            Tab(!librarySection && !imagesSection, { librarySection = false; imagesSection = false }, text = { Text("Collection") })
            Tab(librarySection && !imagesSection, { librarySection = true; imagesSection = false }, text = { Text("Libraries") })
            Tab(imagesSection, {
                imagesSection = true
                librarySection = false
                closeSearch()
            }, text = { Text("Images") })
        }
        if (imagesSection) {
            SavedImagesSection(vm, Modifier.weight(1f))
            return
        }
        if (!librarySection) Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("Saved", "Liked", "History").forEach { label ->
                val count = shelf.count {
                    when (label) { "Saved" -> it.saved; "Liked" -> it.liked; else -> it.lastRelease != null }
                }
                Column(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (filter == label) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .selectable(filter == label, role = Role.Tab, onClick = { filter = label })
                    .semantics { stateDescription = "$count titles" }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                    Text(count.toString(), Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), state = libraryTabs, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = filters.categories.isEmpty(), onClick = { filters = filters.copy(categories = emptySet()) }, label = { Text("All") }) }
                items(categoryNames, key = { it.id }) { library ->
                    FilterChip(
                        selected = library.id in filters.categories,
                        onClick = {
                            filters = filters.copy(
                                categories =
                                    if (library.id in filters.categories)
                                        filters.categories - library.id
                                    else filters.categories + library.id,
                            )
                        },
                        label = { Text(library.name) },
                    )
                }
            }
            RoundControl(KomoriIcons.CollectionsBookmark, "Manage libraries", { manageLibraries = true })
        }
        val recent = shelf.firstOrNull { it.lastRelease != null }
        val book = manga.find { it.id == recent?.mangaId }
        if (book != null && settings.showContinue && query.isEmpty() && !librarySection && filter == "Saved" && filters.isEmpty) {
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
        run {
            val entries =
                shelf.filter {
                    when (activeCollection) {
                        "All titles" -> true
                        "Liked" -> it.liked
                        "History" -> it.lastRelease != null
                        else -> it.saved
                    }
                }
            val categoriesByTitle = remember(memberships) { memberships.groupBy { it.mangaId }.mapValues { (_, rows) -> rows.map { it.categoryId }.toSet() } }
            val baseTitles =
                if (activeCollection == "All titles") manga
                else entries.mapNotNull { entry -> mangaById[entry.mangaId] }
            val titles =
                filterLibraryTitles(
                    baseTitles.filter { it.title.contains(query, true) },
                    shelfById,
                    countById,
                    downloadedIds,
                    categoriesByTitle,
                    filters,
                ).let {
                    if (settings.libraryAlphabetical)
                        it.sortedBy { book -> book.title.lowercase() }
                    else it
                }
            if (titles.isEmpty()) {
                val filtered = query.isNotEmpty() || !filters.isEmpty ||
                    (librarySection && libraryCollection != "All titles")
                EmptyState(
                    KomoriIcons.MenuBook,
                    if (filtered) "No matching titles" else "Your next story starts here",
                    if (filtered) "Try another library or clear your filters." else "Browse a source or download chapters to read offline.",
                    if (filtered) "Clear all filters" else "Explore sources",
                    if (filtered) ({
                        query = ""
                        filters = LibraryFilters()
                        if (librarySection) libraryCollection = "All titles" else filter = "Saved"
                    }) else discover,
                )
            }
            else
                MangaGrid(
                    vm,
                    titles,
                    onOpen = { title -> openLibraryTitle(title, settings.direct) },
                    onRead = { title -> openLibraryTitle(title, true) },
                    unreadCounts = countById.mapValues { it.value.unread },
                    subtitle = { title -> shelf.find { it.mangaId == title.id }?.status ?: "" },
                    gridState = libraryGrid,
                )
        }
    }
}

