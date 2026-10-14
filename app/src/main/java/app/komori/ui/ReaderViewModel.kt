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
import okhttp3.Request

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
    val selected = MutableStateFlow<Manga?>(null)
    val chapters = MutableStateFlow<List<Release>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val notice = MutableStateFlow<String?>(null)
    val reader = MutableStateFlow<Release?>(null)
    val readerPage = MutableStateFlow(0)
    val readerFile = MutableStateFlow<File?>(null)
    val readerError = MutableStateFlow<String?>(null)
    val pageCount = MutableStateFlow(0)
    var canLoadMore = true
        private set

    private var query = ""
    private var nextOffset = 0
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
                    nextOffset = 0
                    query = text
                    creatorFilter = creator
                }
                val found = app.source.search(query, nextOffset, creatorFilter)
                nextOffset += found.size
                results.value =
                    ((if (more) results.value else emptyList()) + found).distinctBy { it.id }
                canLoadMore = found.size == 30
            } finally {
                loading.value = false
            }
        }
    }

    fun open(manga: Manga, immediately: Boolean = false) {
        detailJob?.cancel()
        selected.value = manga
        chapters.value = emptyList()
        error.value = null
        detailJob = task {
            loading.value = true
            try {
                dao.putManga(manga)
                val cached = dao.chapters(manga.id).map { it.release() }
                chapters.value = cached
                // Cached chapters make direct reading usable without a connection.
                if (immediately && cached.any { it.language == settings.value.language }) {
                    startReading()
                    return@task
                }
                try {
                    val fresh = app.source.chapters(manga.id, settings.value.language)
                    dao.putChapters(fresh)
                    chapters.value = fresh.map { it.release() }
                } catch (e: IOException) {
                    if (cached.isEmpty()) throw e
                    notice.value = "Using saved chapters. ${connectionMessage(e)}"
                }
                if (immediately) startReading()
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

    fun selectedChapters(): List<Release> {
        val pref = preferences.value.find { it.mangaId == selected.value?.id }
        val current = shelf.value.find { it.mangaId == selected.value?.id }?.lastRelease
        return ChapterPolicy.select(
            chapters.value,
            ChapterPreferences(
                settings.value.language,
                settings.value.smart,
                pref?.groupId ?: "",
                pref?.strict ?: false,
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

    fun group(id: String, strict: Boolean = false) = task {
        selected.value?.let { dao.putTitlePreferences(TitlePreferences(it.id, id, strict)) }
    }

    fun setting(key: String, value: Boolean) = task { app.settings.set(key, value) }

    fun language(value: String) = task { app.settings.language(value) }

    fun startReading(fromBeginning: Boolean = false) {
        val available = selectedChapters()
        val last = shelf.value.find { it.mangaId == selected.value?.id }?.lastRelease
        val completed = progress.value.filter { it.completed }.map { it.chapterKey }.toSet()
        val next =
            if (fromBeginning) available.firstOrNull()
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
        if (chapter.externalUrl != null || chapter.pages == 0) {
            notice.value =
                "This chapter is hosted externally. Open its source link from the chapter row."
            return
        }
        pageJob?.cancel()
        reader.value = chapter
        readerFile.value = null
        readerError.value = null
        pageCount.value = chapter.pages
        pageJob =
            viewModelScope.launch {
                try {
                    val previous = dao.progress(chapter.id)
                    val page =
                        if (restart || !settings.value.resume || previous?.completed == true) 0
                        else previous?.page ?: 0
                    readerPage.value = page.coerceIn(0, (chapter.pages - 1).coerceAtLeast(0))
                    savePage(chapter, readerPage.value)
                    loadPage(chapter, readerPage.value)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    readerError.value = connectionMessage(e)
                }
            }
    }

    private suspend fun savePage(chapter: Release, page: Int, completed: Boolean = false) {
        mutation.withLock {
            dao.putProgress(Progress(chapter.id, chapter.mangaId, chapter.key, page, completed))
            val old = dao.shelf(chapter.mangaId) ?: Shelf(chapter.mangaId)
            dao.putShelf(old.copy(lastRelease = chapter.id, touched = System.currentTimeMillis()))
        }
    }

    private suspend fun loadPage(chapter: Release, page: Int) =
        withContext(Dispatchers.IO) {
            readerFile.value = null
            readerError.value = null
            val downloaded = pageFile(app, chapter.id, page)
            if (downloaded.exists()) {
                readerFile.value = downloaded
                return@withContext
            }
            val cacheDir = File(app.cacheDir, "reader").apply { mkdirs() }
            val target = File(cacheDir, "${chapter.id}-$page-${settings.value.saver}.img")
            if (target.exists()) {
                target.setLastModified(System.currentTimeMillis())
                readerFile.value = target
                return@withContext
            }
            val manifestKey = "${chapter.id}-${settings.value.saver}"
            val pageUrls =
                pageManifests[manifestKey]
                    ?: app.source.pages(chapter.id, settings.value.saver).also {
                        pageManifests[manifestKey] = it
                    }
            pageCount.value = pageUrls.size
            val url =
                pageUrls.getOrNull(page)
                    ?: throw IOException("Page is no longer available. Reopen the chapter.")
            val temporary = File.createTempFile("page-", ".part", cacheDir)
            try {
                app.source.client.newCall(Request.Builder().url(url).build()).execute().use {
                    response ->
                    if (!response.isSuccessful) {
                        pageManifests.remove(manifestKey)
                        throw IOException("Page request failed (${response.code}). Tap Retry.")
                    }
                    val body = response.body ?: throw IOException("Empty page")
                    temporary.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
                }
                coroutineContext.ensureActive()
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
                readerFile.value = target
            } finally {
                temporary.delete()
            }
        }

    fun page(index: Int) {
        val chapter = reader.value ?: return
        if (index !in 0 until pageCount.value) return
        pageJob?.cancel()
        readerPage.value = index
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
                .firstOrNull { it.key != chapter.key }
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
        if (chapter.externalUrl != null || chapter.pages == 0) {
            notice.value = "External chapters cannot be downloaded here."
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
