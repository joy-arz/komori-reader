package app.komori.data

import androidx.room.withTransaction
import androidx.work.WorkManager
import app.komori.KomoriApp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Owns user queue mutations, separately from reader and discovery state. */
internal class DownloadCoordinator(private val app: KomoriApp) {
    data class BulkDeleteResult(val removed: Int, val skipped: Int, val failed: Int)

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
                    OfflineTitleAssets.removeIfUnused(app, download.mangaId)
                }
            }
        }
        kickDownloadQueue(app)
    }

    /**
     * Removes many queue rows with one settle pass instead of one WorkManager/database
     * round trip per chapter. Chapter locks and row deletes stay paired so a finishing
     * worker cannot recreate a row after its pages were removed.
     */
    suspend fun deleteMany(downloads: Collection<Download>, status: (String) -> Unit = {}): BulkDeleteResult {
        val targets = downloads.distinctBy { it.releaseId }
        if (targets.isEmpty()) return BulkDeleteResult(0, 0, 0)
        var removed = 0
        var skipped = 0
        var failed = 0
        val touchedTitles = mutableSetOf<String>()
        return try {
            withDownloadQueueMutation {
                cancelDeletedWork(targets.map { it.releaseId }.toSet())
                withContext(Dispatchers.IO) {
                    var processed = 0
                    for (chunk in targets.chunked(BULK_DELETE_CHUNK)) {
                        currentCoroutineContext().ensureActive()
                        val candidates =
                            chunk.filterNot { row ->
                                val reading =
                                    try {
                                        ChapterFiles.requireNotReading(row.releaseId)
                                        false
                                    } catch (_: ContentFailure) {
                                        true
                                    }
                                if (reading) skipped++
                                reading
                            }
                        if (candidates.isNotEmpty()) {
                            withContext(NonCancellable) {
                                withChapterLocks(candidates.map { it.releaseId }.sorted()) {
                                    val rows = mutableListOf<Download>()
                                    for (candidate in candidates) {
                                        val latest = app.dao.download(candidate.releaseId) ?: continue
                                        try {
                                            ChapterFiles.requireNotReading(candidate.releaseId)
                                        } catch (_: ContentFailure) {
                                            skipped++
                                            continue
                                        }
                                        ChapterFiles.invalidate(candidate.releaseId)
                                        val directory = chapterDirectory(app, candidate.releaseId)
                                        if (directory.exists() && !directory.deleteRecursively()) {
                                            failed++
                                            continue
                                        }
                                        rows += latest
                                    }
                                    if (rows.isNotEmpty()) {
                                        app.database.withTransaction {
                                            rows.map { it.releaseId }.chunked(SQLITE_ID_CHUNK).forEach { app.dao.removeDownloads(it) }
                                        }
                                        removed += rows.size
                                        touchedTitles += rows.map { it.mangaId }
                                    }
                                }
                            }
                        }
                        processed += chunk.size
                        status("Removing downloads… $processed of ${targets.size}")
                    }
                    if (touchedTitles.isNotEmpty()) {
                        status("Removing unused title art…")
                        touchedTitles.forEach { OfflineTitleAssets.removeIfUnused(app, it) }
                    }
                }
            }
            BulkDeleteResult(removed, skipped, failed)
        } finally {
            withContext(NonCancellable) { kickDownloadQueue(app) }
        }
    }

    private suspend fun cancelDeletedWork(deletedIds: Set<String>) {
        val manager = WorkManager.getInstance(app)
        val head = app.dao.queueHead()
        val remainingActive = app.dao.orderedQueue().any { it.releaseId !in deletedIds }
        withContext(Dispatchers.IO) {
            if (head != null && head.releaseId in deletedIds) {
                manager.cancelUniqueWork("download-${head.releaseId}").result.get()
            }
            if (!remainingActive) {
                manager.cancelUniqueWork("download-queue-recovery").result.get()
                manager.cancelAllWorkByTag(DOWNLOAD_QUEUE_TAG).result.get()
            }
        }
    }

    private suspend fun <T> withChapterLocks(ids: List<String>, block: suspend () -> T): T =
        if (ids.isEmpty()) block()
        else ChapterFiles.locked(ids.first()) { withChapterLocks(ids.subList(1, ids.size), block) }

    private companion object {
        const val BULK_DELETE_CHUNK = 100
        const val SQLITE_ID_CHUNK = 400
    }
}
