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
fun KomoriScreen(vm: ReaderViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val activeSource by vm.activeSource.collectAsStateWithLifecycle()
    val reader by vm.reader.collectAsStateWithLifecycle()
    val readerSession by vm.readerSession.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val bulkStatus by vm.bulkStatus.collectAsStateWithLifecycle()
    val tab by vm.currentTab.collectAsStateWithLifecycle()
    val navigationEntry by vm.navigationEntry.collectAsStateWithLifecycle()
    val canNavigateBack by vm.canNavigateBack.collectAsStateWithLifecycle()
    val dark = when (settings.theme) {
        "system" -> androidx.compose.foundation.isSystemInDarkTheme()
        "light" -> false
        "dark" -> true
        else -> settings.dark
    }
    val screenState = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val view = LocalView.current
    LaunchedEffect(reader, dark) {
        (view.context as? Activity)?.window?.let { window ->
            view.post {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = reader == null && !dark
                    isAppearanceLightNavigationBars = reader == null && !dark
                }
            }
        }
    }
    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            vm.notice.value = null
        }
    }
    MaterialTheme(
        colorScheme = if (dark) Night else Day,
        typography = ReaderTypography,
        shapes = ReaderShapes,
    ) {
        UpdateDialog()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ReaderWindowEffects(reader != null, settings)
            ReaderVolumeKeys(vm, reader != null && settings.volumeKeys)
            if (reader != null)
                key(readerSession) {
                    MaterialTheme(colorScheme = Night) {
                        Surface(color = Color.Black, contentColor = Night.onSurface) {
                            ReaderScreen(vm, reader!!)
                        }
                    }
                }
            else {
                BackHandler(selected != null || canNavigateBack || tab != 0) {
                    if (selected != null || canNavigateBack) vm.back() else vm.currentTab.value = 0
                }
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    modifier =
                        Modifier.background(
                            Brush.radialGradient(
                                if (dark) listOf(Color(0xFF073017), Color.Black)
                                else listOf(Color(0xFFE1F2DB), Day.background),
                                center = Offset.Zero,
                                radius = 1000f,
                            )
                        ),
                    containerColor = Color.Transparent,
                    topBar = {
                        if (selected != null || tab == 4)
                            AppHeading(
                                if (selected != null) "Title details"
                                else
                                    listOf(
                                        "Your library",
                                        "Discover",
                                        "Following",
                                        "Downloads",
                                        "Settings",
                                    )[tab],
                                if (selected != null) ""
                                else
                                    listOf(
                                        "Your stories, in one place",
                                        "",
                                        "Authors & artists",
                                        "Ready whenever you are",
                                        "Make Komori yours",
                                    )[tab],
                                if (selected != null) vm::back else null,
                                browse = { vm.currentTab.value = 1 },
                                refresh = if (selected != null) ({ RefreshControl("Refresh title details", loading, vm::refreshDetails) }) else null,
                            )
                    },
                    bottomBar = {
                        if (selected == null) FloatingNavigation(tab) { vm.currentTab.value = it }
                    },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        UpdateBanner()
                        bulkStatus?.let { status ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    status,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                TextButton(onClick = vm::stopAddingDownloads) {
                                    Text("Stop adding")
                                }
                            }
                        }
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        error?.let { message ->
                            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        message,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    VerifySourceButton(
                                        vm,
                                        selected?.id?.let(::sourceKey) ?: activeSource,
                                    )
                                    TextButton(onClick = { vm.error.value = null }) {
                                        Text("Dismiss")
                                    }
                                }
                            }
                        }
                        screenState.SaveableStateProvider("$navigationEntry-$tab") {
                            if (selected != null)
                                Details(
                                    vm,
                                    selected!!,
                                    browseTag = { tag ->
                                        vm.browseTag(selected!!, tag)
                                        vm.currentTab.value = 1
                                    },
                                ) { creator ->
                                    vm.browseCreator(creator)
                                }
                            else
                                when (tab) {
                                    0 -> Library(vm, { vm.currentTab.value = 1 })
                                    1 -> Discover(vm)
                                    2 -> Authors(vm)
                                    3 -> Downloads(vm)
                                    4 -> SettingsScreen(vm, settings)
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun coverRequest(manga: Manga): coil.request.ImageRequest {
    val context = LocalContext.current
    return remember(context, manga.id, manga.cover) {
        coverImageRequest(context, manga)
    }
}

@Composable
internal fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.Top) {
        OutlinePanel {
            Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.secondary)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (action != null)
                ActionButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(action)
                }
        }
    }
}

