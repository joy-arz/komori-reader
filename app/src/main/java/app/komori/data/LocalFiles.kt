package app.komori.data

import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.room.withTransaction
import app.komori.KomoriApp
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val LOCAL_LIMIT = 512L * 1024 * 1024
private const val MAX_LOCAL_PAGES = 10000
private val localExtensions = setOf("jpg", "jpeg", "png", "webp")

fun naturalFileKey(name: String): String = Regex("\\d+").replace(name.lowercase(Locale.ROOT)) { match ->
    val digits = match.value.trimStart('0').ifEmpty { "0" }
    "${digits.length.toString().padStart(4, '0')}:$digits"
}

private suspend fun copyLocalBytes(input: InputStream, output: OutputStream, limit: Long): Long {
    val buffer = ByteArray(32 * 1024)
    var total = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > limit) throw ContentFailure("Local file exceeds its import/export size limit.")
        storageOperation { output.write(buffer, 0, count) }
    }
    return total
}

private class LimitedArchive(private val input: InputStream) : InputStream() {
    private var total = 0L
    override fun read(): Int = input.read().also { if (it >= 0) count(1) }
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int = input.read(bytes, offset, length).also { if (it > 0) count(it) }
    private fun count(size: Int) { total += size; if (total > 256L * 1024 * 1024) throw ContentFailure("CBZ archives are limited to 256 MB compressed.") }
    override fun close() = input.close()
}

