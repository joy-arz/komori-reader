package app.komori.data

import app.komori.KomoriApp
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Worker writes and destructive actions share a chapter lock. Epochs reject stale reader repairs.
 */
object ChapterFiles {
    private class Entry {
        val mutex = Mutex()
        val epoch = AtomicLong()
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    private fun entry(id: String) = entries.getOrPut(id) { Entry() }

    fun version(id: String): Long = entry(id).epoch.get()

    fun invalidate(id: String) {
        entry(id).epoch.incrementAndGet()
    }

    suspend fun <T> locked(id: String, block: suspend () -> T): T =
        entry(id).mutex.withLock { block() }
}

fun repairMarker(app: KomoriApp, id: String, page: Int) =
    File(chapterDirectory(app, id), "$page.img.repair")

/** Must be called under the chapter lock. File names persist the affected page indices. */
suspend fun markPageForRepair(app: KomoriApp, id: String, page: Int) {
    val record = app.dao.download(id) ?: return
    if (page !in 0 until record.total) return
    app.dao.putDownload(
        record.copy(
            state = "Needs repair",
            done = (record.done - 1).coerceAtLeast(0),
            error = "Page ${page + 1} needs repair.",
        )
    )
    val file = pageFile(app, id, page)
    val marker = repairMarker(app, id, page)
    marker.parentFile!!.mkdirs()
    if (file.exists()) {
        if (!file.renameTo(marker)) throw IOException("Could not prepare this page for repair.")
    } else if (!marker.exists()) marker.writeText("")
    reconcileDownload(app, id)
}

/** Must be called under the chapter lock, after a repair or integrity scan. */
suspend fun reconcileDownload(app: KomoriApp, id: String) {
    val record = app.dao.download(id) ?: return
    val valid =
        (0 until record.total).count {
            !repairMarker(app, id, it).exists() && validImage(pageFile(app, id, it))
        }
    val complete = record.total > 0 && valid == record.total
    app.dao.putDownload(
        record.copy(
            done = valid,
            state = if (complete) "Complete" else "Needs repair",
            error =
                if (complete) ""
                else "${record.total - valid} pages need repair. Other saved pages are retained.",
        )
    )
}
