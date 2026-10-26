package app.komori.data

import android.content.Context
import androidx.work.*
import androidx.room.withTransaction
import app.komori.domain.DownloadPolicy
import app.komori.KomoriApp
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class DownloadTransfer(
    val page: Int = 0,
    val bytes: Long = 0,
    val totalBytes: Long = -1,
    val resolving: Boolean = false,
    val completedPages: Int = 0,
)

/** Transient byte progress; completed pages remain persisted in Room. */
object DownloadTransfers {
    private val current = MutableStateFlow<Map<String, DownloadTransfer>>(emptyMap())
    val progress = current.asStateFlow()

    fun report(id: String, value: DownloadTransfer) {
        current.update { it + (id to value) }
    }

    fun clear(id: String) {
        current.update { it - id }
    }
}

fun chapterDirectory(context: Context, id: String) = File(context.filesDir, "chapters/$id")

fun pageFile(context: Context, id: String, page: Int) =
    File(chapterDirectory(context, id), "$page.img")

suspend fun enqueueDownload(
    context: Context,
    chapter: app.komori.domain.Release,
    settings: Settings,
) {
    val app = context.applicationContext as KomoriApp
    if (app.dao.download(chapter.id)?.state in DownloadPolicy.activeStates) return
    withContext(kotlinx.coroutines.NonCancellable) {
        ChapterFiles.locked(chapter.id) {
            val existing = app.dao.download(chapter.id)
            if (existing?.state in (DownloadPolicy.activeStates + "Complete")) return@locked
            app.database.withTransaction {
                val position = app.dao.lastQueuePosition().let { if (it == Long.MAX_VALUE) throw IllegalStateException("Download queue position limit reached.") else it + 1 }
                app.dao.putDownload(existing?.copy(state = "Queued", error = "", queuePosition = position)
                    ?: Download(chapter.id, chapter.mangaId, chapter.label, chapter.pages.coerceAtLeast(0), dataSaver = settings.saver, queuePosition = position))
            }
        }
        kickDownloadQueue(app)
    }
}

