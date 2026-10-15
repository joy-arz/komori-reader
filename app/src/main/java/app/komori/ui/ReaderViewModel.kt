package app.komori.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import app.komori.KomoriApp
import app.komori.data.*
import app.komori.domain.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as KomoriApp
    private val dao = app.dao
    val shelf = dao.observeShelf().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val library = dao.observeManga().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val creators =
        dao.observeCreators().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val downloads =
        dao.observeDownloads().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val progress =
        dao.observeProgress().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val preferences =
        dao.observeTitlePreferences().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings = app.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val results = MutableStateFlow<List<Manga>>(emptyList())
    val activeSource = MutableStateFlow("")
    val browseFilter = MutableStateFlow(BrowseFilter())
    val tags = MutableStateFlow<List<SourceTag>>(emptyList())
    private var tagsJob: Job? = null
    val selected = MutableStateFlow<Manga?>(null)
    val chapters = MutableStateFlow<List<Release>>(emptyList())
    val chapterStatus = MutableStateFlow("")
    val sourceLanguages = MutableStateFlow<List<String>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val notice = MutableStateFlow<String?>(null)
    val reader = MutableStateFlow<Release?>(null)
    val readerSession = MutableStateFlow(0)
    val bulkStatus = MutableStateFlow<String?>(null)
    val readerPage = MutableStateFlow(0)
    val readerScroll = MutableStateFlow(0f)
    val readerReady = MutableStateFlow(false)
    private val imageFetch = Mutex()
    val readerFile = MutableStateFlow<File?>(null)
    val readerError = MutableStateFlow<String?>(null)
    val pageCount = MutableStateFlow(0)
    var canLoadMore = true
        private set

    private var query = ""
    private var offlineLanguage: String? = null
    private var nextOffset = 1
    private var creatorFilter: Creator? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var pageJob: Job? = null
    private val pageManifests = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private val mutation = Mutex()

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
        searchJob?.cancel()
        searchJob = task {
            loading.value = true
            error.value = null
            try {
                if (!more) {
                    results.value = emptyList()
                    nextOffset = 1
                    query = text
                    creatorFilter = creator
                }
                if (creatorFilter != null) {
                    activeSource.value = "mangadex"
                    browseFilter.value = BrowseFilter()
                }
                if (activeSource.value.isEmpty())
                    activeSource.value = sourceOrder(app.settings.flow.first().sources).first()
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
                results.value =
                    ((if (more) results.value else emptyList()) + found).distinctBy { it.id }
                canLoadMore = foundPage.hasMore
            } finally {
                loading.value = false
            }
        }
    }

    fun chooseSource(key: String) {
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

    fun readOffline(download: Download) {
        detailJob?.cancel()
        detailJob = task {
            val book = dao.manga(download.mangaId) ?: return@task
            val rows = dao.chapters(book.id).map { it.release() }
            val complete =
                dao.observeDownloads()
                    .first()
                    .filter { it.state == "Complete" }
                    .associate { it.releaseId to it.total }
            selected.value = book
            chapters.value = retainDownloadedChapters(emptyList(), rows, complete)
            val release = chapters.value.find { it.id == download.releaseId } ?: return@task
            offlineLanguage = release.language
            chapterStatus.value = "Saved chapter list · Offline"
            read(release)
        }
    }

    fun open(manga: Manga, immediately: Boolean = false) {
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
                val language =
                    if (sourceKey(manga.id) == "mangadex") app.settings.flow.first().language
                    else "en"
                dao.putManga(manga)
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
                    val details = app.source.details(manga)
                    dao.putManga(details)
                    selected.value = details
                } catch (e: IOException) {
                    /* Chapter list remains usable. */
                }
            } finally {
                loading.value = false
            }
        }
    }

    fun back() {
        detailJob?.cancel()
        selected.value = null
        chapters.value = emptyList()
        loading.value = false
        error.value = null
    }

    fun chapterLanguage(): String =
        offlineLanguage
            ?: if (sourceKey(selected.value?.id.orEmpty()) == "mangadex") settings.value.language
            else "en"

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

    fun setting(key: String, value: Boolean) = task { app.settings.set(key, value) }

    fun language(value: String) = task {
        app.settings.language(value)
        settings.first { it.language == value }
        selected.value?.let { open(it) }
    }

    fun startReading(fromBeginning: Boolean = false) {
        val available = selectedChapters()
        val last = shelf.value.find { it.mangaId == selected.value?.id }?.lastRelease
        val completed = progress.value.filter { it.completed }.map { it.chapterKey }.toSet()
        val next =
            if (fromBeginning) available.firstOrNull { it.readable }
            else ChapterPolicy.start(available, last, completed, settings.value.reread)
        if (next == null) {
            notice.value =
                if (available.isEmpty()) "No chapters match your language and group preferences."
                else "You're caught up. Use Read again to start at the beginning."
            return
        }
        read(next, restart = fromBeginning || next.key in completed)
    }

    fun read(chapter: Release, restart: Boolean = false) {
        if (!chapter.readable) {
            notice.value =
                if (chapter.unavailable)
                    "MangaDex marks this chapter unavailable. Its pages cannot be fetched."
                else
                    "This chapter has no MangaDex pages. Open its external link from the chapter row if available."
            return
        }
        if (offlineLanguage != null) offlineLanguage = chapter.language
        pageJob?.cancel()
        readerReady.value = false
        readerSession.value += 1
        reader.value = chapter
        readerFile.value = null
        readerError.value = null
        pageCount.value = chapter.pages
        pageJob =
            viewModelScope.launch {
                try {
                    val resolved = resolveChapter(chapter)
                    reader.value = resolved
                    pageCount.value = resolved.pages
                    val previous = dao.progress(chapter.id)
                    val page =
                        if (restart || !settings.value.resume || previous?.completed == true) 0
                        else previous?.page ?: 0
                    readerPage.value = page.coerceIn(0, (resolved.pages - 1).coerceAtLeast(0))
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

    suspend fun resolveChapter(chapter: Release): Release {
        if (chapter.pages >= 0) return chapter
        val key = "${chapter.id}-${settings.value.saver}"
        val urls =
            pageManifests[key]
                ?: app.source.pages(chapter.id, settings.value.saver).also {
                    rememberPages(key, it)
                }
        if (urls.isEmpty()) throw IOException("This chapter has no available pages.")
        return chapter.copy(pages = urls.size)
    }

    fun nextChapter(chapter: Release): Release? =
        selectedChapters()
            .dropWhile { it.id != chapter.id }
            .drop(1)
            .firstOrNull { it.key != chapter.key && it.readable }

    fun scrollChapterPosition(chapter: Release, page: Int, fraction: Float, session: Int) {
        if (session != readerSession.value) return
        val previous = reader.value ?: return
        val oldPage = readerPage.value
        val completed = ChapterPolicy.completedBetween(selectedChapters(), previous.id, chapter.id)
        if (previous.id != chapter.id) {
            reader.value = chapter
            pageCount.value = chapter.pages
            readerFile.value = null
            readerError.value = null
        }
        readerPage.value = page
        readerScroll.value = fraction.coerceIn(0f, 1f)
        task {
            for (passed in completed) savePage(
                passed,
                if (passed.id == previous.id) oldPage else (passed.pages - 1).coerceAtLeast(0),
                true,
            )
            savePage(chapter, page, scroll = fraction.coerceIn(0f, 1f), preserveCompleted = true)
        }
    }

    suspend fun invalidateCachedPage(chapter: Release, page: Int) =
        withContext(Dispatchers.IO) {
            File(app.cacheDir, "reader/${chapter.id}-$page-${settings.value.saver}.img").delete()
            val local = pageFile(app, chapter.id, page)
            if (local.exists()) {
                val backup = File(local.parentFile, "${local.name}.repair")
                if (!local.renameTo(backup))
                    throw IOException("Could not prepare this page for repair.")
            }
            pageManifests.remove("${chapter.id}-${settings.value.saver}")
            Unit
        }

    fun retryPage() {
        val chapter = reader.value ?: return
        val index = readerPage.value
        task {
            invalidateCachedPage(chapter, index)
            if (reader.value?.id == chapter.id && readerPage.value == index) {
                if (readerReady.value) page(index) else read(chapter)
            }
        }
    }

    fun resetSourceOrder() = task { app.settings.sources(sourceNames.keys.joinToString(",")) }

    fun retryInterval(seconds: Int) = task { app.settings.retrySeconds(seconds) }

    fun downloadCandidates(choice: DownloadChoice) =
        DownloadPolicy.select(
            selectedChapters(),
            progress.value.filter { it.completed }.map { it.chapterKey }.toSet(),
            downloads.value.associate { it.releaseId to it.state },
            choice,
        )

    fun downloadBatch(releases: List<Release>) {
        if (bulkStatus.value != null) return
        bulkStatus.value = "Preparing download queue…"
        task {
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
        for (download in downloads.value.filter { it.state in listOf("Paused", "Failed") }) resume(
                download
            )
            .join()
    }

    private suspend fun savePage(
        chapter: Release,
        page: Int,
        completed: Boolean = false,
        scroll: Float = 0f,
        preserveCompleted: Boolean = false,
    ) {
        mutation.withLock {
            val isCompleted =
                completed || (preserveCompleted && dao.progress(chapter.id)?.completed == true)
            dao.putProgress(
                Progress(chapter.id, chapter.mangaId, chapter.key, page, isCompleted, scroll)
            )
            val old = dao.shelf(chapter.mangaId) ?: Shelf(chapter.mangaId)
            dao.putShelf(old.copy(lastRelease = chapter.id, touched = System.currentTimeMillis()))
        }
    }

    suspend fun pageFileFor(chapter: Release, page: Int): File =
        imageFetch.withLock {
            withContext(Dispatchers.IO) {
                val downloaded = pageFile(app, chapter.id, page)
                if (validImage(downloaded)) {
                    return@withContext downloaded
                }
                val cacheDir = File(app.cacheDir, "reader").apply { mkdirs() }
                val target = File(cacheDir, "${chapter.id}-$page-${settings.value.saver}.img")
                if (validImage(target)) {
                    target.setLastModified(System.currentTimeMillis())
                    return@withContext target
                }
                val manifestKey = "${chapter.id}-${settings.value.saver}"
                val pageUrls =
                    pageManifests[manifestKey]
                        ?: app.source.pages(chapter.id, settings.value.saver).also {
                            rememberPages(manifestKey, it)
                        }
                if (reader.value?.id == chapter.id) pageCount.value = pageUrls.size
                val url =
                    pageUrls.getOrNull(page)
                        ?: throw IOException("Page is no longer available. Reopen the chapter.")
                val temporary = File.createTempFile("page-", ".part", cacheDir)
                try {
                    transferImage(
                        app.source.client,
                        app.source.imageRequest(url, chapter.id),
                        temporary,
                    )
                    coroutineContext.ensureActive()
                    val backup = File(downloaded.parentFile, "${downloaded.name}.repair")
                    if (downloaded.exists() || backup.exists()) {
                        val repaired = File(downloaded.parentFile, "${downloaded.name}.replacement")
                        try {
                            temporary.copyTo(repaired, overwrite = true)
                            coroutineContext.ensureActive()
                            if (!repaired.renameTo(downloaded))
                                throw IOException("Could not repair the downloaded page.")
                            backup.delete()
                        } finally {
                            repaired.delete()
                        }
                    }
                    if (!temporary.renameTo(target)) throw IOException("Could not cache page")
                    // Keep the current file; trim oldest pages to a bounded 128 MB cache.
                    var total = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
                    cacheDir
                        .listFiles()
                        ?.filter { it != target && it.extension == "img" }
                        ?.sortedBy { it.lastModified() }
                        ?.forEach {
                            if (total > 128L * 1024 * 1024) {
                                total -= it.length()
                                it.delete()
                            }
                        }
                    target
                } finally {
                    temporary.delete()
                }
            }
        }

    private suspend fun loadPage(chapter: Release, page: Int) {
        readerFile.value = null
        readerError.value = null
        readerFile.value = pageFileFor(chapter, page)
    }

    fun scrollPosition(chapter: Release, page: Int, fraction: Float) {
        if (reader.value?.id != chapter.id) return
        readerPage.value = page
        readerScroll.value = fraction.coerceIn(0f, 1f)
        task {
            savePage(chapter, page, scroll = fraction.coerceIn(0f, 1f), preserveCompleted = true)
        }
    }

    fun page(index: Int) {
        val chapter = reader.value ?: return
        if (index !in 0 until pageCount.value) return
        pageJob?.cancel()
        readerPage.value = index
        readerScroll.value = 0f
        pageJob =
            viewModelScope.launch {
                try {
                    savePage(chapter, index)
                    loadPage(chapter, index)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
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

    fun closeReader() {
        pageJob?.cancel()
        reader.value = null
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

    fun pause(download: Download) = task {
        withContext(Dispatchers.IO) {
            WorkManager.getInstance(app)
                .cancelUniqueWork("download-${download.releaseId}")
                .result
                .get()
        }
        val latest = dao.download(download.releaseId) ?: return@task
        dao.putDownload(latest.copy(state = "Paused"))
    }

    fun resume(download: Download) = task {
        dao.chapter(download.releaseId)?.release()?.let { enqueueDownload(app, it, settings.value) }
    }

    fun deleteDownload(download: Download) = task {
        withContext(Dispatchers.IO) {
            WorkManager.getInstance(app)
                .cancelUniqueWork("download-${download.releaseId}")
                .result
                .get()
            chapterDirectory(app, download.releaseId).deleteRecursively()
        }
        dao.removeDownload(download.releaseId)
    }
}
