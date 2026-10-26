package app.komori

import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.domain.Release
import app.komori.ui.ReaderCacheAccess
import app.komori.ui.ReaderViewModel
import app.komori.ui.WebtoonStripCache
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ReaderCacheStabilityTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KomoriApp

    private fun writeImage(file: File, color: Int) {
        file.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        try { file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
    }

    @Test fun repeatedReaderCacheHitsRetainTheDecodedStrip() = runBlocking<Unit> {
        val chapter = Release("stable-${UUID.randomUUID()}", "book", "1", null, "", "en", emptyList(), "", 1)
        val vm = withContext(Dispatchers.Main) { ReaderViewModel(app) }
        val stored = app.settings.flow.first()
        withTimeout(5000) { vm.settings.first { it.saver == stored.saver } }
        val file = File(app.cacheDir, "reader/${chapter.id}-0-${stored.saver}.img")
        try {
            writeImage(file, android.graphics.Color.BLUE)
            assertTrue(file.setLastModified(System.currentTimeMillis() - 60000))
            val modified = file.lastModified()
            val key = WebtoonStripCache.key(file, 0, 64, 32)
            val decoded = WebtoonStripCache.load(file, 0, 64, 32, 32)
            repeat(10) {
                assertEquals(file, vm.pageFileFor(chapter, 0))
                assertEquals("A cache hit must not change image identity", key, WebtoonStripCache.key(file, 0, 64, 32))
                assertEquals(modified, file.lastModified())
                assertSame(decoded, WebtoonStripCache.load(file, 0, 64, 32, 32))
            }
            assertTrue(ReaderCacheAccess.lastAccess(file) > modified)
        } finally {
            val job = vm.viewModelScope.coroutineContext[Job]
            withContext(Dispatchers.Main) { vm.viewModelScope.cancel() }
            job?.join()
            file.delete()
        }
    }

    @Test fun replacingAnImageStillInvalidatesItsDecodedStrip() = runBlocking<Unit> {
        val file = File(app.cacheDir, "reader/revision-${UUID.randomUUID()}.img")
        try {
            writeImage(file, android.graphics.Color.BLUE)
            assertTrue(file.setLastModified(System.currentTimeMillis() - 60000))
            val originalKey = WebtoonStripCache.key(file, 0, 64, 32)
            val original = WebtoonStripCache.load(file, 0, 64, 32, 32)
            writeImage(file, android.graphics.Color.RED)
            assertNotEquals(originalKey, WebtoonStripCache.key(file, 0, 64, 32))
            val replacement = WebtoonStripCache.load(file, 0, 64, 32, 32)
            assertNotSame(original, replacement)
            assertEquals(android.graphics.Color.RED, replacement.getPixel(0, 0))
        } finally { file.delete() }
    }
}
