package app.komori.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.os.Build
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

internal data class ImageInfo(val width: Int, val height: Int, val mime: String, val animated: Boolean = false) {
    val canAnimate: Boolean get() = animated && width.toLong() * height <= 4L * 1024 * 1024
}

internal object ImageFormats {
    fun info(file: File): ImageInfo? {
        if (!file.isFile || file.length() !in 1..MAX_IMAGE_BYTES) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth > 0 && options.outHeight > 0) {
            val mime = options.outMimeType.orEmpty()
            val animated = mime == "image/gif" || mime == "image/webp" && file.inputStream().use {
                val header = ByteArray(21)
                it.read(header) == 21 && String(header, 12, 4, Charsets.US_ASCII) == "VP8X" && header[20].toInt() and 2 != 0
            }
            return ImageInfo(options.outWidth, options.outHeight, mime, animated)
        }
        if (Build.VERSION.SDK_INT < 28) return null
        return try {
            var result: ImageInfo? = null
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                result = ImageInfo(info.size.width, info.size.height, info.mimeType, info.isAnimated)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(1, 1)
            }.recycle()
            result
        } catch (_: IOException) { null } catch (_: IllegalArgumentException) { null }
    }

    /** Keep normal long pages tiled; fallback decodes are bounded, including on API 26. */
    @Suppress("DEPRECATION")
    fun region(file: File, rect: Rect, sample: Int): Bitmap {
        val native = try { BitmapRegionDecoder.newInstance(file.path, false) } catch (_: IOException) { null }
        if (native != null) {
            try {
                native.decodeRegion(rect, BitmapFactory.Options().apply { inSampleSize = sample })?.let { return it }
            } finally { native.recycle() }
        }
        val info = info(file) ?: throw IOException("Unsupported or damaged image on this device")
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = (info.width / sample).coerceAtLeast(1)
                val height = (info.height / sample).coerceAtLeast(1)
                decoder.setTargetSize(width, height)
                decoder.crop = Rect((rect.left.toFloat() / sample).roundToInt(), (rect.top.toFloat() / sample).roundToInt(),
                    (rect.right.toFloat() / sample).roundToInt().coerceAtMost(width),
                    (rect.bottom.toFloat() / sample).roundToInt().coerceAtMost(height))
            }
        }
        var boundedSample = sample
        while (info.width.toLong() * info.height / boundedSample / boundedSample > 4L * 1024 * 1024) boundedSample *= 2
        val full = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = boundedSample })
            ?: throw IOException("Unsupported or damaged image on this device")
        try {
            val left = (rect.left / boundedSample).coerceIn(0, full.width - 1)
            val top = (rect.top / boundedSample).coerceIn(0, full.height - 1)
            return Bitmap.createBitmap(full, left, top,
                ((rect.width() + boundedSample - 1) / boundedSample).coerceIn(1, full.width - left),
                ((rect.height() + boundedSample - 1) / boundedSample).coerceIn(1, full.height - top)).let {
                    if (it === full) full.copy(full.config ?: Bitmap.Config.ARGB_8888, false) else it
                }
        } finally { full.recycle() }
    }

    fun exportExtension(mime: String): String? = when (mime) {
        "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"
        "image/avif" -> "avif"; "image/heif", "image/heic" -> "heic"; "image/bmp", "image/x-ms-bmp" -> "bmp"
        else -> null
    }
}
