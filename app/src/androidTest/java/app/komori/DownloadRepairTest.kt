package app.komori

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.data.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DownloadRepairTest {
    @Test
    fun failedRepairKeepsDurableStateAndOtherPagesThenCompletesAfterReplacement() = runBlocking {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as KomoriApp
        val id = "repair-test-${UUID.randomUUID()}"
        try {
            chapterDirectory(app, id).mkdirs()
            writePage(app, id, 0)
            writePage(app, id, 1)
            val retained = pageFile(app, id, 1).readBytes()
            app.dao.putDownload(Download(id, id, "Test", 2, 2, "Complete"))
            ChapterFiles.locked(id) { markPageForRepair(app, id, 0) }
            assertEquals("Needs repair", app.dao.download(id)!!.state)
            assertEquals(1, app.dao.download(id)!!.done)
            assertTrue(repairMarker(app, id, 0).exists())
            assertArrayEquals(retained, pageFile(app, id, 1).readBytes())
            // Re-read persisted files/row with no in-memory repair list.
            ChapterFiles.locked(id) { reconcileDownload(app, id) }
            assertEquals("Needs repair", app.dao.download(id)!!.state)
            writePage(app, id, 0)
            repairMarker(app, id, 0).delete()
            ChapterFiles.locked(id) { reconcileDownload(app, id) }
            assertEquals("Complete", app.dao.download(id)!!.state)
            assertEquals(2, app.dao.download(id)!!.done)
        } finally {
            chapterDirectory(app, id).deleteRecursively()
            app.dao.removeDownload(id)
        }
    }

    @Test
    fun missingFileCannotRemainComplete() = runBlocking {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as KomoriApp
        val id = "missing-test-${UUID.randomUUID()}"
        try {
            chapterDirectory(app, id).mkdirs()
            writePage(app, id, 0)
            app.dao.putDownload(Download(id, id, "Test", 2, 2, "Complete"))
            ChapterFiles.locked(id) { reconcileDownload(app, id) }
            assertEquals("Needs repair", app.dao.download(id)!!.state)
            assertEquals(1, app.dao.download(id)!!.done)
        } finally {
            chapterDirectory(app, id).deleteRecursively()
            app.dao.removeDownload(id)
        }
    }

    private fun writePage(app: KomoriApp, id: String, page: Int) {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        pageFile(app, id, page).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
