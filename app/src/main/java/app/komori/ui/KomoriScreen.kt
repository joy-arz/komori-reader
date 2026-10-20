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
    val screenState = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val view = LocalView.current
    LaunchedEffect(reader, settings.dark) {
        (view.context as? Activity)?.window?.let { window ->
            view.post {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = reader == null && !settings.dark
                    isAppearanceLightNavigationBars = reader == null && !settings.dark
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
        colorScheme = if (settings.dark) Night else Day,
        typography = ReaderTypography,
        shapes = ReaderShapes,
    ) {
        UpdateDialog()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                                if (settings.dark) listOf(Color(0xFF073017), Color.Black)
                                else listOf(Color(0xFFE1F2DB), Day.background),
                                center = Offset.Zero,
                                radius = 1000f,
                            )
                        ),
                    containerColor = Color.Transparent,
                    topBar = {
                        if (selected != null || tab != 1)
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
                                    2 -> Authors(vm) { creator -> vm.browseCreator(creator) }
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
private fun coverRequest(manga: Manga): coil.request.ImageRequest {
    val context = LocalContext.current
    return remember(context, manga.id, manga.cover) {
        coil.request.ImageRequest.Builder(context)
            .data(manga.cover)
            .apply { sourceOrigins[sourceKey(manga.id)]?.let { addHeader("Referer", "$it/") } }
            .build()
    }
}

@Composable
private fun EmptyState(
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
private fun Library(vm: ReaderViewModel, discover: () -> Unit) {
    val manga by vm.library.collectAsStateWithLifecycle()
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val downloadedIds = downloads.filter { it.done > 0 }.map { it.mangaId }.toSet()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("Saved") }
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun closeSearch() {
        searchExpanded = false
        query = ""
        keyboard?.hide()
    }
    BackHandler(searchExpanded) { closeSearch() }
    LaunchedEffect(searchExpanded) { if (searchExpanded) focus.requestFocus() }
    Column {
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
                    AsyncImage(
                        coverRequest(book),
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
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Collections", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (!searchExpanded)
                RoundControl(KomoriIcons.Search, "Search library", { searchExpanded = true })
        }
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
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.titleLarge,
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
            val titles =
                (if (filter == "Downloaded") manga.filter { it.id in downloadedIds }
                    else entries.mapNotNull { entry -> manga.find { it.id == entry.mangaId } })
                    .filter { it.title.contains(query, true) }
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
                    subtitle = { title -> shelf.find { it.mangaId == title.id }?.status ?: "" },
                )
        }
    }
}

