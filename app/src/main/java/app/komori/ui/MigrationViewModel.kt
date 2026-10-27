package app.komori.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import app.komori.KomoriApp
import app.komori.data.*
import app.komori.domain.Release
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

internal interface MigrationRequests {
    suspend fun browse(source: String, query: String, page: Int): BrowsePage
    suspend fun details(candidate: Manga): Manga
    suspend fun chapters(id: String, language: String): List<Chapter>
}

class MigrationViewModel private constructor(application: Application, private val requests: MigrationRequests) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, object : MigrationRequests {
        private val app = application as KomoriApp
        override suspend fun browse(source: String, query: String, page: Int) = app.source.browse(source, query, page, BrowseFilter(), null)
        override suspend fun details(candidate: Manga) = app.source.details(candidate)
        override suspend fun chapters(id: String, language: String) = app.source.chapters(id, language)
    })
    internal constructor(application: Application, requests: MigrationRequests, testScope: Unit = Unit) : this(application, requests)
    private val app = application as KomoriApp
    val state = MutableStateFlow(MigrationReview())
    private var job: Job? = null
    private val generation = java.util.concurrent.atomic.AtomicLong()
    private data class Destination(val token: Long, val from: String, val target: String, val language: String, val rows: List<Chapter>)
    private var destination: Destination? = null
    private var source: Manga? = null

    private fun invalidate(): Long {
        val token = generation.incrementAndGet()
        job?.cancel()
        destination = null
        return token
    }
    override fun onCleared() { invalidate(); source = null; super.onCleared() }
    fun begin(book: Manga) {
        invalidate(); source = book
        state.value = MigrationReview(query = book.title, sourceKey = sourceNames.keys.firstOrNull { it != sourceKey(book.id) } ?: "mangadex")
    }
    fun close() { invalidate(); source = null; state.value = MigrationReview() }
    fun edit(query: String = state.value.query, sourceKey: String = state.value.sourceKey) {
        invalidate()
        state.value = MigrationReview(query = query.take(512), sourceKey = sourceKey.takeIf { it in sourceNames } ?: "mangadex")
    }
    fun search(more: Boolean = false) {
        if (source == null || more && state.value.loading) return
        val old = state.value
        val token = invalidate()
        state.value = old.copy(loading = true, error = null, selected = null, targets = emptyList(), plan = emptyList())
        job = viewModelScope.launch {
            try {
                val page = if (more) old.page else 1
                val found = requests.browse(old.sourceKey, old.query, page)
                if (generation.get() != token) return@launch
                val merged = ((if (more) old.results else emptyList()) + found.titles).distinctBy { it.id }
                state.value = old.copy(results = merged, page = page + 1, hasMore = found.hasMore && merged.size > if (more) old.results.size else 0,
                    loading = false, selected = null, targets = emptyList(), plan = emptyList(), error = null)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (generation.get() == token) state.value = state.value.copy(loading = false, error = connectionMessage(error)) }
        }
    }
    fun select(candidate: Manga) {
        val book = source ?: return
        val old = state.value
        if (candidate.id == book.id || sourceKey(candidate.id) != old.sourceKey || old.results.none { it.id == candidate.id }) {
            state.value = old.copy(error = "Choose a current search result from another title or edition."); return
        }
        val token = invalidate()
        state.value = old.copy(loading = true, selected = null, targets = emptyList(), plan = emptyList(), error = null)
        job = viewModelScope.launch {
            try {
                val settings = app.settings.flow.first()
                val preference = app.dao.titlePreferences(book.id)
                val language = sourceReadingLanguage(candidate.id, settings.language, preference?.language.orEmpty())
                val detailed = requests.details(candidate)
                require(detailed.id == candidate.id) { "Destination details belong to another title." }
                val rows = requests.chapters(candidate.id, language)
                require(rows.all { it.mangaId == candidate.id }) { "The destination feed contains chapters from another title." }
                val target = rows.map { it.release() }.filter { it.readable }
                val plan = SourceMigrationPolicy.propose(app.dao.chapters(book.id).map { it.release() }, app.dao.observeProgressForManga(book.id).first(), target)
                if (generation.get() != token) return@launch
                destination = Destination(token, book.id, candidate.id, language, rows.toList())
                state.value = old.copy(selected = detailed, targets = target, plan = plan, loading = false, error = null)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (generation.get() == token) state.value = state.value.copy(loading = false, error = connectionMessage(error)) }
        }
    }
    fun map(positionId: String, target: Release?) {
        val current = state.value
        if (current.loading || current.selected == null || target != null && target !in current.targets) return
        state.value = current.copy(plan = current.plan.map { if (it.position.releaseId == positionId) it.copy(target = target) else it })
    }
    fun apply(keepOriginal: Boolean, copyPage: Boolean, done: (Manga) -> Unit) {
        val book = source ?: return
        val review = state.value
        val target = review.selected ?: return
        val ready = destination ?: return
        if (review.loading || ready.token != generation.get() || ready.from != book.id || ready.target != target.id) return
        val token = ready.token
        val rows = ready.rows
        val language = ready.language
        state.value = review.copy(loading = true, applying = true, error = null)
        job = viewModelScope.launch {
            try {
                val mapped = review.plan.filter { it.target != null }
                require(mapped.map { it.target!!.key }.distinct().size == mapped.size) { "Map each reading record to a different target chapter." }
                app.database.withTransaction {
                    currentCoroutineContext().ensureActive()
                    if (generation.get() != token) throw CancellationException()
                    val fromShelf = app.dao.shelf(book.id) ?: Shelf(book.id)
                    val toShelf = app.dao.shelf(target.id)
                    val fromPref = app.dao.titlePreferences(book.id)
                    require(rows.all { it.mangaId == target.id }) { "The destination feed contains chapters from another title." }
                    for (row in rows) require(app.dao.chapter(row.id)?.mangaId.let { it == null || it == target.id }) { "A destination chapter belongs to another title." }
                    val allTargets = rows.associateBy { it.id }
                    for (entry in mapped) {
                        val release = entry.target!!
                        require(allTargets[release.id]?.mangaId == target.id) { "The selected edition changed. Review again." }
                        val oldPosition = app.dao.progress(entry.position.releaseId)
                        require(oldPosition == null || oldPosition.mangaId == book.id && oldPosition.chapterKey == entry.position.chapterKey) { "Source progress changed. Review again." }
                        val existing = app.dao.progress(release.id)
                        require(existing == null || existing.mangaId == target.id) { "A target chapter belongs to another title." }
                        if (oldPosition?.completed == true) app.dao.putProgress(existing?.copy(completed = true)
                            ?: Progress(release.id, target.id, release.key, 0, true))
                    }
                    val oldLast = fromShelf.lastRelease?.let { app.dao.progress(it) }
                    val last = oldLast?.let { old -> mapped.find { it.position.chapterKey == old.chapterKey } }
                    var lastTarget = toShelf?.lastRelease
                    if (copyPage && last != null && !oldLast.completed) {
                        val release = last.target!!
                        require(release.pages > oldLast.page) { "The destination page count is unknown or shorter. Keep page copying off." }
                        val existing = app.dao.progress(release.id)
                        if (existing?.completed != true) {
                            app.dao.putProgress(Progress(release.id, target.id, release.key, oldLast.page, false, 0f))
                            lastTarget = release.id
                        }
                    }
                    app.dao.putManga(app.dao.manga(target.id) ?: target)
                    app.dao.saveFeed(target.id, language, rows)
                    app.dao.putShelf((toShelf ?: fromShelf.copy(mangaId = target.id, lastRelease = null)).copy(saved = true, lastRelease = lastTarget,
                        status = if (toShelf == null && fromShelf.status == "Completed") "Reading" else toShelf?.status ?: fromShelf.status,
                        touched = System.currentTimeMillis()))
                    app.dao.putShelf(fromShelf.copy(saved = if (keepOriginal) fromShelf.saved else false))
                    if (app.dao.titlePreferences(target.id) == null && fromPref != null)
                        app.dao.putTitlePreferences(fromPref.copy(mangaId = target.id, groupId = if (sourceKey(book.id) == sourceKey(target.id)) fromPref.groupId else "",
                            strict = sourceKey(book.id) == sourceKey(target.id) && fromPref.strict, language = if (sourceKey(target.id) == "mangadex") language else ""))
                    app.dao.backupMemberships().filter { it.mangaId == book.id }.forEach { app.dao.putMembership(it.copy(mangaId = target.id)) }
                    app.dao.putLink(MigrationLink(book.id, target.id, System.currentTimeMillis()))
                    currentCoroutineContext().ensureActive()
                    if (generation.get() != token) throw CancellationException()
                }
                if (generation.get() == token) {
                    state.value = review.copy(loading = false, applying = false)
                    done(target)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation.get() == token) state.value = review.copy(loading = false, applying = false, error = connectionMessage(e)) }
        }
    }
}

data class MigrationReview(
    val query: String = "", val sourceKey: String = "mangadex", val results: List<Manga> = emptyList(),
    val page: Int = 1, val hasMore: Boolean = false, val loading: Boolean = false, val error: String? = null,
    val applying: Boolean = false, val selected: Manga? = null, val targets: List<Release> = emptyList(), val plan: List<MigrationProgress> = emptyList(),
)
