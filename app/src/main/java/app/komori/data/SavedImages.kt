package app.komori.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Copies a verified reader page into the device picture library. Saved copies are independent
 * from chapter downloads: deleting one never deletes the other.
 */
internal object SavedImages {
    const val ALBUM = "Komori Reader"

    data class Draft(
        val mangaId: String,
        val releaseId: String,
        val mangaTitle: String,
        val chapterLabel: String,
        val page: Int,
    )

    fun needsLegacyPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    fun isMissing(context: Context, image: SavedImage): Boolean {
        val uri = Uri.parse(image.uri)
        if (uri.scheme == "file") return uri.path?.let { !File(it).isFile } ?: true
        if (uri.scheme != "content") return true
        return try {
            context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media._ID), null, null, null)
                ?.use { it.count == 0 } ?: true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun canOpen(context: Context, image: SavedImage): Boolean = try {
        context.contentResolver.openAssetFileDescriptor(Uri.parse(image.uri), "r")?.use { true } ?: false
    } catch (_: Exception) {
        false
    }

    suspend fun save(
        context: Context,
        source: File,
        info: ImageInfo,
        draft: Draft,
    ): SavedImage = withContext(Dispatchers.IO) {
        val extension = ImageFormats.exportExtension(info.mime)
            ?: throw ContentFailure("This image format cannot be saved to the gallery.")
        if (source.length() !in 1..MAX_IMAGE_BYTES) throw ContentFailure("This page image is empty or too large.")
        val id = UUID.randomUUID().toString()
        val name = savedImageFileName(draft, extension, id)
        val destination =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveToMediaStore(context, source, name, info.mime)
            else saveToLegacyPictures(context, source, name, info.mime)
        SavedImage(
            id = id,
            uri = destination.toString(),
            mangaId = draft.mangaId,
            releaseId = draft.releaseId,
            mangaTitle = draft.mangaTitle,
            chapterLabel = draft.chapterLabel,
            page = draft.page,
            fileName = name,
            mimeType = info.mime,
            width = info.width,
            height = info.height,
            savedAt = System.currentTimeMillis(),
        )
    }

    suspend fun delete(context: Context, image: SavedImage) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(image.uri)
        when (uri.scheme) {
            "content" -> try {
                val legacyPath =
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                        context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)
                            ?.use { if (it.moveToFirst()) it.getString(0) else null }
                    else null
                context.contentResolver.delete(uri, null, null)
                if (!legacyPath.isNullOrBlank()) File(legacyPath).delete()
            } catch (e: SecurityException) {
                throw StorageFailure("This saved image cannot be removed. Check gallery access and try again.", e)
            } catch (e: IOException) {
                throw StorageFailure("This saved image cannot be removed. Check gallery access and try again.", e)
            }
            "file" -> {
                val file = uri.path?.let(::File)
                if (file != null && file.exists() && !file.delete())
                    throw StorageFailure("This saved image cannot be removed. Check storage access and try again.")
            }
            else -> throw StorageFailure("This saved image location is no longer supported.")
        }
    }

    private suspend fun saveToMediaStore(context: Context, source: File, name: String, mime: String): Uri {
        val resolver = context.contentResolver
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values)
            ?: throw StorageFailure("Could not create a saved image in Pictures/$ALBUM.")
        try {
            val output = resolver.openOutputStream(uri)
                ?: throw StorageFailure("Could not open the saved image destination.")
            output.use { target ->
                source.inputStream().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_IMAGE_BYTES) throw ContentFailure("This page image is too large to save.")
                        target.write(buffer, 0, count)
                    }
                    if (total <= 0) throw ContentFailure("This page image is empty.")
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            if (e is ContentFailure || e is StorageFailure) throw e
            throw StorageFailure("Could not write this image to Pictures/$ALBUM.", e)
        }
    }

    private suspend fun saveToLegacyPictures(context: Context, source: File, name: String, mime: String): Uri {
        if (needsLegacyPermission(context))
            throw StorageFailure("Allow storage access to save images to Pictures/$ALBUM.")
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
        if (!directory.isDirectory && !directory.mkdirs())
            throw StorageFailure("Could not create Pictures/$ALBUM.")
        var target = File(directory, name)
        if (target.exists()) {
            val stem = name.substringBeforeLast('.')
            val extension = name.substringAfterLast('.')
            target = File(directory, "${stem}_${System.currentTimeMillis()}.$extension")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, target.name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.DATA, target.absolutePath)
            },
        ) ?: throw StorageFailure("Could not create a saved image in Pictures/$ALBUM.")
        try {
            val output = resolver.openOutputStream(uri)
                ?: throw StorageFailure("Could not open the saved image destination.")
            output.use { destination ->
                source.inputStream().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_IMAGE_BYTES) throw ContentFailure("This page image is too large to save.")
                        destination.write(buffer, 0, count)
                    }
                    if (total <= 0) throw ContentFailure("This page image is empty.")
                }
            }
            MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mime), null)
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            target.delete()
            if (e is ContentFailure || e is StorageFailure) throw e
            throw StorageFailure("Could not write this image to Pictures/$ALBUM.", e)
        }
    }
}

/** Builds a gallery-safe name while keeping the title, chapter and page recognizable. */
internal fun savedImageFileName(draft: SavedImages.Draft, extension: String, id: String): String {
    fun clean(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|\\s\\p{Cntrl}]+"), "_")
            .trim('_', ' ', '.')
    // Android storage limits names by bytes, not characters. Keep the unique suffix intact
    // even for long CJK or emoji titles by capping only the descriptive components.
    fun utf8Prefix(value: String, maxBytes: Int): String {
        if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value
        var bytes = 0
        var index = 0
        val out = StringBuilder()
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val piece = String(Character.toChars(codePoint))
            val size = piece.toByteArray(Charsets.UTF_8).size
            if (bytes + size > maxBytes) break
            out.append(piece)
            bytes += size
            index += Character.charCount(codePoint)
        }
        return out.toString().trim('_')
    }
    val title = utf8Prefix(clean(draft.mangaTitle), 96).ifBlank { "Komori" }
    val chapter = utf8Prefix(clean(draft.chapterLabel), 64)
    val base = listOf(
        "Komori",
        title,
        chapter,
        "p${draft.page + 1}",
        Integer.toHexString(draft.mangaId.hashCode()),
        id.take(8),
    )
        .filter { it.isNotBlank() }
        .joinToString("_")
    return "$base.${clean(extension).lowercase()}"
}
