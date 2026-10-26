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

class MigrationViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KomoriApp
    val state = MutableStateFlow(MigrationReview())
    private var job: Job? = null
    private var rows = emptyList<Chapter>()
    private var source: Manga? = null
    private var language = "en"

    fun begin(book: Manga) {
        job?.cancel(); source = book; rows = emptyList()
        state.value = MigrationReview(query = book.title, sourceKey = sourceNames.keys.firstOrNull { it != sourceKey(book.id) } ?: "mangadex")
    }
    fun close() { job?.cancel() }
    fun edit(query: String = state.value.query, sourceKey: String = state.value.sourceKey) { state.value = MigrationReview(query = query.take(512), sourceKey = sourceKey) }
    fun search(more: Boolean = false) {
        job?.cancel()
        val old = state.value
        job = viewModelScope.launch {
            state.value = old.copy(loading = true, error = null)
            try {
                val page = if (more) old.page else 1
                val found = app.source.browse(old.sourceKey, old.query, page, BrowseFilter(), null)
                val merged = ((if (more) old.results else emptyList()) + found.titles).distinctBy { it.id }
                state.value = old.copy(results = merged, page = page + 1, hasMore = found.hasMore && merged.size > if (more) old.results.size else 0, loading = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.value = old.copy(loading = false, error = connectionMessage(e)) }
        }
    }
    fun select(candidate: Manga) {
        val book = source ?: return
        if (candidate.id == book.id) { state.value = state.value.copy(error = "Choose a different title or edition."); return }
        job?.cancel()
        job = viewModelScope.launch {
            state.value = state.value.copy(loading = true, error = null)
            try {
                val settings = app.settings.flow.first()
                val preference = app.dao.titlePreferences(book.id)
                language = sourceReadingLanguage(candidate.id, settings.language, preference?.language.orEmpty())
                val detailed = app.source.details(candidate)
                rows = app.source.chapters(candidate.id, language)
                val target = rows.map { it.release() }.filter { it.readable }
                val plan = SourceMigrationPolicy.propose(app.dao.chapters(book.id).map { it.release() }, app.dao.observeProgressForManga(book.id).first(), target)
                state.value = state.value.copy(selected = detailed, targets = target, plan = plan, loading = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.value = state.value.copy(loading = false, error = connectionMessage(e)) }
        }
    }
    fun map(positionId: String, target: Release?) {
        state.value = state.value.copy(plan = state.value.plan.map { if (it.position.releaseId == positionId) it.copy(target = target) else it })
    }
    fun apply(keepOriginal: Boolean, copyPage: Boolean, done: (Manga) -> Unit) {
        val book = source ?: return
        val review = state.value
        val target = review.selected ?: return
        if (review.loading) return
        job = viewModelScope.launch {
            state.value = review.copy(loading = true, error = null)
            try {
                val mapped = review.plan.filter { it.target != null }
                require(mapped.map { it.target!!.key }.distinct().size == mapped.size) { "Map each reading record to a different target chapter." }
                app.database.withTransaction {
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
                    if (copyPage && last != null && oldLast != null && !oldLast.completed) {
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
                }
                state.value = review.copy(loading = false)
                done(target)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.value = review.copy(loading = false, error = connectionMessage(e)) }
        }
    }
}

data class MigrationReview(
    val query: String = "", val sourceKey: String = "mangadex", val results: List<Manga> = emptyList(),
    val page: Int = 1, val hasMore: Boolean = false, val loading: Boolean = false, val error: String? = null,
    val selected: Manga? = null, val targets: List<Release> = emptyList(), val plan: List<MigrationProgress> = emptyList(),
)
