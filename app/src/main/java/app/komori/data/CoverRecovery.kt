package app.komori.data

import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Only failed HentaiZap thumbnails need a gallery lookup; bound and coalesce those lookups. */
class CoverRecovery(
    private val details: suspend (Manga) -> Manga,
    private val clock: () -> Long = System::nanoTime,
) {
    private data class Entry(val cover: String?, val checkedAt: Long)
    private val lock = Mutex()
    private val entries = object : LinkedHashMap<Pair<String, String>, Entry>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String>, Entry>?) = size > 128
    }

    suspend fun resolve(manga: Manga): String? {
        if (sourceKey(manga.id) != "zap") return null
        return lock.withLock {
            val key = manga.id to manga.cover
            val cached = entries[key]
            val lifetime = if (cached?.cover == null) 60_000_000_000L else 600_000_000_000L
            if (cached != null && clock() - cached.checkedAt in 0 until lifetime) return@withLock cached.cover
            val cover = try {
                details(manga).cover.takeIf {
                    it != manga.cover && it.toHttpUrlOrNull()?.isHttps == true
                }
            } catch (_: IOException) { null }
            entries[key] = Entry(cover, clock())
            cover
        }
    }
}