@Composable
internal fun MangaCover(
    manga: Manga,
    description: String?,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showSource: Boolean = false,
) {
    var coverState by remember(manga.cover) { mutableIntStateOf(if (manga.cover.isEmpty()) 2 else 0) }
    val languages = remember(manga.tagsJson) { manga.coverLanguages() }
    Box(modifier) {
        AsyncImage(coverRequest(manga), description, Modifier.matchParentSize(), contentScale = contentScale,
            onLoading = { coverState = 0 }, onSuccess = { coverState = 1 }, onError = { coverState = 2 })
        if (coverState != 1) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            if (coverState == 0) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else { Icon(KomoriIcons.MenuBook, "No cover available", Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (showSource) Text(
            when (sourceKey(manga.id)) { "mangadex" -> "MD"; "demonic" -> "DS"; "weeb" -> "WC"; "zap" -> "HZ"; "imhentai" -> "IM"; "nhentai" -> "NH"; "manhwa18" -> "M18"; "manhwa18cc" -> "18C"; "local" -> "Local"; else -> "?" },
            Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)).padding(horizontal = 6.dp, vertical = 3.dp)
                .semantics { contentDescription = "Source: ${sourceName(manga.id)}" },
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
        )
        if (languages.isNotEmpty()) {
            Text(
                languages.joinToString(" / ") { it.code },
                Modifier.align(Alignment.TopStart).padding(6.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .semantics { contentDescription = "Language: ${languages.joinToString { it.name }}" },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun VerifySourceButton(vm: ReaderViewModel, source: String) {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
            ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.getStringExtra("source")?.let(vm::retrySource)
            }
        }
    if (source in sourceOrigins)
        TextButton(
            onClick = {
                launcher.launch(
                    Intent(context, SourceVerificationActivity::class.java)
                        .putExtra("source", source)
                )
            }
        ) {
            Text("Verify source")
        }
}

@Composable
internal fun Discover(vm: ReaderViewModel) {
    val results by vm.results.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val activeSource by vm.activeSource.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    val filter by vm.browseFilter.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val activeCreator by vm.activeCreator.collectAsStateWithLifecycle()
    val browseError by vm.error.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val info = grid.layoutInfo
            info.totalItemsCount > 0 &&
                (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, results.size, loading, browseError, activeSource, filter) {
        if (nearEnd && !loading && browseError == null && results.isNotEmpty())
            vm.search(more = true)
    }

    var showFilters by remember { mutableStateOf(false) }
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val canNavigateBack by vm.canNavigateBack.collectAsStateWithLifecycle()
    var query by rememberSaveable(activeSource) { mutableStateOf(searchQuery) }
    var submittedQuery by rememberSaveable(activeSource) { mutableStateOf(searchQuery) }
    LaunchedEffect(searchQuery) {
        query = searchQuery
        submittedQuery = searchQuery
    }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var sourceMenu by remember { mutableStateOf(false) }
    var saveSearch by remember { mutableStateOf(false) }
    var showSavedSearches by remember { mutableStateOf(false) }
    if (saveSearch) SaveSearchDialog(vm) { saveSearch = false }
    if (showSavedSearches) SavedSearchDialog(vm) { showSavedSearches = false }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val sources = enabledSourceOrder(settings)
    LaunchedEffect(sources, activeSource, activeCreator) {
        if (activeSource.isNotEmpty() && activeSource !in sources && activeCreator == null) {
            sources.firstOrNull()?.let(vm::chooseSource) ?: run {
                vm.results.value = emptyList(); vm.activeSource.value = ""
                vm.error.value = "Enable a source in Settings to browse titles."
            }
        }
    }
    val sourceTabs = rememberLazyListState()
    LaunchedEffect(activeSource, sources) {
        val index = sources.indexOf(activeSource)
        if (index >= 0) sourceTabs.animateScrollToItem(index)
    }
    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    fun closeSearch() {
        searchExpanded = false
        keyboard?.hide()
    }
    fun submitSearch() {
        submittedQuery = query
        vm.submitSearch(query)
        closeSearch()
    }
    BackHandler(searchExpanded && !canNavigateBack) { closeSearch() }
    LaunchedEffect(searchExpanded) { if (searchExpanded) focusRequester.requestFocus() }
    LaunchedEffect(activeSource) { closeSearch() }
    if (showFilters) SourceFilters(vm, activeSource, filter, tags) { showFilters = false }
    LaunchedEffect(Unit) { if (activeSource.isEmpty() && !loading) vm.search("", null) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchExpanded) {
                IconButton(onClick = { closeSearch() }) {
                    Icon(KomoriIcons.ArrowBack, "Close search")
                }
                TextField(
                    query,
                    { query = it },
                    Modifier.weight(1f).focusRequester(focusRequester),
                    placeholder = { Text("Search by ${filter.searchType.lowercase()}") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                )
                IconButton(onClick = { submitSearch() }) {
                    Icon(KomoriIcons.Search, "Search source")
                }
            } else {
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Text(
                        "Discover",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        submittedQuery.ifBlank {
                            filter.sort.ifEmpty {
                                vm.app.source.sorts(activeSource).firstOrNull().orEmpty()
                            }
                        } +
                            (if (filter.tags.isNotEmpty()) " · ${filter.tags.size} tags" else "") +
                            listOfNotNull(
                                    sourceBrowseLanguages(activeSource)
                                        .firstOrNull { it.id == filter.language }
                                        ?.name,
                                    sourceBrowseCategories(activeSource)
                                        .firstOrNull { it.id == filter.category }
                                        ?.name,
                                )
                                .joinToString(separator = " · ", prefix = " · ")
                                .takeIf {
                                    filter.language.isNotEmpty() || filter.category.isNotEmpty()
                                }
                                .orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RefreshControl("Refresh discover", loading, vm::refreshDiscover, activeSource.isNotEmpty())
                RoundControl(KomoriIcons.Search, "Open search", { searchExpanded = true })
                RoundControl(
                    KomoriIcons.FilterList,
                    "Filters and sorting",
                    {
                        showFilters = true
                        vm.loadTags()
                    },
                )
                Box {
                    RoundControl(KomoriIcons.More, "Source options", { sourceMenu = true })
                    DropdownMenu(sourceMenu, { sourceMenu = false }) {
                        VerifySourceButton(vm, activeSource)
                        DropdownMenuItem(text = { Text("Save this search") }, onClick = { sourceMenu = false; saveSearch = true })
                        DropdownMenuItem(text = { Text("Saved searches") }, onClick = { sourceMenu = false; showSavedSearches = true })
                        DropdownMenuItem(
                            text = { Text("Reset search") },
                            onClick = {
                                query = ""
                                submittedQuery = ""
                                vm.submitSearch("")
                                sourceMenu = false
                            },
                        )
                    }
                }
            }
        }
        LazyRow(
            state = sourceTabs,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sources, key = { it }) { key ->
                FilterChip(
                    activeSource == key,
                    { vm.chooseSource(key) },
                    label = { Text(sourceNames.getValue(key)) },
                    shape = RoundedCornerShape(50),
                )
            }
        }
        if (searchExpanded && activeSource in listOf("zap", "nhentai", "imhentai")) {
            Row(
                Modifier.padding(horizontal = 20.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                sourceSearchTypes(activeSource).forEach { type ->
                    FilterChip(
                        filter.searchType == type,
                        { vm.setSearchType(type) },
                        label = { Text(type) },
                    )
                }
            }
        }
        if (activeCreator != null || filter.facetPath.isNotEmpty()) {
            Row(
                Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    activeCreator?.name
                        ?: android.net.Uri.decode(filter.facetPath.trim('/').substringAfter('/')),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                )
                TextButton(onClick = vm::clearFacet) { Text("Clear") }
            }
        }
        var sourceDrag by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier.weight(1f)
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { sourceDrag += it },
                    onDragStarted = { sourceDrag = 0f },
                    onDragStopped = {
                        val index = sources.indexOf(activeSource)
                        val next =
                            when {
                                sourceDrag < -swipeThreshold -> index + 1
                                sourceDrag > swipeThreshold -> index - 1
                                else -> index
                            }
                        if (index >= 0 && next != index && next in sources.indices)
                            vm.chooseSource(sources[next])
                        sourceDrag = 0f
                    },
                )
        ) {
            if (results.isEmpty() && loading) {
                OutlinePanel(Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Loading titles…", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        sourceNames[activeSource].orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (results.isEmpty())
                EmptyState(
                    KomoriIcons.Search,
                    "No titles to show",
                    if (vm.canLoadMore && browseError == null) "No matching works on this page. More source results are available."
                    else "Try a different search, or check your connection.",
                    if (vm.canLoadMore && browseError == null) "Continue searching" else "Retry",
                    { vm.search(more = vm.canLoadMore && browseError == null) },
                )
            else
                MangaGrid(
                    vm,
                    results,
                    gridState = grid,
                    onOpen = { vm.open(it) },
                    onRead = { vm.open(it, true) },
                    subtitle = { title ->
                        if (shelf.any { it.mangaId == title.id && it.saved }) "In your library"
                        else if (duplicateTitles(title, library, shelf).isNotEmpty())
                            "Possible duplicate in library"
                        else ""
                    },
                    footer = {
                        if (vm.canLoadMore && results.isNotEmpty())
                            TextButton(
                                enabled = !loading,
                                onClick = { vm.search(more = true) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    if (loading) "Loading more titles…"
                                    else if (browseError != null) "Retry loading titles"
                                    else "Load more titles"
                                )
                            }
                    },
                )
        }
    }
}

@Composable
internal fun MangaGrid(
    vm: ReaderViewModel,
    manga: List<Manga>,
    onOpen: (Manga) -> Unit,
    onRead: (Manga) -> Unit,
    subtitle: (Manga) -> String = { "" },
    footer: @Composable () -> Unit = {},
    unreadCounts: Map<String, Int> = emptyMap(),
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var categorySelection by remember { mutableStateOf<List<String>?>(null) }
    val available = manga.map { it.id }
    val selection = rememberMultiSelection(available)
    val actions =
        listOf("Save", "Like", "Unlike", "Remove from Library", "Mark completed", "Mark all read", "Mark unread").map { label ->
            BatchAction(label, label == "Remove from Library") { ids ->
                vm.batchTitles(manga.filter { it.id in ids }, label)
            }
        } +
            listOf(
                BatchAction("Set categories") { categorySelection = it },
                BatchAction("Download all chapters") { ids ->
                    vm.downloadTitles(manga.filter { it.id in ids }, false)
                },
                BatchAction("Download unread") { ids ->
                    vm.downloadTitles(manga.filter { it.id in ids }, true)
                },
                BatchAction("Delete downloads", true) { ids ->
                    vm.batchDownloads(
                        vm.downloads.value.filter { it.mangaId in ids }.map { it.releaseId },
                        "Delete downloads",
                    )
                },
            )
    categorySelection?.let { ids -> CategoryAssignmentDialog(vm, ids) { categorySelection = null } }
    Column(Modifier.fillMaxSize()) {
        SelectionBar(selection, available, actions)
        LazyVerticalGrid(
            GridCells.Adaptive(if (settings.compact) 140.dp else 170.dp),
            state = gridState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(manga, key = { it.id }) { title ->
                Column {
                    Box {
                        MangaCover(
                            title,
                            title.title,
                            Modifier.fillMaxWidth()
                                .aspectRatio(0.7f)
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(16.dp),
                                )
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .combinedClickable(
                                    onLongClick = { selection.toggle(title.id) },
                                    onClick = {
                                        if (selection.active) selection.toggle(title.id)
                                        else onOpen(title)
                                    },
                                ),
                            contentScale = ContentScale.Crop,
                            showSource = !selection.active,
                        )
                        if ((unreadCounts[title.id] ?: 0) > 0) Text(
                            "${unreadCounts[title.id]} unread",
                            Modifier.align(Alignment.BottomStart).padding(8.dp)
                                .clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                        )
                        if (selection.active)
                            Checkbox(
                                title.id in selection.ids,
                                { selection.toggle(title.id) },
                                Modifier.align(Alignment.TopEnd).semantics {
                                    contentDescription = "Select ${title.title}"
                                },
                            )
                        else
                            FilledIconButton(
                                onClick = { onRead(title) },
                                modifier =
                                    Modifier.align(Alignment.BottomEnd).padding(8.dp).size(48.dp),
                                colors =
                                    IconButtonDefaults.filledIconButtonColors(
                                        containerColor =
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                        contentColor = MaterialTheme.colorScheme.primary,
                                    ),
                            ) {
                                Icon(KomoriIcons.PlayArrow, "Read ${title.title}")
                            }
                    }
                    Text(
                        title.title,
                        Modifier.padding(top = 8.dp)
                            .combinedClickable(
                                onLongClick = { selection.toggle(title.id) },
                                onClick = {
                                    if (selection.active) selection.toggle(title.id)
                                    else onOpen(title)
                                },
                            ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (subtitle(title).isNotEmpty())
                        Text(
                            subtitle(title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { footer() }
        }
    }
}

internal fun languageName(code: String): String =
    if (code == "*") "All languages"
    else if (code == "es-la") "Spanish (Latin America)"
    else if (code == "zh-hk") "Chinese (Traditional)"
    else
        java.util.Locale.forLanguageTag(code).getDisplayName(java.util.Locale.ENGLISH).ifBlank {
            code
        }

@Composable
internal fun LanguagePicker(
    current: String,
    sourceLanguages: List<String>,
    dismiss: () -> Unit,
    titleOnly: Boolean = false,
    choose: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Chapter language") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                item {
                    Text(
                        if (titleOnly) "Applies to this title. Source language hints can include removed translations." else "Applies across the app. MangaDex language metadata can include translations that are no longer available.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                items(
                    (listOf("*", current, "en", "id", "ja") +
                            sourceLanguages +
                            listOf(
                                "es",
                                "es-la",
                                "pt-br",
                                "fr",
                                "ko",
                                "zh",
                                "zh-hk",
                                "ru",
                                "vi",
                                "th",
                                "de",
                                "it",
                                "ar",
                                "tr",
                                "pl",
                            ))
                        .distinct()
                ) { code ->
                    TextButton(onClick = { choose(code) }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (code == current) "✓ " else "") + languageName(code))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Close") } },
    )
}

@Composable
internal fun BrowseChoice(
    title: String,
    allLabel: String,
    choices: List<SourceTag>,
    selected: String,
    choose: (String) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected.isEmpty(), { choose("") }, label = { Text(allLabel) })
        choices.forEach { option ->
            FilterChip(selected == option.id, { choose(option.id) }, label = { Text(option.name) })
        }
    }
}

@Composable
internal fun SourceFilters(
    vm: ReaderViewModel,
    source: String,
    filter: BrowseFilter,
    tags: List<SourceTag>,
    close: () -> Unit,
) {
    val creator by vm.activeCreator.collectAsStateWithLifecycle()
    val facetBrowse = source in gallerySources &&
        (creator != null || filter.facetPath.isNotEmpty() || filter.searchType != "Title")
    val localMetadata = source in setOf("zap", "imhentai") && facetBrowse
    val currentQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val pageSearch = source in setOf("demonic", "manhwa18cc") && currentQuery.isNotBlank()
    val sorts = if (pageSearch) listOf("Source order", "Title (this page)") else vm.app.source.sorts(source, facetBrowse)
    var sort by remember { mutableStateOf(filter.sort.takeIf { it in sorts } ?: sorts.firstOrNull().orEmpty()) }
    var language by remember { mutableStateOf(filter.language) }
    var category by remember { mutableStateOf(filter.category) }
    var chosen by remember { mutableStateOf(filter.tags) }
    var tagQuery by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(filter.status) }
    var ratings by remember { mutableStateOf(filter.ratings) }
    val initialFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = close,
        title = {
            LaunchedEffect(Unit) { initialFocus.requestFocus() }
            Text("Browse filters", Modifier.focusRequester(initialFocus).focusable())
        },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sort", style = MaterialTheme.typography.titleSmall)
                if (pageSearch) Text("This site's text search has no catalog ranking. Alphabetical sorting applies to the current result page.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sorts.forEach { value ->
                        FilterChip(sort == value, { sort = value }, label = { Text(value) })
                    }
                }
                if (source !in gallerySources) {
                    BrowseChoice("Chapter language", "All available languages", sourceBrowseLanguages(source), language) { language = it }
                    if (sourceBrowseCategories(source).isNotEmpty())
                        BrowseChoice(if (source == "mangadex") "Original language / origin" else "Category", "All categories", sourceBrowseCategories(source), category) { category = it }
                    if (source == "mangadex" || source in manhwaSources)
                        BrowseChoice("Publication status", "All statuses", listOf("ongoing", "completed", "hiatus", "cancelled").filter { source == "mangadex" || it != "cancelled" }.map { SourceTag(it, it.replaceFirstChar(Char::uppercase)) }, status) { status = it }
                    if (source == "mangadex") {
                        Text("Content rating", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("safe", "suggestive", "erotica", "pornographic").forEach { value ->
                                FilterChip(value in ratings, { if (value in ratings && ratings.size > 1) ratings = ratings - value else ratings = ratings + value }, { Text(value.replaceFirstChar(Char::uppercase)) })
                            }
                        }
                    }
                    if (source == "manhwa18cc") Text("Genre, category and status refine the current result page during text search. More pages continue loading; this is not a whole-catalog sort.", style = MaterialTheme.typography.bodySmall)
                }
                if (source in gallerySources) {
                    if (localMetadata) Text(
                        "Language and category refine the current metadata result page using gallery details. More pages continue loading.",
                        style = MaterialTheme.typography.bodySmall)
                    run {
                        BrowseChoice("Language", "All languages", sourceBrowseLanguages(source), language) { language = it }
                        BrowseChoice("Category", "All categories", sourceBrowseCategories(source), category) { category = it }
                    }
                    Text("Use Parody search for an exact source name, or tap Parodies on title details.",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (source == "demonic") Text(
                    "Genres also refine the current title-search results using title metadata.",
                    style = MaterialTheme.typography.bodySmall)
                if (source !in setOf("zap", "imhentai")) {
                    OutlinedTextField(tagQuery, { tagQuery = it }, label = { Text("Find a genre or tag") }, singleLine = true)
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(tags.filter { it.name.contains(tagQuery, true) }, key = { it.id }) { tag ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                                chosen = if (tag.id in chosen) chosen - tag.id else chosen + tag.id
                            }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(tag.id in chosen, null)
                                Text(tag.name)
                            }
                        }
                        if (tags.isEmpty()) item { Text("Tags unavailable or still loading.") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.applyFilter(filter.copy(sort = sort,
                    tags = if (source in setOf("zap", "imhentai")) emptySet() else chosen,
                    language = language,
                    category = category,
                    status = status, ratings = ratings))
                close()
            }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = { vm.applyFilter(BrowseFilter()); close() }) { Text("Reset") }
        },
    )
}