/** A delayed continuation shares the original unique chain so pause/delete cancels both. */
internal fun downloadRequest(
    id: String,
    settings: Settings,
    attemptBase: Int = 0,
    delayMillis: Long = 0,
) =
    OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
        .setInputData(workDataOf("chapter" to id, "attemptBase" to attemptBase))
        .setInitialDelay(
            delayMillis.coerceIn(
                0,
                Long.MAX_VALUE - System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1),
            ),
            TimeUnit.MILLISECONDS,
        )
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(
                    if (settings.wifi) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .setRequiresStorageNotLow(true)
                .build()
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .apply { if (delayMillis <= 0) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
        .build()

class ChapterDownloadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    internal var elapsedMillis: () -> Long = { android.os.SystemClock.elapsedRealtime() }
    internal var continuation: suspend (KomoriApp, String, Long, Long) -> Unit = { app, id, failures, delay -> scheduleContinuation(app, id, failures, delay) }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val record = inputData.getString("chapter")?.let { (applicationContext as KomoriApp).dao.download(it) }
        return downloadForegroundInfo(applicationContext, record?.label ?: "Komori download")
    }
    override suspend fun doWork(): Result {
        val power = applicationContext.getSystemService(android.os.PowerManager::class.java)
        val wakeLock = power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Komori:ChapterDownload")
        try {
            setForeground(getForegroundInfo())
            wakeLock.acquire(TimeUnit.MINUTES.toMillis(9))
            return download((applicationContext as KomoriApp).source, enforceQueue = true)
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
            if (!isStopped) withContext(kotlinx.coroutines.NonCancellable) { kickDownloadQueue(applicationContext) }
        }
    }

    internal suspend fun download(source: Sources, enforceQueue: Boolean = false): Result =
        gate.withLock {
            withContext(Dispatchers.IO) {
                val app = applicationContext as KomoriApp
                val id = inputData.getString("chapter") ?: return@withContext Result.failure()
                val head = app.dao.queueHead()
                if (enforceQueue && head != null && head.releaseId != id) return@withContext Result.success()
                ChapterFiles.locked(id) chapter@{
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    var record = app.dao.download(id) ?: return@chapter Result.failure()
                    if (record.state !in DownloadPolicy.activeStates)
                        return@chapter Result.failure()
                    val attempt =
                        inputData.getInt("attemptBase", 0).coerceAtLeast(0).toLong() +
                            runAttemptCount
                    try {
                        if (app.filesDir.usableSpace < 64L * 1024 * 1024) throw StorageFailure("Free at least 64 MB to continue downloading.")
                        record = record.copy(state = "Downloading", error = "")
                        app.dao.putDownload(record)
                        DownloadTransfers.report(id, DownloadTransfer(resolving = true))
                        val urls = source.pages(id, record.dataSaver)
                        if (urls.isEmpty())
                            throw ContentFailure("This release has no downloadable pages.")
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
                            storageOperation { manifest.writeText(identity) }
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
                        val started = elapsedMillis()
                        urls.forEachIndexed { index, url ->
                            if (isStopped) throw CancellationException()
                            if (elapsedMillis() - started > 8 * 60 * 1000) {
                                continuation(app, id, attempt, 0)
                                app.dao.putDownload(record.copy(state = "Queued"))
                                return@chapter Result.success()
                            }
                            val file = pageFile(app, id, index)
                            if (repairMarker(app, id, index).exists() || !validImage(file)) {
                                if (directory.usableSpace < 64L * 1024 * 1024)
                                    throw StorageFailure(
                                        "Free at least 64 MB to continue downloading."
                                    )
                                val temporary = storageOperation {
                                    File.createTempFile("page-$index-", ".part", directory)
                                }
                                try {
                                    DownloadTransfers.report(
                                        id,
                                        DownloadTransfer(
                                            page = index + 1,
                                            completedPages = record.done,
                                        ),
                                    )
                                    var lastProgress = 0L
                                    source.transferPage(id, url, temporary) { bytes, total ->
                                        val now = System.nanoTime()
                                        if (now - lastProgress >= 250_000_000L || bytes == total) {
                                            lastProgress = now
                                            DownloadTransfers.report(
                                                id,
                                                DownloadTransfer(
                                                    index + 1,
                                                    bytes,
                                                    total,
                                                    completedPages = record.done,
                                                ),
                                            )
                                        }
                                    }
                                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                    if (!temporary.renameTo(file))
                                        throw StorageFailure("Could not save page ${index + 1}")
                                    writePageDigest(file)
                                    repairMarker(app, id, index).delete()
                                    record = record.copy(done = record.done + 1)
                                } finally {
                                    temporary.delete()
                                }
                            }
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            app.dao.putDownload(record)
                            if (enforceQueue) setForeground(downloadForegroundInfo(app, record.label, record.done, record.total))
                        }
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        app.dao.putDownload(record.copy(state = "Complete"))
                        Result.success()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val needsAction = requiresSourceAction(e)
                        val storageWait = failureKind(e) == FailureKind.STORAGE
                        val retry = (automaticRetryAllowed(e) && attempt < 3) || storageWait
                        app.dao.putDownload(
                            record.copy(
                                state =
                                    if (needsAction) "Verification required"
                                    else if (storageWait) "Waiting for storage"
                                    else if (retry) "Retrying" else "Failed",
                                error = connectionMessage(e),
                            )
                        )
                        if (retry) {
                            val delayMillis =
                                (retryAtMillis(e) - System.currentTimeMillis()).coerceAtLeast(0)
                            if (delayMillis > 0) {
                                continuation(app, id, attempt + 1, delayMillis)
                                Result.success()
                            } else Result.retry()
                        } else Result.failure()
                    } finally {
                        DownloadTransfers.clear(id)
                    }
                }
            }
        }

    private suspend fun scheduleContinuation(app: KomoriApp, id: String, failures: Long, delayMillis: Long) {
        WorkManager.getInstance(app).enqueueUniqueWork(
            "download-$id", ExistingWorkPolicy.APPEND_OR_REPLACE,
            downloadRequest(id, app.settings.flow.first(), failures.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), delayMillis),
        ).result.get()
    }

    companion object {
        private val gate = Mutex()
    }
}
