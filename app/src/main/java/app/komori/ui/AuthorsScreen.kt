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
internal fun Authors(vm: ReaderViewModel) {
    val creators by vm.creators.collectAsStateWithLifecycle()
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    var encodedFilter by rememberSaveable { mutableStateOf(AuthorWorkFilter().encode()) }
    val workFilter = remember(encodedFilter) { AuthorWorkFilter.decode(encodedFilter) }
    var filterOpen by remember { mutableStateOf(false) }
    val catalogCreators = creators.filter { workFilter.sources.isEmpty() || sourceKey(it.id) in workFilter.sources }
    if (filterOpen) AuthorFiltersDialog(workFilter, creators.map { sourceKey(it.id) }.distinct(),
        { encodedFilter = it.encode(); filterOpen = false }, { filterOpen = false })
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var manage by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(AuthorSort.LATEST.name) }
    val sort = AuthorSort.valueOf(sortName)
    var sortMenu by remember { mutableStateOf(false) }
    val selected = catalogCreators.find { it.id == selectedId }
    val visible = creators.filter { it.name.contains(query, true) }
    val creatorSelection = rememberMultiSelection(visible.map { it.id })
    val context = (selected?.let { listOf(it) } ?: catalogCreators).map { it.id }
    val currentFeed by vm.authorFeed.collectAsStateWithLifecycle()
    val feed =
        if (currentFeed.context == context && currentFeed.sort == sort && currentFeed.filter == workFilter) currentFeed
        else AuthorFeed(loading = true)
    LaunchedEffect(catalogCreators, selected, sort, workFilter) {
        vm.showAuthorWorks(selected?.let { listOf(it) } ?: catalogCreators, sort, workFilter)
    }
    val shelfById = remember(shelf) { shelf.associateBy { it.mangaId } }
    val works = feed.items.filter { workFilter.matchesLibrary(shelfById[it.id]) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Following", style = MaterialTheme.typography.titleMedium)
                if (!manage)
                    Text(
                        sort.label + if (workFilter.active) " · Filtered" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            if (!manage && creators.isNotEmpty()) RoundControl(KomoriIcons.FilterList, "Filter author works", { filterOpen = true })
            if (!manage && creators.isNotEmpty()) RefreshControl("Refresh author works", feed.loading, vm::refreshAuthors)
            if (!manage && creators.isNotEmpty())
                Box {
                    RoundControl(KomoriIcons.FilterList, "Sort author works", { sortMenu = true })
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        AuthorSort.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.label) },
                                onClick = {
                                    sortName = order.name
                                    sortMenu = false
                                },
                                trailingIcon = { if (sort == order) Text("✓") },
                            )
                        }
                        Text(
                            "Latest and popularity blend each creator’s source ranking. All orders update as more works load.",
                            Modifier.widthIn(max = 260.dp).padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected == null, { selectedId = null }, { Text("All") }) }
                items(catalogCreators, key = { it.id }) { creator ->
                    FilterChip(
                        selected?.id == creator.id,
                        { selectedId = creator.id },
                        { Text(creator.name) },
                    )
                }
            }
            key(context, sort, workFilter) {
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
                            }
                            if (!feed.loading && feed.hasMore)
                                TextButton(onClick = vm::loadAuthorWorks) {
                                    Text(
                                        if (feed.errors.isEmpty()) "Load more works"
                                        else "Retry / load more"
                                    )
                                }
                            if (!feed.loading && !feed.hasMore && works.isEmpty())
                                Text(if (workFilter.active) "No works match these filters." else "No works found.")
                        }
                    },
                )
            }
        }
    }
}

