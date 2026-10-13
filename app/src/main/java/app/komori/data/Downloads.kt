package app.komori.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.work.*
import app.komori.KomoriApp
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request

fun chapterDirectory(context: Context, id: String) = File(context.filesDir, "chapters/$id")

fun pageFile(context: Context, id: String, page: Int) =
    File(chapterDirectory(context, id), "$page.img")

suspend fun enqueueDownload(
    context: Context,
    chapter: app.komori.domain.Release,
    settings: Settings,
) {
    val app = context.applicationContext as KomoriApp
    val existing = app.dao.download(chapter.id)
    if (existing?.state == "Complete") return
    app.dao.putDownload(
        existing?.copy(state = "Queued", error = "")
            ?: Download(
                chapter.id,
                chapter.mangaId,
                chapter.label,
                chapter.pages,
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

class ChapterDownloadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        gate.withLock {
            withContext(Dispatchers.IO) {
                val app = applicationContext as KomoriApp
                val id = inputData.getString("chapter") ?: return@withContext Result.failure()
                var record = app.dao.download(id) ?: return@withContext Result.failure()
                try {
                    val urls = app.source.pages(id, record.dataSaver)
                    if (urls.isEmpty()) throw IOException("This release has no downloadable pages.")
                    val directory = chapterDirectory(app, id).apply { mkdirs() }
                    // The manifest tracks image identity, not expiring delivery-host URLs.
                    val identity =
                        urls.joinToString("\n") {
                            it.substringAfterLast("/data/", it.substringAfterLast("/data-saver/"))
                        }
                    val manifest = File(directory, "manifest")
                    if (!manifest.exists() || manifest.readText() != identity) {
                        directory.listFiles()?.forEach { it.delete() }
                        manifest.writeText(identity)
                    }
                    record =
                        record.copy(state = "Downloading", total = urls.size, done = 0, error = "")
                    app.dao.putDownload(record)
                    val started = System.currentTimeMillis()
                    urls.forEachIndexed { index, url ->
                        if (isStopped) throw CancellationException()
                        if (System.currentTimeMillis() - started > 8 * 60 * 1000) {
                            app.dao.putDownload(record.copy(state = "Queued"))
                            return@withContext Result.retry()
                        }
                        val file = pageFile(app, id, index)
                        if (!file.exists()) {
                            if (directory.usableSpace < 64L * 1024 * 1024)
                                throw IOException("Free at least 64 MB to continue downloading.")
                            val temporary = File(directory, "$index.part")
                            try {
                                app.source.client
                                    .newCall(Request.Builder().url(url).build())
                                    .execute()
                                    .use { response ->
                                        if (!response.isSuccessful)
                                            throw IOException(
                                                "Page ${index + 1}: HTTP ${response.code}"
                                            )
                                        val body =
                                            response.body
                                                ?: throw IOException("Empty image response")
                                        temporary.outputStream().use { output ->
                                            body.byteStream().use { it.copyTo(output) }
                                        }
                                    }
                                val bounds =
                                    BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(temporary.path, bounds)
                                if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                                    throw IOException("Page ${index + 1} is not a valid image")
                                if (!temporary.renameTo(file))
                                    throw IOException("Could not save page ${index + 1}")
                            } finally {
                                temporary.delete()
                            }
                        }
                        record = record.copy(done = index + 1)
                        app.dao.putDownload(record)
                    }
                    app.dao.putDownload(record.copy(state = "Complete"))
                    Result.success()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    app.dao.putDownload(
                        record.copy(
                            state = if (runAttemptCount < 3) "Retrying" else "Failed",
                            error = e.message ?: "Download failed",
                        )
                    )
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
            }
        }

    companion object {
        private val gate = Mutex()
    }
}
