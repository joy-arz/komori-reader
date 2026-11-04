package app.komori.data

import android.content.Context
import app.komori.KomoriApp
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Persistent download-owned covers. Metadata and chapter ownership remain in Room. */
object OfflineTitleAssets {
    private val lock = Mutex()
    fun cover(context: Context, id: String): File {
        val token = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "download-covers/$token.img")
    }
    suspend fun retain(app: KomoriApp, manga: Manga, source: Sources) = lock.withLock {
        withContext(Dispatchers.IO) {
            if (manga.cover.isBlank() || !app.dao.hasDownloads(manga.id)) return@withContext
            val target = cover(app, manga.id)
            if (validImage(target)) return@withContext
            target.parentFile!!.mkdirs()
            val temporary = File(target.parentFile, "${target.name}.part")
            try {
                source.transferPage(manga.id, manga.cover, temporary)
                if (!validImage(temporary)) throw IOException("Downloaded cover is not a supported image.")
                if (!temporary.renameTo(target)) throw IOException("Could not store the downloaded cover.")
            } finally { temporary.delete() }
        }
    }
    suspend fun removeIfUnused(app: KomoriApp, id: String) = lock.withLock {
        withContext(Dispatchers.IO) {
            if (!app.dao.hasDownloads(id)) {
                cover(app, id).delete()
                File(cover(app, id).path + ".part").delete()
            }
        }
    }
}
