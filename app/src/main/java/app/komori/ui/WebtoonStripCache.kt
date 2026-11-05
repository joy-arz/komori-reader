package app.komori.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.util.LruCache
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Cache only viewport-sized strips, never a complete long webtoon image. */
internal object WebtoonStripCache {
    private val cache =
        object :
            LruCache<String, Bitmap>(
                minOf(8L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 32).toInt()
            ) {
            override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
            // Do not recycle on eviction: a visible composable may still hold this bitmap.
        }
    private val decode = Mutex()

    fun key(file: File, top: Int, bottom: Int, width: Int) =
        "${file.path}:${file.lastModified()}:${file.length()}:$top:$bottom:$width"

    fun peek(key: String): Bitmap? = cache.get(key)

    @Suppress("DEPRECATION")
    suspend fun load(
        file: File,
        top: Int,
        bottom: Int,
        sourceWidth: Int,
        displayWidth: Int,
    ): Bitmap =
        withContext(Dispatchers.IO) {
          ReaderCacheFiles.leased(file) {
            val key = key(file, top, bottom, displayWidth)
            cache.get(key)
                ?: decode.withLock {
                    cache.get(key)
                        ?: run {
                            coroutineContext.ensureActive()
                            var sample = 1
                            while (sourceWidth / (sample * 2) >= displayWidth.coerceAtLeast(1)) sample *= 2
                            val bitmap = app.komori.data.ImageFormats.region(file, Rect(0, top, sourceWidth, bottom), sample)
                            coroutineContext.ensureActive()
                            cache.put(key, bitmap)
                            bitmap
                        }
                }
          }
        }
}