object LocalFiles {
    suspend fun importArchive(app: KomoriApp, uri: Uri, progress: (String) -> Unit): Manga = withContext(Dispatchers.IO) {
        val title = displayName(app, uri).substringBeforeLast('.').ifBlank { "Imported comic" }.take(1000)
        stage(app, title, progress) { directory ->
            val pages = mutableListOf<Pair<String, File>>()
            val names = mutableSetOf<String>()
            var entries = 0
            var total = 0L
            val input = app.contentResolver.openInputStream(uri) ?: throw ContentFailure("Could not open the chosen CBZ.")
            ZipInputStream(LimitedArchive(input)).use { zip ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.nextEntry ?: break
                    entries++
                    if (entries > 20000 || entry.name.length > 512) throw ContentFailure("CBZ contains too many entries or an oversized file name.")
                    val name = entry.name.replace('\\', '/')
                    if (!names.add(name)) throw ContentFailure("CBZ contains duplicate entry names.")
                    if (entry.isDirectory) { zip.closeEntry(); continue }
                    if (name.substringAfterLast('.').lowercase(Locale.ROOT) !in localExtensions) {
                        val sink = object : OutputStream() { override fun write(value: Int) {} ; override fun write(bytes: ByteArray, offset: Int, length: Int) {} }
                        total += copyLocalBytes(zip, sink, minOf(1024L * 1024, LOCAL_LIMIT - total))
                    } else {
                        if (pages.size >= MAX_LOCAL_PAGES) throw ContentFailure("A local comic can contain at most 10,000 pages.")
                        ensureSpace(directory)
                        val file = File(directory, "staged-${pages.size}")
                        total += file.outputStream().use { copyLocalBytes(zip, it, minOf(MAX_IMAGE_BYTES, LOCAL_LIMIT - total)) }
                        if (!validImage(file)) throw ContentFailure("CBZ contains an unreadable JPEG, PNG or WebP page.")
                        pages += name to file
                        progress("Importing ${pages.size} pages")
                    }
                    zip.closeEntry()
                }
            }
            pages
        }
    }

    suspend fun importFolder(app: KomoriApp, tree: Uri, progress: (String) -> Unit): Manga = withContext(Dispatchers.IO) {
        val root = DocumentsContract.getTreeDocumentId(tree)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(tree, root)
        val title = displayName(app, rootUri).ifBlank { "Imported folder" }.take(1000)
        stage(app, title, progress) { directory ->
            val pages = mutableListOf<Pair<String, File>>()
            val pending = ArrayDeque<Triple<String, String, Int>>()
            val visited = mutableSetOf<String>()
            pending.add(Triple(root, "", 0))
            var total = 0L
            var entries = 0
            while (pending.isNotEmpty()) {
                val (parent, prefix, depth) = pending.removeFirst()
                if (!visited.add(parent)) continue
                if (visited.size > 20000 || depth > 8) throw ContentFailure("The selected folder exceeds the import depth/entry limit.")
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
                val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
                app.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        entries++
                        if (entries > 20000) throw ContentFailure("The selected folder contains too many entries.")
                        currentCoroutineContext().ensureActive()
                        val id = cursor.getString(0)
                        val name = cursor.getString(1).orEmpty()
                        if (name.length > 512 || id.length > 4096) throw ContentFailure("Folder contains an oversized file name.")
                        if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) pending.add(Triple(id, "$prefix$name/", depth + 1))
                        else if (name.substringAfterLast('.').lowercase(Locale.ROOT) in localExtensions) {
                            if (pages.size >= MAX_LOCAL_PAGES) throw ContentFailure("A local comic can contain at most 10,000 pages.")
                            ensureSpace(directory)
                            val page = File(directory, "staged-${pages.size}")
                            val document = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                            val input = app.contentResolver.openInputStream(document) ?: throw ContentFailure("Could not read a page from this folder.")
                            total += input.use { source -> page.outputStream().use { copyLocalBytes(source, it, minOf(MAX_IMAGE_BYTES, LOCAL_LIMIT - total)) } }
                            if (!validImage(page)) throw ContentFailure("Folder contains an unreadable image.")
                            pages += "$prefix$name" to page
                            progress("Importing ${pages.size} pages")
                        }
                    }
                } ?: throw ContentFailure("Could not list the selected folder.")
            }
            pages
        }
    }

    private suspend fun stage(app: KomoriApp, title: String, progress: (String) -> Unit, read: suspend (File) -> List<Pair<String, File>>): Manga {
        val token = UUID.randomUUID().toString()
        val stage = File(app.filesDir, "local-staging/$token").apply { if (!mkdirs()) throw StorageFailure("Could not prepare local import storage.") }
        val chapterId = sourceId("local", "/chapter/$token/")
        val destination = chapterDirectory(app, chapterId)
        var committed = false
        try {
            val pages = read(stage).sortedWith(compareBy<Pair<String, File>> { naturalFileKey(it.first) }.thenBy { it.first })
            if (pages.isEmpty()) throw ContentFailure("No supported JPEG, PNG or WebP pages were found.")
            pages.forEachIndexed { index, (_, file) ->
                if (!file.renameTo(File(stage, "$index.img"))) throw StorageFailure("Could not order imported pages.")
            }
            val mangaId = sourceId("local", "/book/$token/")
            val manga = Manga(mangaId, title, "Imported on this device. Original files are unchanged.", Uri.fromFile(File(destination, "0.img")).toString())
            val chapter = Chapter(chapterId, mangaId, JSONObject().put("id", chapterId)
                .put("attributes", JSONObject().put("chapter", "1").put("title", title).put("translatedLanguage", "en").put("pages", pages.size))
                .put("relationships", JSONArray().put(JSONObject().put("type", "manga").put("id", mangaId))).toString())
            progress("Saving imported comic")
            withContext(NonCancellable) {
                destination.parentFile!!.mkdirs()
                if (!stage.renameTo(destination)) throw StorageFailure("Could not publish imported pages.")
                app.database.withTransaction {
                    app.dao.putManga(manga)
                    app.dao.putChapters(listOf(chapter))
                    app.dao.saveFeed(mangaId, "en", listOf(chapter))
                    app.dao.putShelf(Shelf(mangaId, saved = true, touched = System.currentTimeMillis()))
                    app.dao.putDownload(Download(chapterId, mangaId, "Imported comic", pages.size, pages.size, "Complete"))
                }
                committed = true
            }
            return manga
        } finally {
            stage.deleteRecursively()
            if (!committed) destination.deleteRecursively()
        }
    }

    suspend fun export(app: KomoriApp, download: Download, uri: Uri?, tree: Uri?, progress: (String) -> Unit) = withContext(Dispatchers.IO) {
        ChapterFiles.locked(download.releaseId) {
            val current = app.dao.download(download.releaseId) ?: throw ContentFailure("This download no longer exists.")
            if (current.state != "Complete" || current.total <= 0) throw ContentFailure("Complete this chapter before exporting it.")
            val archive = File.createTempFile("comic-", ".cbz", app.cacheDir)
            try {
                var total = 0L
                ZipOutputStream(archive.outputStream()).use { zip ->
                    zip.setLevel(0)
                    repeat(current.total) { index ->
                        currentCoroutineContext().ensureActive()
                        ensureSpace(archive.parentFile!!)
                        val page = pageFile(app, current.releaseId, index)
                        if (repairMarker(app, current.releaseId, index).exists() || !validImage(page)) throw ContentFailure("A downloaded page needs repair before export.")
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(page.path, bounds)
                        val extension = when (bounds.outMimeType) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; "image/avif" -> "avif"; else -> throw ContentFailure("This image format cannot be exported as CBZ.") }
                        zip.putNextEntry(ZipEntry("${(index + 1).toString().padStart(5, '0')}.$extension"))
                        total += page.inputStream().use { copyLocalBytes(it, zip, minOf(MAX_IMAGE_BYTES, LOCAL_LIMIT - total)) }
                        zip.closeEntry()
                        progress("Exporting ${index + 1} / ${current.total} pages")
                    }
                }
                val destination = uri ?: tree?.let { folder ->
                    DocumentsContract.createDocument(app.contentResolver, DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder)),
                        "application/vnd.comicbook+zip", exportName(download))
                } ?: throw ContentFailure("Choose an export document or folder.")
                val output = app.contentResolver.openOutputStream(destination, "wt") ?: throw StorageFailure("Could not open the export destination.")
                output.use { target -> archive.inputStream().use { copyLocalBytes(it, target, LOCAL_LIMIT + 4L * 1024 * 1024) } }
            } finally { archive.delete() }
        }
    }

    fun exportName(download: Download) = download.label.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(100).ifBlank { "Comic" } + ".cbz"

    private fun displayName(app: KomoriApp, uri: Uri): String = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0).orEmpty() else ""
    }.orEmpty()

    private fun ensureSpace(directory: File) {
        if (directory.usableSpace < 64L * 1024 * 1024) throw StorageFailure("Free at least 64 MB to import or export pages.")
    }
}
