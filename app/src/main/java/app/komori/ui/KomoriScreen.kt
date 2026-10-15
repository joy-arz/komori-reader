@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.komori.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.komori.BuildConfig
import app.komori.data.*
import app.komori.domain.DownloadChoice
import app.komori.domain.DownloadPolicy
import coil.compose.AsyncImage
import org.json.JSONArray

private val Day =
    lightColorScheme(
        primary = Color(0xFF1F6256),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD8ECE1),
        onPrimaryContainer = Color(0xFF244535),
        secondary = Color(0xFF56665D),
        secondaryContainer = Color(0xFFE0EBE2),
        onSecondaryContainer = Color(0xFF244535),
        surfaceContainer = Color(0xFFEAF0E9),
        background = Color(0xFFF6F8F5),
        surface = Color(0xFFF6F8F5),
        surfaceVariant = Color(0xFFE6ECE5),
    )
private val Night =
    darkColorScheme(
        primary = Color(0xFF9CD5BD),
        onPrimary = Color(0xFF163A2F),
        primaryContainer = Color(0xFF224C40),
        onPrimaryContainer = Color(0xFFC2E1D0),
        secondaryContainer = Color(0xFF2D473B),
        onSecondaryContainer = Color(0xFFC2E1D0),
        surfaceContainer = Color(0xFF1B251E),
        background = Color(0xFF121713),
        surface = Color(0xFF121713),
        surfaceVariant = Color(0xFF29332D),
    )

