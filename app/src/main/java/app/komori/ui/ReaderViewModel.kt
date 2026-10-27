package app.komori.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import androidx.room.withTransaction
import app.komori.KomoriApp
import app.komori.data.*
import app.komori.domain.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class ReaderViewModel(
    application: Application,
    private val authorPage: (suspend (Creator, Int, BrowseFilter) -> BrowsePage)?,
    private val savedState: SavedStateHandle,
    private val pageSource: Sources? = null,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, null, SavedStateHandle())
    constructor(application: Application, savedState: SavedStateHandle) : this(application, null, savedState)
    constructor(application: Application, authorPage: (suspend (Creator, Int, BrowseFilter) -> BrowsePage)?) : this(application, authorPage, SavedStateHandle())

    internal constructor(application: Application, source: Sources) : this(application, null, SavedStateHandle(), source)

    val app = application as KomoriApp
    private val dao = app.dao
    private val downloadCoordinator = DownloadCoordinator(app)
    val shelf = dao.observeShelf().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val library = dao.observeManga().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val creators =
        dao.observeCreators().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val downloads =
        dao.observeDownloads().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val selected = MutableStateFlow<Manga?>(null)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val progress = selected.map { it?.id.orEmpty() }.distinctUntilChanged()
        .flatMapLatest { dao.observeProgressForManga(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val preferences =
        dao.observeTitlePreferences().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings = app.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val migrationLinks = dao.observeMigrationLinks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories = dao.observeCategories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val memberships = dao.observeMemberships().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val libraryCounts = settings.map { it.language }.distinctUntilChanged()
        .flatMapLatest { dao.observeLibraryCounts(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val libraryRefreshing = WorkManager.getInstance(app).getWorkInfosByTagFlow("library-manual-refresh")
        .map { rows -> rows.any { !it.state.isFinished } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val libraryUpdates = dao.observeLibraryUpdates().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val results = MutableStateFlow<List<Manga>>(emptyList())
    val activeSource = MutableStateFlow("")
    val browseFilter = MutableStateFlow(BrowseFilter())
    val tags = MutableStateFlow<List<SourceTag>>(emptyList())
    private var tagsJob: Job? = null
    val chapters = MutableStateFlow<List<Release>>(emptyList())
    val chapterStatus = MutableStateFlow("")
    val sourceLanguages = MutableStateFlow<List<String>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val notice = MutableStateFlow<String?>(null)
    val reader = MutableStateFlow<Release?>(null)
    val readerSession = MutableStateFlow(0)
    val readerGapTarget = MutableStateFlow<Release?>(null)
    val bulkStatus = MutableStateFlow<String?>(null)
    val updatesVisible = MutableStateFlow(false)
    val sourceStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val localFileStatus = MutableStateFlow<String?>(null)
    private var localFileJob: Job? = null
    private var bulkJob: Job? = null

    fun stopAddingDownloads() {
        bulkJob?.cancel()
    }

    val readerPage = MutableStateFlow(0)
    val readerScroll = MutableStateFlow(0f)
    val readerReady = MutableStateFlow(false)
    private val imageFetch = Array(16) { Mutex() }
    private val imageConnections = Semaphore(2)
    val downloadTransfers = DownloadTransfers.progress
    val readerFile = MutableStateFlow<File?>(null)
    val readerError = MutableStateFlow<String?>(null)
    private val pageRetries = PageRetryBudget()
    val readerRetryAt = MutableStateFlow(0L)
    val readerSourceAction = MutableStateFlow(false)
    val readerRetryAllowed = MutableStateFlow(true)
    val pageCount = MutableStateFlow(0)
    var canLoadMore = true
        private set

    private var query = ""
    private var offlineLanguage: String? = null
    private var nextOffset = 1
    private val paginationIds = mutableSetOf<String>()
    private var creatorFilter: Creator? = null
    val activeCreator = MutableStateFlow<Creator?>(null)
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var pageJob: Job? = null
    private val readerCacheOwner = Any()
    private var prefetchJob: Job? = null
    private var prefetchAnchor: Pair<String, Int>? = null
    private val pageManifests = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private val manifestRequests = SharedRequests<List<String>>(viewModelScope)
    private val mutation = Mutex()
    private val readerProgressStore = ReaderProgressStore(dao, mutation)

    val currentTab = MutableStateFlow(0)
    val navigationEntry = MutableStateFlow(0L)
    val canNavigateBack = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")
    private var nextEntry = 0L

    private data class NavigationSnapshot(
        val entry: Long,
        val tab: Int,
        val query: String,
        val source: String,
        val filter: BrowseFilter,
        val creator: Creator?,
        val results: List<Manga>,
        val nextOffset: Int,
        val more: Boolean,
        val tags: List<SourceTag>,
        val title: Manga?,
        val chapters: List<Release>,
        val languages: List<String>,
        val status: String,
        val offlineLanguage: String?,
        val error: String?,
        val paginationIds: Set<String>,
    )

    private val history = ArrayDeque<NavigationSnapshot>()

    private fun pushScreen() {
        history.addLast(
            NavigationSnapshot(
                navigationEntry.value,
                currentTab.value,
                query,
                activeSource.value,
                browseFilter.value,
                creatorFilter,
                results.value,
                nextOffset,
                canLoadMore,
                tags.value,
                selected.value,
                chapters.value,
                sourceLanguages.value,
                chapterStatus.value,
                offlineLanguage,
                error.value,
                paginationIds.toSet(),
            )
        )
        if (history.size > 30) history.removeFirst()
        navigationEntry.value = ++nextEntry
        canNavigateBack.value = true
    }

    val authorFeed = MutableStateFlow(AuthorFeed())
    private var authorContext: List<Creator>? = null
    private var authorCatalog: AuthorCatalog? = null
    private var authorWorkFilter = AuthorWorkFilter()
    private var authorSort = AuthorSort.LATEST
    private var authorJob: Job? = null

    fun refreshAuthors() {
        if (authorFeed.value.loading) return
        val current = authorContext ?: return
        authorContext = null
        app.source.clearCreatorMetadata()
        showAuthorWorks(current, authorSort, authorWorkFilter)
    }

    fun refreshDiscover() { if (!loading.value) search() }
    fun refreshDetails() { if (!loading.value) selected.value?.let { open(it) } }

    val clearingCache = MutableStateFlow(false)
    fun clearReaderCache() {
        if (clearingCache.value || reader.value != null) return
        clearingCache.value = true
        viewModelScope.launch {
            try {
                prefetchJob?.join()
                pageJob?.join()
                val removed = withContext(Dispatchers.IO) {
                    ReaderCacheFiles.clean(File(app.cacheDir, "reader"), images = true)
                }
                notice.value = "Cleared ${storageLabel(removed)} of cached pages. Downloaded chapters were kept."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { notice.value = "Could not clear cached pages. Downloaded chapters were kept." }
            finally { clearingCache.value = false }
        }
    }

    val refreshingReader = MutableStateFlow(false)
    fun refreshReader() {
        if (refreshingReader.value) return
        val current = reader.value ?: return
        val page = readerPage.value
        if (!readerReady.value && pageJob?.isActive == true) return
        refreshingReader.value = true
        // Offline pages remain authoritative. Only transient manifests and online cache are refreshed.
        manifestRequests.invalidate("${current.id}-${settings.value.saver}")
        pageManifests.remove("${current.id}-${settings.value.saver}")
        pageRetries.clear()
        prefetchJob?.cancel()
        prefetchAnchor = null
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    imageFetch[("${current.id}-$page".hashCode() and Int.MAX_VALUE) % imageFetch.size].withLock {
                        File(app.cacheDir, "reader/${current.id}-$page-${settings.value.saver}.img").delete()
                    }
                }
                if (reader.value?.id == current.id) {
                    val saved = dao.download(current.id)
                    val offline = current.local || saved?.state == "Complete" || sourceKey(current.mangaId) == "local"
                    read(if (offline) current else current.copy(pages = -1), requestedPage = page)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { readerError.value = connectionMessage(e) }
            finally { refreshingReader.value = false }
        }
    }

    fun showAuthorWorks(creators: List<Creator>, sort: AuthorSort = AuthorSort.LATEST, workFilter: AuthorWorkFilter = AuthorWorkFilter()) {
        if (authorContext == creators && authorSort == sort && authorWorkFilter == workFilter) return
        val current = authorCatalog
        if (
            authorContext == creators && authorWorkFilter == workFilter &&
                current != null &&
                (authorSort == AuthorSort.POPULAR) == (sort == AuthorSort.POPULAR)
        ) {
            authorSort = sort
            current.reorder(sort)
            authorFeed.value = authorFeed.value.copy(items = current.items, sort = sort)
            return
        }
        authorJob?.cancel()
        authorContext = creators.toList()
        authorSort = sort
        authorWorkFilter = workFilter
        authorCatalog =
            AuthorCatalog(creators, sort, workFilter, enrich = { if (authorPage == null) app.source.details(it) else it }) { creator, page ->
                val key = sourceKey(creator.id)
                require(key in setOf("mangadex", "zap", "nhentai", "imhentai")) {
                    "This source does not provide a creator catalog."
                }
                val filter = authorBrowseFilter(key, sort, workFilter)
                val result = authorPage?.invoke(creator, page, filter)
                    ?: app.source.browse(key, "", page, filter, creator, deferCreatorDetails = true)
                result
            }
        authorFeed.value =
            AuthorFeed(
                hasMore = creators.isNotEmpty(),
                context = creators.map { it.id },
                sort = sort,
                filter = workFilter,
            )
        loadAuthorWorks()
    }

    fun loadAuthorWorks() {
        val catalog = authorCatalog ?: return
        if (authorJob?.isActive == true || !catalog.hasMore) return
        authorJob =
            viewModelScope.launch {
                authorFeed.value = authorFeed.value.copy(loading = true, errors = emptyList())
                val errors = catalog.loadNext()
                if (authorCatalog === catalog) {
                    authorFeed.value =
                        AuthorFeed(
                            catalog.items,
                            false,
                            catalog.hasMore,
                            errors,
                            authorContext.orEmpty().map { it.id },
                            catalog.sort,
                            authorWorkFilter,
                            catalog.incompleteMetadataCount,
                        )
                }
            }
    }

    fun retryAuthorMetadata() {
        val catalog = authorCatalog ?: return
        if (authorJob?.isActive == true) return
        authorJob = viewModelScope.launch {
            authorFeed.value = authorFeed.value.copy(loading = true)
            catalog.retryMetadata()
            if (authorCatalog === catalog) authorFeed.value = authorFeed.value.copy(
                items = catalog.items, loading = false, incompleteMetadata = catalog.incompleteMetadataCount)
        }
    }

    fun browseCreator(creator: Creator) {
        pushScreen()
        detailJob?.cancel()
        selected.value = null
        chapters.value = emptyList()
        currentTab.value = 1
        browseFilter.value = BrowseFilter()
        search("", creator)
    }

    private fun task(block: suspend () -> Unit) =
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = connectionMessage(e)
            }
        }

    fun search(text: String = query, creator: Creator? = creatorFilter, more: Boolean = false) {
        if (more && (searchJob?.isActive == true || !canLoadMore)) return
        searchJob?.cancel()
        if (!more) searchQuery.value = text
        searchJob = task {
            loading.value = true
            error.value = null
            try {
                if (!more) {
                    results.value = emptyList()
                    nextOffset = 1
                    paginationIds.clear()
                    canLoadMore = true
                    query = text
                    if (creator != null && creator.id != creatorFilter?.id)
                        browseFilter.value =
                            browseFilter.value.copy(facetPath = "", tags = emptySet())
                    creatorFilter = creator
                    activeCreator.value = creator
                }
                if (creatorFilter != null) {
                    val creatorSource = sourceKey(creatorFilter!!.id)
                    if (activeSource.value != creatorSource) {
                        browseFilter.value = BrowseFilter()
                        tagsJob?.cancel()
                        tags.value = emptyList()
                    }
                    activeSource.value = creatorSource
                }
                if (activeSource.value.isEmpty())
                    activeSource.value = enabledSourceOrder(app.settings.flow.first()).firstOrNull()
                        ?: throw IOException("Enable a source in Settings to browse titles.")
                browseFilter.value = browseFilter.value.copy(sort = contextualSearchSort(activeSource.value, query, browseFilter.value.sort))
                val foundPage =
                    app.source.browse(
                        activeSource.value,
                        query,
                        nextOffset,
                        browseFilter.value,
                        creatorFilter,
                    )
                val found = foundPage.titles
                nextOffset += 1
                val previous = if (more) results.value else emptyList()
                val merged = (previous + found).distinctBy { it.id }
                val beforePagination = paginationIds.size
                paginationIds.addAll(foundPage.paginationIds)
                canLoadMore = PaginationPolicy.hasNext(foundPage.hasMore, beforePagination, paginationIds.size)
                results.value = merged
            } finally {
                loading.value = false
            }
        }
    }

    fun submitSearch(text: String) {
        pushScreen()
        if (text != query)
            browseFilter.value = BrowseFilter(searchType = browseFilter.value.searchType,
                language = browseFilter.value.language, category = browseFilter.value.category)
        else browseFilter.value = browseFilter.value.copy(facetPath = "")
        search(text, null)
    }

    fun setSearchType(type: String) {
        require(type in sourceSearchTypes(activeSource.value))
        creatorFilter = null
        activeCreator.value = null
        browseFilter.value =
            browseFilter.value.copy(searchType = type, facetPath = "", tags = emptySet(),
                language = if (activeSource.value == "nhentai" || type == "Title") browseFilter.value.language else "",
                category = if (activeSource.value == "nhentai" || type == "Title") browseFilter.value.category else "",
                sort = browseFilter.value.sort.takeIf {
                    it in app.source.sorts(activeSource.value, type != "Title")
                }.orEmpty())
    }

    fun clearFacet() {
        pushScreen()
        browseFilter.value = BrowseFilter()
        search("", null)
    }

    fun retrySource(key: String) {
        val release = reader.value
        val title = selected.value
        if (release != null && sourceKey(release.mangaId) == key) read(release)
        else if (title != null && sourceKey(title.id) == key) open(title)
        else if (activeSource.value == key) search() else chooseSource(key)
    }

    fun browseTag(manga: Manga, tag: SourceTag) {
        pushScreen()
        detailJob?.cancel()
        selected.value = null
        chapters.value = emptyList()
        currentTab.value = 1
        activeSource.value = sourceKey(manga.id)
        tagsJob?.cancel()
        tags.value = emptyList()
        browseFilter.value = if (sourceKey(manga.id) in gallerySources) BrowseFilter(facetPath = tag.id)
            else BrowseFilter(tags = setOf(tag.id))
        search("", null)
    }

    fun chooseSource(key: String) {
        if (activeSource.value == key) return
        pushScreen()
        activeSource.value = key
        browseFilter.value = BrowseFilter()
        tags.value = emptyList()
        tagsJob?.cancel()
        search("", null)
    }

    fun loadTags() {
        tagsJob?.cancel()
        val key = activeSource.value
        tagsJob = task { tags.value = app.source.tags(key) }
    }

    fun applyFilter(filter: BrowseFilter) {
        pushScreen()
        browseFilter.value = filter
        search()
    }

    fun moveSource(key: String, delta: Int) = task {
        val order = sourceOrder(app.settings.flow.first().sources).toMutableList()
        val index = order.indexOf(key)
        val target = (index + delta).coerceIn(0, order.lastIndex)
        order.removeAt(index)
        order.add(target, key)
        app.settings.sources(order.joinToString(","))
    }

    fun readOffline(
        download: Download,
        startReading: Boolean = true,
        chooseStart: Boolean = false,
    ) {
        detailJob?.cancel()
        detailJob = task {
            val checked =
                withContext(Dispatchers.IO) {
                    val current = dao.download(download.releaseId)
                    if (current?.state in DownloadPolicy.activeStates) current
                    else
                        ChapterFiles.locked(download.releaseId) {
                            if (
                                dao.download(download.releaseId)?.state !in
                                    DownloadPolicy.activeStates
                            )
                                reconcileDownload(app, download.releaseId)
                            dao.download(download.releaseId)
                        }
                } ?: return@task
            if (checked.done == 0) {
                notice.value = "No intact saved pages remain. Choose Repair to download them again."
                return@task
            }
            val book = dao.manga(download.mangaId) ?: return@task
            val rows = dao.chapters(book.id).map { it.release() }
            val complete =
                dao.observeDownloads()
                    .first()
                    .filter { it.done > 0 }
                    .associate { it.releaseId to it.total }
            if (selected.value?.id != book.id) pushScreen()
            selected.value = book
            chapters.value =
                retainDownloadedChapters(
                    emptyList(),
                    rows,
                    if (checked.done > 0) complete + (checked.releaseId to checked.total)
                    else complete,
                )
            val release =
                if (chooseStart) {
                    val ordered =
                        ChapterPolicy.select(
                            chapters.value,
                            ChapterPreferences("*", settings.value.smart),
                        )
                    ChapterPolicy.start(
                        ordered,
                        dao.shelf(book.id)?.lastRelease,
                        dao.observeProgressForManga(book.id)
                            .first()
                            .filter { it.completed }
                            .map { it.chapterKey }
                            .toSet(),
                        settings.value.reread,
                    ) ?: ordered.firstOrNull()
                } else chapters.value.find { it.id == download.releaseId }
            if (release == null) return@task
            offlineLanguage = release.language
            chapterStatus.value = "Saved chapter list · Offline"
            if (startReading) read(release)
        }
    }

    fun open(manga: Manga, immediately: Boolean = false) {
        if (selected.value?.id != manga.id) pushScreen()
        searchJob?.cancel()
        offlineLanguage = null
        detailJob?.cancel()
        selected.value = manga
        chapters.value = emptyList()
        error.value = null
        sourceLanguages.value = emptyList()
        chapterStatus.value = "Loading complete chapter list…"
        detailJob = task {
            loading.value = true
            try {
                val language = sourceReadingLanguage(manga.id, app.settings.flow.first().language, dao.titlePreferences(manga.id)?.language.orEmpty())
                dao.markUpdatesSeen(manga.id)
                val stored = dao.manga(manga.id)
                val baseManga = stored ?: manga
                selected.value = baseManga
                dao.putManga(baseManga)
                val snapshot = dao.feed(manga.id, language)
                val cachedRows = dao.chapters(manga.id)
                val snapshotIds =
                    snapshot?.let {
                        val ids = org.json.JSONArray(it.releaseIds)
                        (0 until ids.length()).map { i -> ids.getString(i) }.toSet()
                    }
                val completedDownloads =
                    dao.observeDownloads()
                        .first()
                        .filter { it.mangaId == manga.id && it.state == "Complete" }
                        .associate { it.releaseId to it.total }
                val localChapters =
                    cachedRows
                        .map { it.release() }
                        .filter { language == "*" || it.language == language }
                val cached =
                    retainDownloadedChapters(
                        cachedRows
                            .filter { snapshotIds == null || it.id in snapshotIds }
                            .map { it.release() }
                            .filter { language == "*" || it.language == language },
                        localChapters,
                        completedDownloads,
                    )
                chapters.value = cached
                if (sourceKey(manga.id) == "local") {
                    chapterStatus.value = "Imported comic · Stored on this device"
                    sourceLanguages.value = listOf("en")
                    if (immediately) startReading()
                    return@task
                }
                // Only resume a known complete snapshot early; a new start waits for
                // the full feed so a partial legacy cache cannot choose a late chapter.
                val resumed =
                    immediately &&
                        snapshot != null &&
                        cached.isNotEmpty() &&
                        dao.shelf(manga.id)?.lastRelease != null
                if (resumed) startReading()
                try {
                    val fresh = app.source.chapters(manga.id, language)
                    dao.saveFeed(manga.id, language, fresh)
                    chapters.value =
                        retainDownloadedChapters(
                            fresh.map { it.release() },
                            localChapters,
                            completedDownloads,
                        )
                    chapterStatus.value =
                        "${fresh.size} releases returned by ${sourceName(manga.id)} · Updated now"
                } catch (e: IOException) {
                    chapterStatus.value =
                        if (snapshot != null) "Saved chapter list · Refresh failed"
                        else "Refresh failed · Cached list may be incomplete"
                    if (cached.isEmpty()) throw e
                    notice.value = "Using saved chapters. ${connectionMessage(e)}"
                }
                if (immediately && !resumed) startReading()
                // Metadata language hints can be stale; never treat them as proof that
                // a readable chapter exists. Failure here must not discard a good feed.
                try {
                    sourceLanguages.value = app.source.languages(manga.id)
                    val details = app.source.details(baseManga)
                    dao.putManga(details)
                    selected.value = details
                } catch (e: IOException) {
                    notice.value = "Metadata refresh failed. ${connectionMessage(e)}"
                }
            } finally {
                loading.value = false
            }
        }
    }

    fun back() {
        detailJob?.cancel()
        searchJob?.cancel()
        tagsJob?.cancel()
        loading.value = false
        val previous = history.removeLastOrNull()
        if (previous == null) {
            selected.value = null
            chapters.value = emptyList()
            error.value = null
        } else {
            currentTab.value = previous.tab
            query = previous.query
            searchQuery.value = previous.query
            activeSource.value = previous.source
            browseFilter.value = previous.filter
            creatorFilter = previous.creator
            activeCreator.value = previous.creator
            results.value = previous.results
            nextOffset = previous.nextOffset
            paginationIds.clear()
            paginationIds.addAll(previous.paginationIds)
            canLoadMore = previous.more
            tags.value = previous.tags
            selected.value = previous.title
            chapters.value = previous.chapters
            sourceLanguages.value = previous.languages
            chapterStatus.value = previous.status
            offlineLanguage = previous.offlineLanguage
            error.value = previous.error
            navigationEntry.value = previous.entry
        }
        canNavigateBack.value = history.isNotEmpty()
    }

    fun chapterLanguage(): String =
        offlineLanguage
            ?: sourceReadingLanguage(selected.value?.id.orEmpty(), settings.value.language,
                preferences.value.find { it.mangaId == selected.value?.id }?.language.orEmpty())

    fun selectedChapters(): List<Release> {
        val pref = preferences.value.find { it.mangaId == selected.value?.id }
        val current = shelf.value.find { it.mangaId == selected.value?.id }?.lastRelease
        return ChapterPolicy.select(
            chapters.value,
            ChapterPreferences(
                chapterLanguage(),
                settings.value.smart,
                pref?.groupId ?: "",
                pref?.strict ?: false,
                settings.value.showUnavailable,
            ),
            current,
        )
    }

    fun batchTitles(titles: List<Manga>, action: String) = task {
        mutation.withLock {
            for (title in titles.distinctBy { it.id }) {
                dao.putManga(title)
                val old = dao.shelf(title.id) ?: Shelf(title.id)
                if (action == "Mark unread") dao.resetProgress(title.id)
                if (action == "Mark all read") {
                    val cached = dao.chapters(title.id).ifEmpty {
                        val language = sourceReadingLanguage(title.id, settings.value.language, dao.titlePreferences(title.id)?.language.orEmpty())
                        app.source.chapters(title.id, language).also {
                            dao.saveFeed(title.id, language, it)
                        }
                    }
                    cached.map { it.release() }.filter { it.readable }.forEach {
                        val position = dao.progress(it.id)
                        dao.putProgress(Progress(it.id, it.mangaId, it.key, position?.page ?: 0, true, position?.scroll ?: 0f))
                    }
                }
                dao.putShelf(
                    when (action) {
                        "Save" -> old.copy(saved = true)
                        "Remove from Library" -> old.copy(saved = false)
                        "Like" -> old.copy(liked = true)
                        "Unlike" -> old.copy(liked = false)
                        "Mark completed" -> old.copy(saved = true, status = "Completed")
                        "Mark all read" -> old
                        "Mark unread" -> old.copy(lastRelease = null, status = if (old.status == "Completed") "Reading" else old.status)
                        else -> throw IllegalArgumentException("Unknown title action")
                    }
                )
            }
        }
        notice.value = "$action · ${titles.size} titles"
    }

    fun batchCreators(ids: List<String>) = task {
        for (id in ids.distinct()) dao.removeCreator(id)
        notice.value = "Unfollowed ${ids.size} creators"
    }

    fun batchChapters(releases: List<Release>, completed: Boolean) = task {
        mutation.withLock {
            if (!completed) {
                val keys = releases.map { it.key }.toSet()
                for (old in keys.toList().chunked(400).flatMap { dao.progressForKeys(it) }) dao
                    .putProgress(old.copy(completed = false, page = 0, scroll = 0f))
            }
            for (release in releases.distinctBy { it.id }) {
                val old = dao.progress(release.id)
                dao.putProgress(
                    Progress(
                        release.id,
                        release.mangaId,
                        release.key,
                        if (completed) old?.page ?: 0 else 0,
                        completed,
                        if (completed) old?.scroll ?: 0f else 0f,
                    )
                )
            }
        }
    }

    fun batchDownloads(ids: List<String>, action: String) = task {
        for (id in ids.distinct()) dao.download(id)?.let {
            when (action) {
                "Pause" -> pause(it).join()
                "Resume" -> resume(it).join()
                "Cancel" -> cancelDownload(it).join()
                "Delete downloads" -> deleteDownload(it).join()
            }
        }
    }

    fun downloadTitles(titles: List<Manga>, unread: Boolean) {
        if (bulkStatus.value != null) return
        bulkStatus.value = "Preparing selected titles…"
        bulkJob = task {
            var failed = 0
            try {
                val options = app.settings.flow.first()
                for ((index, title) in titles.distinctBy { it.id }.withIndex()) {
                    bulkStatus.value = "Preparing ${index + 1}/${titles.size}: ${title.title}"
                    try {
                        dao.putManga(title)
                        val language = sourceReadingLanguage(title.id, options.language, dao.titlePreferences(title.id)?.language.orEmpty())
                        val rows = app.source.chapters(title.id, language)
                        dao.saveFeed(title.id, language, rows)
                        val preference = dao.titlePreferences(title.id)
                        val releases =
                            ChapterPolicy.select(
                                rows.map { it.release() },
                                ChapterPreferences(
                                    language,
                                    options.smart,
                                    preference?.groupId ?: "",
                                    preference?.strict ?: false,
                                    options.showUnavailable,
                                ),
                            )
                        val read =
                            dao.observeProgressForManga(title.id).first().filter { it.completed }.map { it.chapterKey }.toSet()
                        for (release in
                            releases.filter {
                                it.readable && (!unread || it.key !in read)
                            }) enqueueDownload(app, release, options)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed++
                        error.value = "${title.title}: ${connectionMessage(e)}"
                    }
                }
                notice.value =
                    if (failed == 0) "Selected titles queued"
                    else "$failed titles could not be queued. Other titles were processed."
            } finally {
                bulkStatus.value = null
            }
        }
    }

    fun toggle(manga: Manga, liked: Boolean = false) = task {
        mutation.withLock {
            dao.putManga(manga)
            val old = dao.shelf(manga.id) ?: Shelf(manga.id)
            dao.putShelf(if (liked) old.copy(liked = !old.liked) else old.copy(saved = !old.saved))
        }
    }

    fun status(manga: Manga, value: String) = task {
        mutation.withLock {
            val old = dao.shelf(manga.id) ?: Shelf(manga.id)
            dao.putShelf(old.copy(saved = true, status = value))
        }
    }

    fun follow(creator: Creator) = task {
        if (creators.value.any { it.id == creator.id }) dao.removeCreator(creator.id)
        else dao.putCreator(creator)
    }

    fun saveCategory(name: String, id: String? = null) = task {
        val clean = name.trim()
        require(clean.isNotEmpty() && clean.length <= 100) { "Category names must contain 1–100 characters." }
        mutation.withLock {
            val existing = dao.backupCategories()
            require(existing.none { it.id != id && it.name.equals(clean, true) }) { "A category with this name already exists." }
            require(id != null || existing.size < 1000) { "The category limit is 1,000." }
            dao.putCategory(LibraryCategory(id ?: java.util.UUID.randomUUID().toString(), clean))
        }
    }

    fun deleteCategory(id: String) = task { dao.deleteCategory(id) }

    fun changeCategories(ids: List<String>, changes: Map<String, Boolean>) = task {
        mutation.withLock {
            val known = dao.backupCategories().map { it.id }.toSet()
            for (id in ids.distinct()) for ((category, add) in changes) {
                require(category in known) { "This category no longer exists." }
                if (add) dao.putMembership(CategoryMembership(id, category)) else dao.removeMembership(id, category)
            }
        }
    }

    fun titleLanguage(value: String) = task {
        val id = selected.value?.id ?: return@task
        require(value.isEmpty() || value == "*" || value.matches(Regex("[a-z]{2,3}(?:-[A-Za-z]{2,8})*"))) { "Invalid language." }
        mutation.withLock {
            dao.putTitlePreferences((dao.titlePreferences(id) ?: TitlePreferences(id)).copy(language = value))
        }
        selected.value?.let { open(it) }
    }

    fun group(id: String, strict: Boolean = false) {
        val mangaId = selected.value?.id ?: return
        task {
            mutation.withLock {
                val old = dao.titlePreferences(mangaId) ?: TitlePreferences(mangaId)
                dao.putTitlePreferences(old.copy(groupId = id, strict = strict))
            }
        }
    }

    fun titleReaderMode(mode: String) {
        val mangaId = reader.value?.mangaId ?: return
        task {
            mutation.withLock {
                val old = dao.titlePreferences(mangaId) ?: TitlePreferences(mangaId)
                dao.putTitlePreferences(old.copy(readerMode = mode))
            }
        }
    }

    fun setting(key: String, value: Boolean) = task {
        app.settings.set(key, value)
        if (key == "checkWifi") LibraryCheckScheduler.schedule(app, app.settings.flow.first())
        if (key == "wifi") {
            // WorkManager constraints are immutable; apply the new choice to the existing queue.
            val queued = downloads.value.filter { it.state in DownloadPolicy.activeStates }
            for (download in queued) {
                pause(download).join()
                dao.chapter(download.releaseId)?.release()?.let {
                    enqueueDownload(app, it, settings.value.copy(wifi = value))
                }
            }
        }
    }

    fun language(value: String) = task {
        app.settings.language(value)
        settings.first { it.language == value }
        selected.value?.let { open(it) }
    }

    fun startReading(fromBeginning: Boolean = false) = task {
        val available = selectedChapters()
        val last = shelf.value.find { it.mangaId == selected.value?.id }?.lastRelease
        val completed = dao.observeProgressForManga(selected.value?.id.orEmpty()).first()
            .filter { it.completed }.map { it.chapterKey }.toSet()
        val next =
            if (fromBeginning) available.firstOrNull { it.readable }
            else ChapterPolicy.start(available, last, completed, reread = true)
        if (next == null) {
            notice.value =
                if (available.isEmpty()) "No chapters match your language and group preferences."
                else "You're caught up. Use Read again to start at the beginning."
            return@task
        }
        read(next, restart = fromBeginning || next.key in completed)
    }

    fun read(chapter: Release, restart: Boolean = false, preserveRetries: Boolean = false, requestedPage: Int? = null) {
        if (!chapter.readable) {
            notice.value =
                if (chapter.unavailable)
                    "MangaDex marks this chapter unavailable. Its pages cannot be fetched."
                else
                    "This chapter has no MangaDex pages. Open its external link from the chapter row if available."
            return
        }
        if (offlineLanguage != null) offlineLanguage = chapter.language
        prefetchJob?.cancel()
        prefetchAnchor = null
        pageJob?.cancel()
        readerReady.value = false
        if (!preserveRetries) pageRetries.clear()
        readerGapTarget.value = null
        readerSession.value += 1
        reader.value = chapter
        readerFile.value = null
        readerError.value = null
        readerRetryAllowed.value = true
        readerRetryAt.value = 0L
        readerSourceAction.value = false
        pageCount.value = chapter.pages
        pageJob =
            viewModelScope.launch {
                try {
                    val resolved = resolveChapter(chapter)
                    reader.value = resolved
                    pageCount.value = resolved.pages
                    val previous = dao.progress(chapter.id)
                    val page =
                        requestedPage ?: if (restart || !settings.value.resume || previous?.completed == true) 0
                        else previous?.page ?: 0
                    readerPage.value = page.coerceIn(0, (resolved.pages - 1).coerceAtLeast(0))
                    pinReaderPages(readerCacheOwner, listOf(resolved to readerPage.value))
                    readerScroll.value =
                        if (
                            page == previous?.page &&
                                !restart &&
                                settings.value.resume &&
                                !previous.completed
                        )
                            previous.scroll
                        else 0f
                    savePage(chapter, readerPage.value, scroll = readerScroll.value)
                    readerReady.value = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    readerRetryAllowed.value = automaticRetryAllowed(e)
                    readerRetryAt.value = retryAtMillis(e)
                    readerSourceAction.value = requiresSourceAction(e)
                    readerError.value = connectionMessage(e)
                }
            }
    }

    private fun rememberPages(key: String, urls: List<String>) {
        pageManifests[key] = urls
        val active = reader.value?.id
        pageManifests.keys
            .filter { it != key && (active == null || !it.startsWith("$active-")) }
            .take((pageManifests.size - 6).coerceAtLeast(0))
            .forEach { pageManifests.remove(it) }
    }

    private suspend fun manifestFor(id: String, saver: Boolean): List<String> {
        val key = "$id-$saver"
        return pageManifests[key] ?: manifestRequests.get(key) {
            pageManifests[key] ?: (pageSource ?: app.source).pages(id, saver).also {
                if (it.isEmpty()) throw ContentFailure("This chapter has no available pages.")
                rememberPages(key, it)
            }
        }
    }

    suspend fun resolveChapter(chapter: Release, speculative: Boolean = false): Release {
        if (chapter.pages >= 0) return chapter
        val saver = settings.value.saver
        val key = "manifest-${chapter.id}-$saver"
        if (!speculative) pageRetries.check(key)
        try { return chapter.copy(pages = manifestFor(chapter.id, saver).size) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw if (speculative) e else pageRetries.failed(key, e) }
    }

    fun resetPageRetries(chapter: Release, page: Int) {
        pageRetries.reset("${chapter.id}-$page-${settings.value.saver}")
        resetChapterRetries(chapter)
    }

    fun resetChapterRetries(chapter: Release) {
        pageRetries.reset("manifest-${chapter.id}-${settings.value.saver}")
    }

    fun previousChapter(chapter: Release): Release? = ReaderContinuity.previous(selectedChapters(), chapter)

    fun jumpTo(chapter: Release, page: Int = 0) = read(chapter, requestedPage = page.coerceAtLeast(0))

    fun previousChapter() {
        val current = reader.value ?: return
        previousChapter(current)?.let { jumpTo(it, if (it.pages < 0) Int.MAX_VALUE else (it.pages - 1).coerceAtLeast(0)) }
    }

    fun numberSetting(key: String, value: Int) = task {
        app.settings.number(key, value)
        if (key == "libraryCheckHours") LibraryCheckScheduler.schedule(app, app.settings.flow.first())
    }
    fun checkLibraryNow() = task { LibraryCheckScheduler.now(app, app.settings.flow.first()); notice.value = "Library refresh queued. Your chapter-check Wi-Fi and battery constraints still apply." }
    fun markUpdatesSeen(id: String) = task { dao.markUpdatesSeen(id) }
    fun markAllUpdatesSeen() = task { dao.markAllUpdatesSeen() }
    fun openLibraryUpdate(update: LibraryUpdate) = task {
        val title = dao.manga(update.mangaId) ?: return@task
        updatesVisible.value = false
        open(title)
        if (update.kind == "chapter") dao.chapter(update.releaseId)?.release()?.let { read(it) }
    }
    fun enableSource(key: String, enabled: Boolean) = task { app.settings.enableSource(key, enabled) }
    fun choiceSetting(key: String, value: String) = task { app.settings.choice(key, value) }

    fun nextChapter(chapter: Release): Release? =
        selectedChapters()
            .dropWhile { it.id != chapter.id }
            .drop(1)
            .firstOrNull { it.key != chapter.key && it.readable }

    fun scrollChapterPosition(chapter: Release, page: Int, fraction: Float, session: Int, flush: Boolean = false) {
        if (session != readerSession.value) return
        val previous = reader.value
        if (previous == null) {
            if (flush) task { savePage(chapter, page, scroll = fraction.coerceIn(0f, 1f), preserveCompleted = true, flush = true) }
            return
        }
        val oldPage = readerPage.value
        val completed = ChapterPolicy.completedBetween(selectedChapters(), previous.id, chapter.id)
        if (previous.id != chapter.id) {
            reader.value = chapter
            pageCount.value = chapter.pages
            readerFile.value = null
            readerError.value = null
            readerRetryAllowed.value = true
            readerRetryAt.value = 0L
            readerSourceAction.value = false
        }
        readerPage.value = page
        pinReaderPages(readerCacheOwner, listOf(chapter to page))
        readerScroll.value = fraction.coerceIn(0f, 1f)
        if (prefetchAnchor != (chapter.id to page)) prefetchAfter(chapter, page)
        task {
            for (passed in completed) savePage(
                passed,
                if (passed.id == previous.id) oldPage else (passed.pages - 1).coerceAtLeast(0),
                true,
            )
            savePage(chapter, page, scroll = fraction.coerceIn(0f, 1f), preserveCompleted = true, flush = flush)
        }
    }

    suspend fun invalidateCachedPage(chapter: Release, page: Int) =
        withContext(Dispatchers.IO) {
            File(app.cacheDir, "reader/${chapter.id}-$page-${settings.value.saver}.img").delete()
            if (dao.download(chapter.id)?.state in DownloadPolicy.activeStates)
                throw IOException("Pause this chapter's download before repairing a page.")
            ChapterFiles.locked(chapter.id) {
                ChapterFiles.invalidate(chapter.id)
                markPageForRepair(app, chapter.id, page)
            }
            pageManifests.remove("${chapter.id}-${settings.value.saver}")
            Unit
        }

    fun retryPage(manual: Boolean = true) {
        val chapter = reader.value ?: return
        val index = readerPage.value
        if (manual) resetPageRetries(chapter, index)
        task {
            invalidateCachedPage(chapter, index)
            if (reader.value?.id == chapter.id && readerPage.value == index) {
                if (readerReady.value) page(index) else read(chapter, preserveRetries = true)
            }
        }
    }

    private fun fileTask(block: suspend () -> Unit) {
        if (localFileJob?.isActive == true) { notice.value = "A file operation is already running."; return }
        localFileJob = viewModelScope.launch {
            localFileStatus.value = "Preparing files…"
            try { block(); notice.value = "File operation completed" }
            catch (e: CancellationException) { notice.value = "File operation stopped. An export destination may be incomplete."; throw e }
            catch (e: Exception) { error.value = connectionMessage(e) + " An export destination may be incomplete." }
            finally { localFileStatus.value = null }
        }
    }
    fun importComic(uri: android.net.Uri, folder: Boolean) = fileTask {
        if (folder) LocalFiles.importFolder(app, uri) { localFileStatus.value = it }
        else LocalFiles.importArchive(app, uri) { localFileStatus.value = it }
        currentTab.value = 0
    }
    fun exportComic(download: Download, uri: android.net.Uri? = null) = fileTask {
        val tree = app.settings.flow.first().exportFolder.takeIf { it.isNotEmpty() }?.let(android.net.Uri::parse)
        LocalFiles.export(app, download, uri, tree) { localFileStatus.value = it }
    }
    fun exportFolder(uri: android.net.Uri?) = task { app.settings.exportFolder(uri?.toString().orEmpty()) }
    fun cancelFileOperation() { localFileJob?.cancel() }

    fun saveCurrentSearch(name: String) = task {
        require(activeSource.value in sourceNames) { "Choose a source first." }
        app.settings.saveSearch(SavedSearch(java.util.UUID.randomUUID().toString(), name.trim(),
            NavigationState(tab = 1, source = activeSource.value, query = query, filter = browseFilter.value, creator = creatorFilter)))
        notice.value = "Search saved"
    }
    fun removeSavedSearch(id: String) = task { app.settings.removeSearch(id) }
    fun restoreSearch(saved: SavedSearch) = task {
        require(saved.navigation.source in enabledSourceOrder(app.settings.flow.first())) { "Enable this source in Settings before opening its saved search." }
        pushScreen()
        selected.value = null
        activeSource.value = saved.navigation.source
        browseFilter.value = saved.navigation.filter
        creatorFilter = saved.navigation.creator
        search(saved.navigation.query, saved.navigation.creator)
        currentTab.value = 1
    }
    fun testSource(key: String) = task {
        sourceStatus.value = sourceStatus.value + (key to "Checking native access…")
        try {
            val page = app.source.browse(key, "", 1, BrowseFilter(), null)
            sourceStatus.value = sourceStatus.value + (key to if (page.titles.isEmpty()) "Responded, but no titles were parsed. Access is not confirmed." else "Native catalog available · ${page.titles.size} titles returned")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { sourceStatus.value = sourceStatus.value + (key to connectionMessage(e)) }
    }

    fun resetSourceOrder() = task { app.settings.sources(sourceNames.keys.joinToString(",")) }

    fun retryInterval(seconds: Int) = task { app.settings.retrySeconds(seconds) }

    fun downloadCandidates(choice: DownloadChoice, first: Int = 1, last: Int = Int.MAX_VALUE): List<Release> {
        val existing = downloads.value.associate { it.releaseId to it.state }
        return if (choice == DownloadChoice.RANGE) DownloadPolicy.range(selectedChapters(), existing, first, last)
        else DownloadPolicy.select(selectedChapters(), progress.value.filter { it.completed }.map { it.chapterKey }.toSet(), existing, choice)
    }

    fun downloadBatch(releases: List<Release>) {
        if (bulkStatus.value != null) return
        bulkStatus.value = "Preparing download queue…"
        bulkJob = task {
            try {
                val options = app.settings.flow.first()
                var queued = 0
                for (chapter in releases.distinctBy { it.id }) {
                    currentCoroutineContext().ensureActive()
                    val existing = dao.download(chapter.id)
                    if (
                        chapter.readable &&
                            existing?.state !in DownloadPolicy.activeStates + "Complete"
                    ) {
                        enqueueDownload(app, chapter, options)
                        queued++
                    }
                    bulkStatus.value = "Queued $queued / ${releases.size} chapters"
                }
                notice.value =
                    "$queued chapters queued. " +
                        if (options.wifi) "Downloads use an unmetered connection."
                        else "Downloads use your current connection."
            } finally {
                bulkStatus.value = null
            }
        }
    }

    fun pauseAll() = task {
        for (download in downloads.value.filter { it.state in DownloadPolicy.activeStates }) pause(
                download
            )
            .join()
    }

    fun resumeAll() = task {
        for (download in
            downloads.value.filter {
                it.state in listOf("Paused", "Failed", "Verification required", "Needs repair")
            }) resume(download).join()
    }

    private suspend fun savePage(
        chapter: Release,
        page: Int,
        completed: Boolean = false,
        scroll: Float = 0f,
        preserveCompleted: Boolean = false,
        flush: Boolean = false,
    ) {
        readerProgressStore.save(chapter, page, completed, scroll, preserveCompleted, flush)
    }

    fun pinReaderPages(owner: Any, pages: List<Pair<Release, Int>>) {
        if (reader.value == null) { ReaderCacheFiles.release(owner); ChapterFiles.releaseReader(owner); return }
        ChapterFiles.reading(owner, pages.map { it.first.id }, readerCacheOwner)
        ReaderCacheFiles.pin(owner, pages.flatMap { (chapter, page) ->
            listOf(true, false).map { saver -> File(app.cacheDir, "reader/${chapter.id}-$page-$saver.img") }
        }, readerCacheOwner)
    }

    fun releaseReaderPages(owner: Any) { ReaderCacheFiles.release(owner); ChapterFiles.releaseReader(owner) }

    suspend fun pageFileFor(chapter: Release, page: Int, speculative: Boolean = false): File {
        val saver = settings.value.saver
        // Disk hits must never queue behind an unrelated slow network request.
        val cached =
            withContext(Dispatchers.IO) {
                if (repairMarker(app, chapter.id, page).exists()) null
                else {
                    val offline = pageFile(app, chapter.id, page)
                    val online =
                        File(app.cacheDir, "reader/${chapter.id}-$page-${saver}.img")
                    when {
                        validImage(offline) -> offline
                        validImage(online) ->
                            online.also { ReaderCacheAccess.touch(it) }
                        else -> null
                    }
                }
            }
        if (cached != null) return cached
        val key = "${chapter.id}-$page"
        return imageFetch[(key.hashCode() and Int.MAX_VALUE) % imageFetch.size].withLock {
            withContext(Dispatchers.IO) {
                val downloaded = pageFile(app, chapter.id, page)
                if (!repairMarker(app, chapter.id, page).exists() && validImage(downloaded)) {
                    return@withContext downloaded
                }
                val saved = dao.download(chapter.id)
                if (saved != null && saved.state !in DownloadPolicy.activeStates)
                    ChapterFiles.locked(chapter.id) {
                        if (dao.download(chapter.id)?.state !in DownloadPolicy.activeStates)
                            markPageForRepair(app, chapter.id, page)
                    }
                val repairVersion = ChapterFiles.version(chapter.id)
                if (sourceKey(chapter.mangaId) == "local") throw ContentFailure("This local page is unavailable. Import the comic again to restore its files.")
                val cacheDir = File(app.cacheDir, "reader").apply { mkdirs() }
                val target = File(cacheDir, "${chapter.id}-$page-${saver}.img")
                if (validImage(target) && !repairMarker(app, chapter.id, page).exists()) {
                    ReaderCacheAccess.touch(target)
                    return@withContext target
                }
                val retryKey = "$key-${saver}"
                if (!speculative) pageRetries.check(retryKey)
                try {
                    val pageUrls = manifestFor(chapter.id, saver)
                    if (reader.value?.id == chapter.id) pageCount.value = pageUrls.size
                    val url =
                        pageUrls.getOrNull(page)
                            ?: throw ContentFailure(
                                "Page is no longer available. Reopen the chapter."
                            )
                    val temporary = storageOperation {
                        ReaderCacheFiles.temporary(cacheDir)
                    }
                    try {
                        imageConnections.withPermit {
                            (pageSource ?: app.source).transferPage(chapter.id, url, temporary)
                        }
                        coroutineContext.ensureActive()
                        if (repairMarker(app, chapter.id, page).exists())
                            ChapterFiles.locked(chapter.id) {
                                if (
                                    ChapterFiles.version(chapter.id) == repairVersion &&
                                        dao.download(chapter.id) != null &&
                                        repairMarker(app, chapter.id, page).exists()
                                ) {
                                    val repaired =
                                        File.createTempFile(
                                            "repair-",
                                            ".part",
                                            downloaded.parentFile,
                                        )
                                    try {
                                        storageOperation {
                                            temporary.copyTo(repaired, overwrite = true)
                                        }
                                        coroutineContext.ensureActive()
                                        if (!repaired.renameTo(downloaded))
                                            throw StorageFailure(
                                                "Could not repair the downloaded page."
                                            )
                                        writePageDigest(downloaded)
                                        repairMarker(app, chapter.id, page).delete()
                                        reconcileDownload(app, chapter.id)
                                    } finally {
                                        repaired.delete()
                                    }
                                }
                            }
                        ReaderCacheFiles.publish(temporary, target, settings.value.readerCacheMb.toLong() * 1024 * 1024)
                        target
                    } finally {
                        ReaderCacheFiles.discard(temporary)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw if (speculative) e else pageRetries.failed(retryKey, e)
                }
            }
        }
    }

    private suspend fun loadPage(chapter: Release, page: Int) {
        readerFile.value = null
        readerError.value = null
        readerRetryAllowed.value = true
        readerRetryAt.value = 0L
        readerSourceAction.value = false
        readerFile.value = pageFileFor(chapter, page)
        if (page == pageCount.value - 1) savePage(chapter, page, completed = true)
        if (settings.value.detectWebtoon && preferences.value.none { it.mangaId == chapter.mangaId && it.readerMode.isNotEmpty() }) {
            val dimensions = withContext(Dispatchers.IO) {
                android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                    android.graphics.BitmapFactory.decodeFile(readerFile.value?.path, this)
                }
            }
            if (dimensions.outWidth > 0 && dimensions.outHeight.toLong() > dimensions.outWidth * 3L)
                titleReaderMode("webtoon")
        }
        prefetchAfter(chapter, page)
    }

    fun prefetchAfter(chapter: Release, page: Int) {
        if (reader.value?.id != chapter.id || page != readerPage.value) return
        val anchor = chapter.id to page
        if (prefetchAnchor == anchor && prefetchJob?.isActive == true) return
        prefetchJob?.cancel()
        prefetchAnchor = anchor
        val session = readerSession.value
        val count = settings.value.preloadPages.coerceIn(0, 10)
        prefetchJob = viewModelScope.launch {
            try {
                if (count == 0) return@launch
                pageFileFor(chapter, page, speculative = true)
                prefetchReaderPages(chapter, page, count,
                    nextChapter = { current ->
                        if (!settings.value.continuousChapters || readerSession.value != session) null
                        else nextChapter(current)?.takeUnless {
                            settings.value.gapWarnings && ReaderContinuity.gap(current, it) != null
                        }
                    },
                    resolve = { resolveChapter(it, speculative = true) },
                    fetch = { target, nextPage -> pageFileFor(target, nextPage, speculative = true); Unit },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Speculation never changes the visible page or its error state.
            }
        }
    }

    fun scrollPosition(chapter: Release, page: Int, fraction: Float) {
        if (reader.value?.id != chapter.id) return
        readerPage.value = page
        pinReaderPages(readerCacheOwner, listOf(chapter to page))
        readerScroll.value = fraction.coerceIn(0f, 1f)
        if (prefetchAnchor != (chapter.id to page)) prefetchAfter(chapter, page)
        task {
            savePage(chapter, page, scroll = fraction.coerceIn(0f, 1f), preserveCompleted = true)
        }
    }

    fun page(index: Int) {
        val chapter = reader.value ?: return
        if (index !in 0 until pageCount.value) return
        prefetchJob?.cancel()
        prefetchAnchor = null
        pageJob?.cancel()
        readerPage.value = index
        pinReaderPages(readerCacheOwner, listOf(chapter to index))
        readerScroll.value = 0f
        pageJob =
            viewModelScope.launch {
                try {
                    savePage(chapter, index, preserveCompleted = true)
                    loadPage(chapter, index)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    readerRetryAllowed.value = automaticRetryAllowed(e)
                    readerRetryAt.value = retryAtMillis(e)
                    readerSourceAction.value = requiresSourceAction(e)
                    readerError.value = connectionMessage(e)
                }
            }
    }

    fun finishChapter() = task {
        val chapter = reader.value ?: return@task
        savePage(chapter, readerPage.value, true)
        val available = selectedChapters()
        val next =
            available
                .dropWhile { it.id != chapter.id }
                .drop(1)
                .firstOrNull { it.key != chapter.key && it.readable }
        if (next != null) read(next)
        else {
            closeReader()
            notice.value = "You're caught up with the available chapters."
        }
    }

    fun completeVisibleChapter(chapter: Release, session: Int) = task {
        if (session == readerSession.value && chapter.pages > 0 && reader.value?.mangaId == chapter.mangaId)
            savePage(chapter, chapter.pages - 1, completed = true)
    }

    fun navigateReader(delta: Int) {
        val chapter = reader.value ?: return
        val count = pageCount.value
        val page = readerPage.value
        if (!readerReady.value || count <= 0 || readerGapTarget.value != null) return
        if (delta < 0 && page == 0) previousChapter()
        else if (delta > 0 && page + 1 >= count) {
            val next = nextChapter(chapter)
            if (settings.value.gapWarnings && next != null && ReaderContinuity.gap(chapter, next) != null)
                readerGapTarget.value = next
            else finishChapter()
        } else {
            val override = preferences.value.find { it.mangaId == chapter.mangaId }?.readerMode.orEmpty()
            val mode = ReadingMode.resolve(override, settings.value.webtoon, settings.value.rtl)
            if (mode == ReadingMode.WEBTOON) jumpTo(chapter, page + delta) else page(page + delta)
        }
    }

    override fun onCleared() {
        closeReader()
        super.onCleared()
    }

    fun closeReader() {
        readerGapTarget.value = null
        prefetchJob?.cancel()
        prefetchAnchor = null
        pageJob?.cancel()
        manifestRequests.cancel()
        reader.value = null
        ReaderCacheFiles.releaseGroup(readerCacheOwner); ChapterFiles.releaseReaders(readerCacheOwner)
        readerFile.value = null
    }

    fun download(chapter: Release) = task {
        if (!chapter.readable) {
            notice.value = "This release does not provide downloadable pages."
            return@task
        }
        enqueueDownload(app, chapter, settings.value)
        notice.value =
            if (settings.value.wifi) "Queued. Downloads start on an unmetered connection."
            else "Chapter queued for download."
    }

    fun pause(download: Download) = task { stopDownload(download, "Paused") }

    private suspend fun stopDownload(download: Download, state: String) = downloadCoordinator.stop(download, state)

    fun cancelDownload(download: Download) = task {
        if (download.state == "Complete") return@task
        stopDownload(download, "Cancelled")
    }

    fun moveDownload(download: Download, step: Int? = null) = task {
        downloadCoordinator.move(download, step)
        notice.value = "Queue order updated. The current chapter finishes first."
    }

    fun resume(download: Download) = task {
        dao.chapter(download.releaseId)?.release()?.let { enqueueDownload(app, it, settings.value) }
    }

    fun deleteDownload(download: Download) = task { downloadCoordinator.delete(download) }
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeNavigation() {
        viewModelScope.launch {
            combine(currentTab, activeSource, searchQuery, browseFilter, selected) { tab, source, query, filter, title ->
                NavigationState(tab, source, query, filter, title?.id.orEmpty())
            }.combine(reader) { state, release -> state.copy(reader = release?.id.orEmpty()) }
                .combine(readerPage) { state, page -> state.copy(page = page) }
                .combine(activeCreator) { state, creator -> state.copy(creator = creator) }
                .debounce(150).collect { savedState["navigation"] = it.encode() }
        }
    }

    init {
        viewModelScope.launch {
            val restored = NavigationState.decode(savedState["navigation"])
            if (restored != null) {
                app.settings.flow.first()
                currentTab.value = restored.tab
                activeSource.value = restored.source.takeIf { it in sourceNames }.orEmpty()
                browseFilter.value = restored.filter
                searchQuery.value = restored.query
                query = restored.query
                creatorFilter = restored.creator
                activeCreator.value = restored.creator
                val title = restored.title.takeIf { it.isNotEmpty() }?.let { dao.manga(it) }
                if (title != null) {
                    selected.value = title
                    chapters.value = dao.chapters(title.id).map { it.release() }
                    val release = restored.reader.takeIf { it.isNotEmpty() }?.let { dao.chapter(it)?.release() }
                    if (release != null && release.mangaId == title.id) read(release, requestedPage = restored.page)
                    else open(title)
                } else if (restored.tab == 1 && activeSource.value.isNotEmpty()) {
                    search(restored.query, restored.creator)
                }
            }
            observeNavigation()
        }
    }

}
