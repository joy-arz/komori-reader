package app.komori.data

import androidx.room.withTransaction
import androidx.work.WorkManager
import app.komori.KomoriApp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Owns user queue mutations, separately from reader and discovery state. */
internal class DownloadCoordinator(private val app: KomoriApp) {
    suspend fun stop(download: Download, state: String) {
        withDownloadQueueMutation {
            withContext(Dispatchers.IO) { WorkManager.getInstance(app).cancelUniqueWork("download-${download.releaseId}").result.get() }
            ChapterFiles.locked(download.releaseId) {
                ChapterFiles.invalidate(download.releaseId)
                app.dao.download(download.releaseId)?.let { if (it.state != "Complete") app.dao.putDownload(it.copy(state = state)) }
            }
        }
        kickDownloadQueue(app)
    }

    suspend fun move(download: Download, step: Int?) {
        app.database.withTransaction {
            val rows = app.dao.orderedQueue().toMutableList()
            val index = rows.indexOfFirst { it.releaseId == download.releaseId }
            if (index >= 0) {
                val target = if (step == null) 0 else (index + step).coerceIn(0, rows.lastIndex)
                rows.add(target, rows.removeAt(index))
                rows.forEachIndexed { rank, row -> app.dao.queuePosition(row.releaseId, rank.toLong()) }
            }
        }
        kickDownloadQueue(app)
    }

    suspend fun delete(download: Download) {
        withDownloadQueueMutation {
            withContext(Dispatchers.IO) {
                WorkManager.getInstance(app).cancelUniqueWork("download-${download.releaseId}").result.get()
                ChapterFiles.locked(download.releaseId) {
                    ChapterFiles.requireNotReading(download.releaseId)
                    ChapterFiles.invalidate(download.releaseId)
                    val directory = chapterDirectory(app, download.releaseId)
                    if (directory.exists() && !directory.deleteRecursively()) throw IOException("Could not delete the downloaded chapter. Please retry.")
                    app.dao.removeDownload(download.releaseId)
                }
            }
        }
        kickDownloadQueue(app)
    }
}