@Composable
private fun Authors(vm: ReaderViewModel, browseCreator: (Creator) -> Unit) {
    val creators by vm.creators.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var manage by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val selected = creators.find { it.id == selectedId }
    val visible = creators.filter { it.name.contains(query, true) }
    val creatorSelection = rememberMultiSelection(visible.map { it.id })
    val followedIds = creators.map { it.id }.toSet()
    val savedIds = shelf.filter { it.saved || it.liked }.map { it.mangaId }.toSet()
    val cachedWorks =
        remember(library, savedIds, followedIds, selected) {
            library.filter { manga ->
                manga.id in savedIds &&
                    runCatching {
                            JSONArray(manga.creatorsJson).objects().any {
                                val id = it.optString("id")
                                if (selected != null) id == selected.id else id in followedIds
                            }
                        }
                        .getOrDefault(false)
            }
        }
    val feed by vm.authorFeed.collectAsStateWithLifecycle()
    LaunchedEffect(creators, selected) {
        vm.showAuthorWorks(selected?.let { listOf(it) } ?: creators)
    }
    val works = (feed.items + cachedWorks).distinctBy { it.id }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${creators.size} following",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(
                onClick = {
                    manage = !manage
                    creatorSelection.clear()
                    query = ""
                }
            ) {
                Text(if (manage) "Done" else "Manage")
            }
        }
        if (creators.isEmpty()) {
            EmptyState(
                KomoriIcons.PersonOutline,
                "Follow your favorite creators",
                "Follow an author or artist from a title. Explore their works here, including titles outside your Library.",
            )
        } else if (manage) {
            SelectionBar(
                creatorSelection,
                visible.map { it.id },
                listOf(BatchAction("Unfollow selected", true) { vm.batchCreators(it) }),
            )
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                placeholder = { Text("Find a creator") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
            )
            LazyColumn {
                items(visible, key = { it.id }) { creator ->
                    ListItem(
                        headlineContent = { Text(creator.name) },
                        supportingContent = { Text("${sourceName(creator.id)} · ${creator.role}") },
                        trailingContent = {
                            if (creatorSelection.active)
                                Checkbox(
                                    creator.id in creatorSelection.ids,
                                    { creatorSelection.toggle(creator.id) },
                                    Modifier.semantics {
                                        contentDescription = "Select ${creator.name}"
                                    },
                                )
                            else TextButton(onClick = { vm.follow(creator) }) { Text("Unfollow") }
                        },
                        modifier =
                            Modifier.combinedClickable(
                                onLongClick = { creatorSelection.toggle(creator.id) },
                                onClick = {
                                    if (creatorSelection.active) creatorSelection.toggle(creator.id)
                                    else {
                                        selectedId = creator.id
                                        manage = false
                                    }
                                },
                            ),
                    )
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { CreatorBubble("All", selected == null) { selectedId = null } }
                items(creators, key = { it.id }) { creator ->
                    CreatorBubble(creator.name, selected?.id == creator.id) {
                        selectedId = creator.id
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        selected?.name ?: "All works",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (selected != null) "${sourceName(selected.id)} · ${selected.role}"
                        else "From the creators you follow",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected != null)
                    TextButton(onClick = { browseCreator(selected) }) { Text("All works") }
            }
            key(selectedId) {
                val grid = rememberLazyGridState()
                val nearEnd by remember {
                    derivedStateOf {
                        val info = grid.layoutInfo
                        info.totalItemsCount > 0 &&
                            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >=
                                info.totalItemsCount - 5
                    }
                }
                LaunchedEffect(nearEnd, feed) {
                    if (
                        nearEnd && feed.items.isNotEmpty() && !feed.loading && feed.errors.isEmpty()
                    )
                        vm.loadAuthorWorks()
                }
                MangaGrid(
                    vm,
                    works,
                    { vm.open(it) },
                    { vm.open(it, true) },
                    subtitle = { if (it.id in savedIds) "In your Library" else "" },
                    gridState = grid,
                    footer = {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (feed.loading) CircularProgressIndicator(Modifier.padding(16.dp))
                            if (feed.errors.isNotEmpty()) {
                                Text(
                                    feed.errors.joinToString("\n"),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (feed.items.isEmpty() && cachedWorks.isNotEmpty())
                                    Text("Showing saved works while the source is unavailable.")
                            }
                            if (!feed.loading && feed.hasMore)
                                TextButton(onClick = vm::loadAuthorWorks) {
                                    Text(
                                        if (feed.errors.isEmpty()) "Load more works"
                                        else "Retry / load more"
                                    )
                                }
                            if (!feed.loading && !feed.hasMore && works.isEmpty())
                                Text("No works found.")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CreatorBubble(name: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(58.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    androidx.compose.foundation.shape.CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name
                    .split(" ")
                    .filter { it.isNotEmpty() }
                    .take(2)
                    .joinToString("") { it.take(1) }
                    .uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            name,
            Modifier.padding(top = 6.dp, bottom = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
        )
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
private fun Discover(vm: ReaderViewModel) {
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
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val sources = sourceOrder(settings.sources)
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
            Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 18.dp),
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
                    placeholder = { Text("Search ${filter.searchType.lowercase()}s") },
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
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        "Discover",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        submittedQuery.ifBlank {
                            filter.sort.ifEmpty {
                                vm.app.source.sorts(activeSource).firstOrNull().orEmpty()
                            }
                        } + if (filter.tags.isNotEmpty()) " · ${filter.tags.size} tags" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
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
                Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("Title", "Tag", "Author").forEach { type ->
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
                    "Try a different search, or check your connection.",
                    "Retry",
                    { vm.search() },
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
private fun MangaGrid(
    vm: ReaderViewModel,
    manga: List<Manga>,
    onOpen: (Manga) -> Unit,
    onRead: (Manga) -> Unit,
    subtitle: (Manga) -> String = { "" },
    footer: @Composable () -> Unit = {},
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val available = manga.map { it.id }
    val selection = rememberMultiSelection(available)
    val actions =
        listOf("Save", "Like", "Unlike", "Remove from Library", "Mark completed").map { label ->
            BatchAction(label, label == "Remove from Library") { ids ->
                vm.batchTitles(manga.filter { it.id in ids }, label)
            }
        } +
            listOf(
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
    Column(Modifier.fillMaxSize()) {
        SelectionBar(selection, available, actions)
        LazyVerticalGrid(
            GridCells.Adaptive(140.dp),
            state = gridState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(manga, key = { it.id }) { title ->
                Column {
                    Box {
                        AsyncImage(
                            coverRequest(title),
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

@Composable
private fun Details(
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
                AsyncImage(
                    coverRequest(manga),
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
            val metadataTags =
                remember(manga.tagsJson) {
                    JSONArray(manga.tagsJson).objects().map {
                        SourceTag(it.getString("id"), it.getString("name"))
                    }
                }
            if (metadataTags.isNotEmpty()) {
                Text("Tags", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    metadataTags.forEach { tag ->
                        AssistChip(onClick = { browseTag(tag) }, label = { Text(tag.name) })
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
                TextButton(onClick = { vm.open(manga) }, enabled = !loading) { Text("Refresh") }
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
        stickyHeader {
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
                else ChapterDownloadControl(vm, chapter, downloaded)
            }
        }
        item {
            TextButton(onClick = { uri.openUri(sourcePageUrl(manga.id)) }) {
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
    if (showLanguages)
        LanguagePicker(settings.language, sourceLanguages, { showLanguages = false }) {
            vm.language(it)
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

@Composable
private fun Downloads(vm: ReaderViewModel) {
    val transfers by vm.downloadTransfers.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val manga by vm.library.collectAsStateWithLifecycle()
    val downloadIds = downloads.map { it.releaseId }
    val selection = rememberMultiSelection(downloadIds)
    var deleting by remember { mutableStateOf<Download?>(null) }
    if (downloads.isEmpty())
        EmptyState(
            KomoriIcons.Download,
            "Take a few chapters with you",
            "Download from a title's chapter list. Finished downloads remain available offline.",
        )
    else
        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            stickyHeader {
                SelectionBar(
                    selection,
                    downloadIds,
                    listOf(
                        BatchAction("Pause selected") { vm.batchDownloads(it, "Pause") },
                        BatchAction("Resume selected") { vm.batchDownloads(it, "Resume") },
                        BatchAction("Delete downloads", true) {
                            vm.batchDownloads(it, "Delete downloads")
                        },
                    ),
                )
            }
            item {
                val active = downloads.count { it.state in DownloadPolicy.activeStates }
                val complete = downloads.count { it.state == "Complete" }
                Text(
                    "$active in queue · $complete ready offline",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row {
                    TextButton(enabled = active > 0, onClick = { vm.pauseAll() }) {
                        Text("Pause queue")
                    }
                    TextButton(
                        enabled =
                            downloads.any {
                                it.state in
                                    listOf(
                                        "Paused",
                                        "Failed",
                                        "Verification required",
                                        "Needs repair",
                                    )
                            },
                        onClick = { vm.resumeAll() },
                    ) {
                        Text("Resume / retry all")
                    }
                }
            }
            items(
                downloads.sortedBy { if (it.state == "Complete") 1 else 0 },
                key = { it.releaseId },
            ) { download ->
                OutlinedCard(
                    modifier =
                        Modifier.combinedClickable(
                            onLongClick = { selection.toggle(download.releaseId) },
                            onClick = { if (selection.active) selection.toggle(download.releaseId) },
                        ),
                    colors =
                        CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        if (selection.active)
                            Checkbox(
                                download.releaseId in selection.ids,
                                { selection.toggle(download.releaseId) },
                                Modifier.semantics {
                                    contentDescription = "Select download ${download.label}"
                                },
                            )
                        Text(
                            manga.find { it.id == download.mangaId }?.title ?: download.label,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "${download.label} · ${download.state}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val transfer = transfers[download.releaseId]
                        val receiving = download.state == "Downloading" && transfer != null
                        if (receiving && (transfer!!.resolving || transfer.totalBytes <= 0)) {
                            LinearProgressIndicator(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp)
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = {
                                    val fraction =
                                        if (receiving && transfer!!.totalBytes > 0)
                                            (transfer.bytes.toFloat() / transfer.totalBytes)
                                                .coerceIn(0f, 1f)
                                        else 0f
                                    if (download.total == 0) 0f
                                    else
                                        (((if (receiving) transfer!!.completedPages
                                            else download.done) + fraction) / download.total)
                                            .coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            )
                        }
                        Text(
                            when {
                                receiving && transfer!!.resolving -> "Finding chapter images…"
                                receiving ->
                                    "Page ${transfer!!.page} · ${transfer.bytes / 1024} KB" +
                                        if (transfer.totalBytes > 0)
                                            " / ${transfer.totalBytes / 1024} KB"
                                        else " received"
                                download.state == "Queued" ->
                                    if (settings.wifi)
                                        "Waiting for queue, unmetered Wi-Fi and available storage"
                                    else "Waiting for queue, connection and available storage"
                                download.state == "Retrying" ->
                                    "Retry scheduled · ${download.done} pages saved"
                                else -> "${download.done} / ${download.total} pages"
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (download.error.isNotEmpty())
                            Text(
                                download.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        if (download.state == "Verification required")
                            VerifySourceButton(vm, sourceKey(download.mangaId))
                        Row {
                            if (
                                download.state in
                                    listOf(
                                        "Paused",
                                        "Failed",
                                        "Verification required",
                                        "Needs repair",
                                    )
                            )
                                TextButton(onClick = { vm.resume(download) }) {
                                    Text(
                                        if (download.state == "Needs repair") "Repair" else "Resume"
                                    )
                                }
                            else if (download.state != "Complete")
                                TextButton(onClick = { vm.pause(download) }) { Text("Pause") }
                            if (download.done > 0)
                                TextButton(onClick = { vm.readOffline(download) }) {
                                    Text(
                                        if (download.state == "Complete") "Read offline"
                                        else "Read saved pages"
                                    )
                                }
                            TextButton(onClick = { deleting = download }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete downloaded chapter?") },
            text = { Text("Your reading progress and library entry will stay saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteDownload(item)
                        deleting = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit) {
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
private fun SettingsScreen(vm: ReaderViewModel, settings: Settings) {
    var section by rememberSaveable { mutableStateOf("Reading") }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Reading", "Sources", "App").forEach { label ->
                FilterChip(
                    section == label,
                    { section = label },
                    label = { Text(label) },
                    shape = RoundedCornerShape(50),
                )
            }
        }
        key(section) {
            LazyColumn(
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
                                    "${index + 1}. ${sourceNames.getValue(key)}${if(index == 0) " · Default" else ""}",
                                    Modifier.weight(1f),
                                )
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
                    item {
                        TextButton(onClick = { vm.resetSourceOrder() }) {
                            Text("Reset source order")
                        }
                    }
                }
                if (section == "Reading") {
                    item { Text("Reading", style = MaterialTheme.typography.titleLarge) }
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
                            "Read again when caught up",
                            "Start from the first chapter when all are read",
                            settings.reread,
                        ) {
                            vm.setting("reread", it)
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
                            "Dark appearance",
                            "A softer shelf for late-night reading",
                            settings.dark,
                        ) {
                            vm.setting("dark", it)
                        }
                    }
                    item {
                        HorizontalDivider()
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

private fun languageName(code: String): String =
    if (code == "*") "All languages"
    else if (code == "es-la") "Spanish (Latin America)"
    else if (code == "zh-hk") "Chinese (Traditional)"
    else
        java.util.Locale.forLanguageTag(code).getDisplayName(java.util.Locale.ENGLISH).ifBlank {
            code
        }

@Composable
private fun LanguagePicker(
    current: String,
    sourceLanguages: List<String>,
    dismiss: () -> Unit,
    choose: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Chapter language") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                item {
                    Text(
                        "Applies across the app. MangaDex language metadata can include translations that are no longer available.",
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
private fun SourceFilters(
    vm: ReaderViewModel,
    source: String,
    filter: BrowseFilter,
    tags: List<SourceTag>,
    close: () -> Unit,
) {
    var sort by remember {
        mutableStateOf(filter.sort.ifEmpty { vm.app.source.sorts(source).firstOrNull().orEmpty() })
    }
    val creator by vm.activeCreator.collectAsStateWithLifecycle()
    val facetBrowse =
        source in listOf("zap", "nhentai", "imhentai") &&
            (creator != null || filter.facetPath.isNotEmpty() || filter.searchType != "Title")
    var chosen by remember { mutableStateOf(filter.tags) }
    var tagQuery by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Browse filters") },
        text = {
            Column {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    vm.app.source.sorts(source).forEach { value ->
                        FilterChip(sort == value, { sort = value }, label = { Text(value) })
                    }
                }
                if (source in listOf("zap", "imhentai"))
                    Text(
                        "Categories filter title searches. Use Tag or Author search for an exact name, or tap metadata on a title.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                if (source == "demonic")
                    Text(
                        "Sort and tags apply to browsing. Title search is separate.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                if (facetBrowse) {
                    Text(
                        "Tag and category filters apply to title searches. Metadata browsing uses the exact source name.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    OutlinedTextField(
                        tagQuery,
                        { tagQuery = it },
                        label = {
                            Text(
                                if (source in listOf("zap", "imhentai")) "Find a category"
                                else "Find a tag"
                            )
                        },
                        singleLine = true,
                    )
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(tags.filter { it.name.contains(tagQuery, true) }, key = { it.id }) {
                            tag ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    chosen =
                                        if (tag.id in chosen) chosen - tag.id else chosen + tag.id
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
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
            TextButton(
                onClick = {
                    vm.applyFilter(
                        filter.copy(sort = sort, tags = if (facetBrowse) emptySet() else chosen)
                    )
                    close()
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    vm.applyFilter(BrowseFilter())
                    close()
                }
            ) {
                Text("Reset")
            }
        },
    )
}

@Composable
private fun BulkDownloadDialog(vm: ReaderViewModel, close: () -> Unit) {
    var choice by remember { mutableStateOf(DownloadChoice.NEXT_5) }
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val chapters by vm.chapters.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    val candidates =
        remember(choice, downloads, progress, chapters, preferences, settings, shelf) {
            vm.downloadCandidates(choice)
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
                    vm.downloadBatch(vm.downloadCandidates(choice))
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
private fun ChapterDownloadControl(
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