@Composable
fun KomoriScreen(vm: ReaderViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val reader by vm.reader.collectAsStateWithLifecycle()
    val readerSession by vm.readerSession.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
    MaterialTheme(colorScheme = if (settings.dark) Night else Day) {
        UpdateDialog()
        Surface(Modifier.fillMaxSize()) {
            if (reader != null)
                key(readerSession) {
                    MaterialTheme(colorScheme = Night) {
                        Surface(color = Color.Black, contentColor = Night.onSurface) {
                            ReaderScreen(vm, reader!!)
                        }
                    }
                }
            else {
                BackHandler(selected != null) { vm.back() }
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    if (selected != null) "Title details"
                                    else
                                        listOf("Your library", "Discover", "Downloads", "Settings")[
                                            tab],
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            navigationIcon = {
                                if (selected != null)
                                    IconButton(onClick = vm::back) {
                                        Icon(KomoriIcons.ArrowBack, "Back")
                                    }
                            },
                            actions = {
                                if (selected == null)
                                    Text(
                                        "Komori",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 20.dp),
                                    )
                            },
                        )
                    },
                    bottomBar = {
                        if (selected == null)
                            NavigationBar {
                                listOf(
                                        "Library" to KomoriIcons.CollectionsBookmark,
                                        "Discover" to KomoriIcons.Explore,
                                        "Downloads" to KomoriIcons.Download,
                                        "Settings" to KomoriIcons.Tune,
                                    )
                                    .forEachIndexed { index, item ->
                                        NavigationBarItem(
                                            selected = tab == index,
                                            onClick = { tab = index },
                                            icon = { Icon(item.second, item.first) },
                                            label = { Text(item.first) },
                                        )
                                    }
                            }
                    },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
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
                                    TextButton(onClick = { vm.error.value = null }) {
                                        Text("Dismiss")
                                    }
                                }
                            }
                        }
                        if (selected != null)
                            Details(vm, selected!!) { creator ->
                                vm.back()
                                tab = 1
                                vm.search("", creator)
                            }
                        else
                            when (tab) {
                                0 ->
                                    Library(
                                        vm,
                                        { tab = 1 },
                                        { creator ->
                                            tab = 1
                                            vm.search("", creator)
                                        },
                                    )
                                1 -> Discover(vm)
                                2 -> Downloads(vm)
                                3 -> SettingsScreen(vm, settings)
                            }
                    }
                }
            }
        }
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
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun Library(vm: ReaderViewModel, discover: () -> Unit, browseCreator: (Creator) -> Unit) {
    val manga by vm.library.collectAsStateWithLifecycle()
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    val creators by vm.creators.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("Saved") }
    var query by rememberSaveable { mutableStateOf("") }
    Column {
        val recent = shelf.firstOrNull { it.lastRelease != null }
        val book = manga.find { it.id == recent?.mangaId }
        if (book != null && settings.showContinue && query.isEmpty() && filter == "Saved") {
            Card(
                onClick = { vm.open(book, true) },
                modifier = Modifier.fillMaxWidth().padding(20.dp, 0.dp, 20.dp, 16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        book.cover,
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
                        Text(
                            "Pick up where you left off",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { vm.setting("showContinue", false) }) { Text("Dismiss") }
                }
            }
        }
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            placeholder = { Text("Find something on your shelf") },
            leadingIcon = { Icon(KomoriIcons.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("Saved", "Liked", "History", "Creators").forEach {
                FilterChip(filter == it, { filter = it }, label = { Text(it) })
            }
        }
        if (filter == "Creators") {
            if (creators.isEmpty())
                EmptyState(
                    KomoriIcons.PersonOutline,
                    "Follow the people behind the pages",
                    "Open a title and follow its author or artist. Your follows stay on this device.",
                )
            else
                LazyColumn {
                    items(creators.filter { it.name.contains(query, true) }, key = { it.id }) {
                        creator ->
                        ListItem(
                            headlineContent = { Text(creator.name) },
                            supportingContent = { Text("${creator.role} · Followed locally") },
                            leadingContent = { Icon(KomoriIcons.PersonOutline, null) },
                            trailingContent = {
                                TextButton(onClick = { vm.follow(creator) }) { Text("Unfollow") }
                            },
                            modifier = Modifier.clickable { browseCreator(creator) },
                        )
                    }
                }
        } else {
            val entries =
                shelf.filter {
                    when (filter) {
                        "Liked" -> it.liked
                        "History" -> it.lastRelease != null
                        else -> it.saved
                    }
                }
            val titles =
                entries
                    .mapNotNull { entry -> manga.find { it.id == entry.mangaId } }
                    .filter { it.title.contains(query, true) }
                    .let {
                        if (settings.libraryAlphabetical)
                            it.sortedBy { book -> book.title.lowercase() }
                        else it
                    }
            if (titles.isEmpty())
                EmptyState(
                    KomoriIcons.MenuBook,
                    "A little space for your next story",
                    "Save a title from Discover, or start reading to build your history.",
                    "Explore sources",
                    discover,
                )
            else
                MangaGrid(
                    titles,
                    onOpen = { vm.open(it, settings.direct) },
                    onRead = { vm.open(it, true) },
                    subtitle = { title -> shelf.find { it.mangaId == title.id }?.status ?: "" },
                )
        }
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
    var showFilters by remember { mutableStateOf(false) }
    var query by rememberSaveable(activeSource) { mutableStateOf("") }
    if (showFilters) SourceFilters(vm, activeSource, filter, tags) { showFilters = false }
    LaunchedEffect(Unit) { if (results.isEmpty() && !loading) vm.search("", null) }
    Column {
        ScrollableTabRow(
            selectedTabIndex = sourceOrder(settings.sources).indexOf(activeSource).coerceAtLeast(0),
            edgePadding = 12.dp,
        ) {
            sourceOrder(settings.sources).forEach { key ->
                Tab(
                    selected = activeSource == key,
                    onClick = { vm.chooseSource(key) },
                    text = { Text(sourceNames.getValue(key)) },
                )
            }
        }
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.weight(1f),
                placeholder = { Text("Search manga & manhwa") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
            IconButton(onClick = { vm.search(query, null) }) {
                Icon(KomoriIcons.Search, "Search source")
            }
        }
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (activeSource == "comix") "Integration pending · source access blocked"
                else
                    "${filter.sort.ifEmpty { vm.app.source.sorts(activeSource).firstOrNull().orEmpty() }} · ${filter.tags.size} tags",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
            )
            TextButton(
                enabled = activeSource != "comix",
                onClick = {
                    showFilters = true
                    vm.loadTags()
                },
            ) {
                Text("Filters")
            }
        }
        if (results.isEmpty() && !loading)
            EmptyState(
                KomoriIcons.Search,
                "No titles to show",
                "Try a different search, or check your connection.",
                "Retry",
                { vm.search(query, null) },
            )
        else
            MangaGrid(
                results,
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
                            Text("Load more titles")
                        }
                },
            )
    }
}