@Composable
internal fun BulkDownloadDialog(vm: ReaderViewModel, close: () -> Unit) {
    var choice by remember { mutableStateOf(DownloadChoice.NEXT_5) }
    var first by rememberSaveable { mutableStateOf("1") }
    var last by rememberSaveable { mutableStateOf("10") }
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val chapters by vm.chapters.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    val candidates =
        remember(choice, first, last, downloads, progress, chapters, preferences, settings, shelf) {
            vm.downloadCandidates(choice, first.toIntOrNull() ?: 0, last.toIntOrNull() ?: 0)
        }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Download chapters") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Uses your language and Smart Chapter preferences. Completed and active downloads are skipped.",
                    style = MaterialTheme.typography.bodySmall,
                )
                DownloadChoice.entries.forEach { value ->
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = choice == value,
                                role = Role.RadioButton,
                                onClick = { choice = value },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(choice == value, null)
                        Text(value.label)
                    }
                }
                if (choice == DownloadChoice.RANGE) {
                    val readable = vm.selectedChapters().filter { it.readable }
                    Text("Choose list positions in reading order, including extras (1–${readable.size}).", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(first, { first = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f), label = { Text("From position") },
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), singleLine = true)
                        OutlinedTextField(last, { last = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f), label = { Text("To position") },
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), singleLine = true)
                    }
                    val from = first.toIntOrNull()?.let { readable.getOrNull(it - 1) }
                    val to = last.toIntOrNull()?.let { readable.getOrNull(it - 1) }
                    if (from != null && to != null) Text("${from.label} → ${to.label}", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "${candidates.size} chapters to queue",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = candidates.isNotEmpty(),
                onClick = {
                    vm.downloadBatch(vm.downloadCandidates(choice, first.toIntOrNull() ?: 0, last.toIntOrNull() ?: 0))
                    close()
                },
            ) {
                Text("Queue ${candidates.size}")
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}

