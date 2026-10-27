package app.komori.data

import android.graphics.BitmapFactory
import app.komori.KomoriApp
import app.komori.domain.DownloadPolicy
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal fun imageDigest(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun writePageDigest(file: File) {
    val target = File(file.parentFile, "${file.name}.sha256")
    val temporary = File(file.parentFile, "${file.name}.sha256.part")
    try {
        temporary.writeText(imageDigest(file))
        if (!temporary.renameTo(target)) throw IOException("Could not save page integrity information.")
    } catch (e: Exception) {
        target.delete()
        throw e
    } finally { temporary.delete() }
}

internal fun decodableImage(file: File): Boolean {
    if (!validImage(file) || file.length() > MAX_IMAGE_BYTES) return false
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
    val image = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
    image.recycle()
    return true
}

data class DownloadStorage(val bytes: Long = 0, val chapters: Int = 0)
data class ReadDownloadPreview(val chapters: List<Download>, val bytes: Long, val titles: Map<String, String>)
data class IntegrityReport(val checked: Int, val damaged: Int, val baselined: Int, val skipped: Int)

class DownloadMaintenance(private val app: KomoriApp) {
    suspend fun storage(): DownloadStorage = withContext(Dispatchers.IO) {
        val rows = app.dao.observeDownloads().first()
        DownloadStorage(rows.sumOf { directoryBytes(chapterDirectory(app, it.releaseId)) }, rows.size)
    }

    suspend fun scan(onlyIds: Set<String>? = null, status: (String) -> Unit): IntegrityReport = withContext(Dispatchers.IO) {
        val rows = app.dao.observeDownloads().first().filter { onlyIds == null || it.releaseId in onlyIds }
        var checked = 0; var damaged = 0; var baselined = 0; var skipped = 0
        for ((index, row) in rows.withIndex()) {
            currentCoroutineContext().ensureActive()
            status("Checking chapter ${index + 1} of ${rows.size}…")
            if (row.state in DownloadPolicy.activeStates) { skipped++; continue }
            ChapterFiles.locked(row.releaseId) {
                val latest = app.dao.download(row.releaseId)
                if (latest == null || latest.state in DownloadPolicy.activeStates || latest.total <= 0) { skipped++; return@locked }
                checked++
                var valid = 0
                val invalidPages = mutableListOf<Int>()
                for (page in 0 until latest.total) {
                    currentCoroutineContext().ensureActive()
                    val file = pageFile(app, row.releaseId, page)
                    val signature = File(file.parentFile, "${file.name}.sha256")
                    val good = !repairMarker(app, row.releaseId, page).exists() && decodableImage(file) &&
                        (!signature.exists() || signature.length() == 64L && signature.readText() == imageDigest(file))
                    if (good) {
                        valid++
                        if (!signature.exists()) { writePageDigest(file); baselined++ }
                    } else {
                        damaged++
                        invalidPages += page
                    }
                }
                withContext(kotlinx.coroutines.NonCancellable) {
                    ChapterFiles.invalidate(row.releaseId)
                    invalidPages.forEach { page ->
                        val marker = repairMarker(app, row.releaseId, page)
                        if (!marker.exists()) marker.writeText("")
                    }
                    app.dao.putDownload(latest.copy(done = valid, state = if (valid == latest.total) "Complete" else "Needs repair",
                        error = if (valid == latest.total) "" else "${latest.total - valid} pages failed the integrity scan. Other saved pages are retained."))
                }
            }
        }
        IntegrityReport(checked, damaged, baselined, skipped)
    }

    suspend fun previewRead(): ReadDownloadPreview = withContext(Dispatchers.IO) {
        val completed = app.dao.observeProgress().first().filter { it.completed }.map { it.releaseId }.toSet()
        val rows = app.dao.observeDownloads().first().filter { it.releaseId in completed && it.state !in DownloadPolicy.activeStates }
        val titles = rows.map { it.mangaId }.distinct().associateWith { app.dao.manga(it)?.title ?: "Unknown title" }
        ReadDownloadPreview(rows, rows.sumOf { directoryBytes(chapterDirectory(app, it.releaseId)) }, titles)
    }

    suspend fun deleteRead(preview: ReadDownloadPreview): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        for (row in preview.chapters) {
            currentCoroutineContext().ensureActive()
            ChapterFiles.locked(row.releaseId) {
                val latest = app.dao.download(row.releaseId) ?: return@locked
                if (latest != row || latest.state in DownloadPolicy.activeStates || app.dao.progress(row.releaseId)?.completed != true) return@locked
                withContext(kotlinx.coroutines.NonCancellable) {
                    ChapterFiles.requireNotReading(row.releaseId)
                    ChapterFiles.invalidate(row.releaseId)
                    val directory = chapterDirectory(app, row.releaseId)
                    if (directory.exists() && !directory.deleteRecursively()) throw IOException("Could not delete ${row.label}. Retry after checking storage.")
                    app.dao.removeDownload(row.releaseId)
                    deleted++
                }
            }
        }
        deleted
    }
}

internal suspend fun directoryBytes(directory: File): Long {
    var bytes = 0L
    if (directory.exists()) for (file in directory.walkTopDown()) {
        currentCoroutineContext().ensureActive()
        if (file.isFile) bytes += file.length()
    }
    return bytes
}