@Composable
private fun MangaGrid(
    manga: List<Manga>,
    onOpen: (Manga) -> Unit,
    onRead: (Manga) -> Unit,
    subtitle: (Manga) -> String = { "" },
    footer: @Composable () -> Unit = {},
) {
    LazyVerticalGrid(
        GridCells.Adaptive(140.dp),
        contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items(manga, key = { it.id }) { title ->
            Column {
                Box {
                    AsyncImage(
                        title.cover,
                        title.title,
                        Modifier.fillMaxWidth()
                            .aspectRatio(0.7f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onOpen(title) },
                        contentScale = ContentScale.Crop,
                    )
                    FilledIconButton(
                        onClick = { onRead(title) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(44.dp),
                    ) {
                        Icon(KomoriIcons.PlayArrow, "Read ${title.title}")
                    }
                }
                Text(
                    title.title,
                    Modifier.padding(top = 8.dp).clickable { onOpen(title) },
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

@Composable
private fun Details(vm: ReaderViewModel, manga: Manga, browseCreator: (Creator) -> Unit) {
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
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                AsyncImage(
                    manga.cover,
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
                Button(
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
        items(visibleChapters, key = { it.id }) { chapter ->
            val downloaded = downloads.find { it.releaseId == chapter.id }
            val read = progress.any { it.chapterKey == chapter.key && it.completed }
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (
                            !chapter.local &&
                                !chapter.unavailable &&
                                chapter.externalUrl?.startsWith("https://") == true
                        )
                            uri.openUri(chapter.externalUrl)
                        else vm.read(chapter)
                    }
                    .padding(vertical = 10.dp),
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
                ChapterDownloadControl(vm, chapter, downloaded)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val manga by vm.library.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Download?>(null) }
    if (downloads.isEmpty())
        EmptyState(
            KomoriIcons.Download,
            "Take a few chapters with you",
            "Download from a title's chapter list. Finished downloads remain available offline.",
        )
    else
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                        enabled = downloads.any { it.state in listOf("Paused", "Failed") },
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
                ElevatedCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            manga.find { it.id == download.mangaId }?.title ?: download.label,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "${download.label} · ${download.state}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        LinearProgressIndicator(
                            progress = {
                                if (download.total == 0) 0f
                                else download.done.toFloat() / download.total
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        )
                        Text(
                            "${download.done} / ${download.total} pages",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (download.error.isNotEmpty())
                            Text(
                                download.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        Row {
                            if (download.state in listOf("Paused", "Failed"))
                                TextButton(onClick = { vm.resume(download) }) { Text("Resume") }
                            else if (download.state != "Complete")
                                TextButton(onClick = { vm.pause(download) }) { Text("Pause") }
                            if (download.state == "Complete")
                                TextButton(onClick = { vm.readOffline(download) }) {
                                    Text("Read offline")
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
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
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
        Switch(checked, change)
    }
}

@Composable
private fun SettingsScreen(vm: ReaderViewModel, settings: Settings) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Make room for your reading rhythm",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(12.dp))
        }
        item {
            Text("Sources", style = MaterialTheme.typography.titleLarge)
            Text(
                "Top source opens first in Discover. MangaDex library entries stay available.",
                style = MaterialTheme.typography.bodySmall,
            )
            sourceOrder(settings.sources).forEachIndexed { index, key ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${index + 1}. ${sourceNames.getValue(key)}${if(index == 0) " · Default" else ""}",
                        Modifier.weight(1f),
                    )
                    TextButton(enabled = index > 0, onClick = { vm.moveSource(key, -1) }) {
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
        item { TextButton(onClick = { vm.resetSourceOrder() }) { Text("Reset source order") } }
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
            Text("Retry failed reader images", style = MaterialTheme.typography.titleSmall)
            Text(
                "Retry the failed image up to three times while it is visible.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 5, 10, 30).forEach { seconds ->
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
            Toggle("Smart Chapters", "Collapse matching numbered releases", settings.smart) {
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
                LanguagePicker(settings.language, emptyList(), { languageDialog = false }) {
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
            Toggle("Dark appearance", "A softer shelf for late-night reading", settings.dark) {
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
                if (source == "demonic")
                    Text(
                        "Sort and tags apply to browsing. Title search is separate.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                OutlinedTextField(
                    tagQuery,
                    { tagQuery = it },
                    label = { Text("Find a tag") },
                    singleLine = true,
                )
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(tags.filter { it.name.contains(tagQuery, true) }, key = { it.id }) { tag
                        ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                chosen = if (tag.id in chosen) chosen - tag.id else chosen + tag.id
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    vm.applyFilter(BrowseFilter(sort, chosen))
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
    val candidates = remember(choice, downloads, progress) { vm.downloadCandidates(choice) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Download chapters") },
        text = {
            Column {
                Text(
                    "Uses your language and Smart Chapter preferences. Completed and active downloads are skipped.",
                    style = MaterialTheme.typography.bodySmall,
                )
                DownloadChoice.entries.forEach { value ->
                    Row(
                        Modifier.fillMaxWidth().clickable { choice = value },
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
                    vm.downloadBatch(candidates)
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
        "Failed" ->
            TextButton(onClick = { vm.resume(download) }) {
                Text(if (download.state == "Failed") "Retry" else "Resume")
            }
        else ->
            IconButton(onClick = { vm.download(chapter) }, enabled = chapter.readable) {
                Icon(KomoriIcons.Download, "Download ${chapter.label}")
            }
    }
}