@Composable
internal fun ChapterDownloadControl(
    vm: ReaderViewModel,
    chapter: app.komori.domain.Release,
    download: Download?,
) {
    when (download?.state) {
        "Complete" ->
            IconButton(onClick = { vm.readOffline(download) }) {
                Icon(KomoriIcons.OfflinePin, "Read downloaded ${chapter.label}")
            }
        "Queued",
        "Downloading",
        "Retrying" ->
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (download.state == "Downloading" && download.total > 0)
                    CircularProgressIndicator(
                        progress = { download.done.toFloat() / download.total },
                        modifier = Modifier.size(38.dp),
                        strokeWidth = 2.dp,
                    )
                else CircularProgressIndicator(Modifier.size(38.dp), strokeWidth = 2.dp)
                TextButton(
                    onClick = { vm.pause(download) },
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Pause download ${chapter.label}"
                        },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("Ⅱ")
                }
            }
        "Paused",
        "Verification required",
        "Needs repair",
        "Failed" ->
            TextButton(onClick = { vm.resume(download) }) {
                Text(
                    if (download.state == "Needs repair") "Repair"
                    else if (download.state == "Failed") "Retry" else "Resume"
                )
            }
        else ->
            IconButton(onClick = { vm.download(chapter) }, enabled = chapter.readable) {
                Icon(KomoriIcons.Download, "Download ${chapter.label}")
            }
    }
}
