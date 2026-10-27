package app.komori

import android.graphics.Bitmap
import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.data.*
import app.komori.ui.ReaderCacheFiles
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Recorded measurements, deliberately no emulator-dependent timing thresholds. */
class PerformanceFollowupTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KomoriApp
    @Test fun sourceSessionConstructionAndLongWebtoonCacheAreMeasuredAndBounded() = runBlocking<Unit> {
        val sessions = mutableListOf<Double>()
        withContext(Dispatchers.Main) {
            repeat(3) { val start = System.nanoTime(); SourceSessions(app).client(); sessions += (System.nanoTime() - start) / 1e6 }
        }
        val directory = File(app.cacheDir, "webtoon-benchmark-${UUID.randomUUID()}").apply { mkdirs() }
        val owner = Any(); val budget = 64L * 1024 * 1024
        val bitmap = Bitmap.createBitmap(256, 4096, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(42)
        val pixels = IntArray(256 * 4096) { random.nextInt() or (0xff shl 24) }
        bitmap.setPixels(pixels, 0, 256, 0, 0, 256, 4096)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val before = Debug.getPss(); var peak = before; val times = mutableListOf<Double>()
        try {
            withContext(Dispatchers.IO) {
                repeat(80) { page ->
                    val start = System.nanoTime()
                    val temporary = ReaderCacheFiles.temporary(directory).apply { writeBytes(bytes) }
                    val target = File(directory, "$page.img")
                    val visible = ((page - 1).coerceAtLeast(0)..page).map { File(directory, "$it.img") }
                    ReaderCacheFiles.pin(owner, visible)
                    ReaderCacheFiles.publish(temporary, target, budget)
                    assertTrue(decodableImage(target))
                    assertTrue(visible.all { it.isFile })
                    assertTrue(directory.listFiles().orEmpty().sumOf { it.length() } <= budget + bytes.size)
                    times += (System.nanoTime() - start) / 1e6
                    if (page % 10 == 0) peak = maxOf(peak, Debug.getPss())
                }
            }
            val sorted = times.sorted()
            android.util.Log.i("KomoriBenchmark", "webtoon synthetic_pages=80 dimensions=256x4096 encoded_bytes=${bytes.size} median_publish_decode_ms=${sorted[40]} p95_ms=${sorted[76]} pss_before_kb=$before sampled_peak_kb=$peak pss_after_kb=${Debug.getPss()} cache_bytes=${directory.listFiles().orEmpty().sumOf { it.length() }} budget=$budget")
            android.util.Log.i("KomoriBenchmark", "source_sessions construction_ms=$sessions scope=warm_process_WebView_not_cold_phone")
        } finally { ReaderCacheFiles.release(owner); directory.deleteRecursively() }
    }
}
