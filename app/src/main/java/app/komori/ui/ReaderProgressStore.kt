package app.komori.ui

import android.os.SystemClock
import app.komori.data.*
import app.komori.domain.Release
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

internal class ReaderProgressStore(private val dao: LibraryDao, private val mutation: Mutex) {
    private var lastWrite = 0L

    suspend fun save(chapter: Release, page: Int, completed: Boolean, scroll: Float, preserveCompleted: Boolean, flush: Boolean) = mutation.withLock {
        val old = dao.progress(chapter.id)
        val finished = completed || preserveCompleted && old?.completed == true
        val now = SystemClock.elapsedRealtime()
        val unchanged = old != null && old.page == page && old.completed == finished &&
            (old.scroll == scroll || !flush && now - lastWrite < 500 && abs(old.scroll - scroll) < 0.1f)
        if (!unchanged) {
            dao.putProgress(Progress(chapter.id, chapter.mangaId, chapter.key, page, finished, scroll))
            lastWrite = now
        }
        val shelf = dao.shelf(chapter.mangaId) ?: Shelf(chapter.mangaId)
        val wallTime = System.currentTimeMillis()
        if (shelf.lastRelease != chapter.id || wallTime - shelf.touched >= 60000)
            dao.putShelf(shelf.copy(lastRelease = chapter.id, touched = wallTime))
    }
}
