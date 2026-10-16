package app.komori.data

import android.content.Context
import androidx.work.*
import app.komori.KomoriApp
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

fun chapterDirectory(context: Context, id: String) = File(context.filesDir, "chapters/$id")

fun pageFile(context: Context, id: String, page: Int) =
    File(chapterDirectory(context, id), "$page.img")

suspend fun enqueueDownload(
    context: Context,
    chapter: app.komori.domain.Release,
    settings: Settings,
) {
    val app = context.applicationContext as KomoriApp
    if (app.dao.download(chapter.id)?.state in setOf("Queued", "Downloading", "Retrying")) return
    ChapterFiles.locked(chapter.id) {
        val existing = app.dao.download(chapter.id)
        if (existing?.state in setOf("Complete", "Queued", "Downloading", "Retrying")) return@locked
        app.dao.putDownload(
            existing?.copy(state = "Queued", error = "")
                ?: Download(
                    chapter.id,
                    chapter.mangaId,
                    chapter.label,
                    chapter.pages.coerceAtLeast(0),
                    dataSaver = settings.saver,
                )
        )
        val request =
            OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
                .setInputData(workDataOf("chapter" to chapter.id))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            if (settings.wifi) NetworkType.UNMETERED else NetworkType.CONNECTED
                        )
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("download-${chapter.id}", ExistingWorkPolicy.KEEP, request)
    }
}

class ChapterDownloadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        gate.withLock {
            withContext(Dispatchers.IO) {
                val app = applicationContext as KomoriApp
                val id = inputData.getString("chapter") ?: return@withContext Result.failure()
                ChapterFiles.locked(id) chapter@{
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    var record = app.dao.download(id) ?: return@chapter Result.failure()
                    if (record.state !in setOf("Queued", "Downloading", "Retrying"))
                        return@chapter Result.failure()
                    try {
                        val urls = app.source.pages(id, record.dataSaver)
                        if (urls.isEmpty())
                            throw IOException("This release has no downloadable pages.")
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val directory = chapterDirectory(app, id).apply { mkdirs() }
                        // The manifest tracks image identity, not expiring delivery-host URLs.
                        val identity =
                            urls.joinToString("\n") {
                                it.substringAfterLast(
                                    "/data/",
                                    it.substringAfterLast("/data-saver/"),
                                )
                            }
                        val manifest = File(directory, "manifest")
                        if (!manifest.exists() || manifest.readText() != identity) {
                            directory.listFiles()?.forEach { it.delete() }
                            manifest.writeText(identity)
                        }
                        record =
                            record.copy(
                                state = "Downloading",
                                total = urls.size,
                                done =
                                    urls.indices.count {
                                        !repairMarker(app, id, it).exists() &&
                                            validImage(pageFile(app, id, it))
                                    },
                                error = "",
                            )
                        app.dao.putDownload(record)
                        val started = System.currentTimeMillis()
                        urls.forEachIndexed { index, url ->
                            if (isStopped) throw CancellationException()
                            if (System.currentTimeMillis() - started > 8 * 60 * 1000) {
                                app.dao.putDownload(record.copy(state = "Queued"))
                                return@chapter Result.retry()
                            }
                            val file = pageFile(app, id, index)
                            if (repairMarker(app, id, index).exists() || !validImage(file)) {
                                if (directory.usableSpace < 64L * 1024 * 1024)
                                    throw IOException(
                                        "Free at least 64 MB to continue downloading."
                                    )
                                val temporary =
                                    File.createTempFile("page-$index-", ".part", directory)
                                try {
                                    transferImage(
                                        app.source.client,
                                        app.source.imageRequest(url, id),
                                        temporary,
                                    )
                                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                    if (!temporary.renameTo(file))
                                        throw IOException("Could not save page ${index + 1}")
                                    repairMarker(app, id, index).delete()
                                    record = record.copy(done = record.done + 1)
                                } finally {
                                    temporary.delete()
                                }
                            }
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            app.dao.putDownload(record)
                        }
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        app.dao.putDownload(record.copy(state = "Complete"))
                        Result.success()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val needsAction = requiresSourceAction(e)
                        app.dao.putDownload(
                            record.copy(
                                state =
                                    if (needsAction) "Verification required"
                                    else if (runAttemptCount < 3) "Retrying" else "Failed",
                                error = connectionMessage(e),
                            )
                        )
                        if (!needsAction && runAttemptCount < 3) Result.retry()
                        else Result.failure()
                    }
                }
            }
        }

    companion object {
        private val gate = Mutex()
    }
}
