package app.komori

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.data.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DownloadMaintenanceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KomoriApp
    private fun image(id: String, page: Int, color: Int = 0) {
        chapterDirectory(app, id).mkdirs()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        pageFile(app, id, page).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun scanFindsMissingAndChangedPagesRetainsGoodPagesAndSkipsActiveChapters() = runBlocking {
        val id = "scan-${UUID.randomUUID()}"
        val active = "active-${UUID.randomUUID()}"
        try {
            image(id, 0); image(id, 1)
            writePageDigest(pageFile(app, id, 1))
            image(id, 1, -1) // Still a decodable image, but bytes differ from its saved checksum.
            val retained = pageFile(app, id, 0).readBytes()
            app.dao.putDownload(Download(id, id, "Scan fixture", 3, 3, "Complete"))
            app.dao.putDownload(Download(active, active, "Active fixture", 1, 0, "Downloading"))
            val result = DownloadMaintenance(app).scan(setOf(id, active)) { }
            assertEquals(1, result.checked)
            assertEquals(2, result.damaged)
            assertEquals(1, result.baselined)
            assertEquals(1, result.skipped)
            assertEquals("Needs repair", app.dao.download(id)!!.state)
            assertEquals(1, app.dao.download(id)!!.done)
            assertArrayEquals(retained, pageFile(app, id, 0).readBytes())
            assertTrue(repairMarker(app, id, 1).exists())
            assertTrue(repairMarker(app, id, 2).exists())
            assertEquals("Downloading", app.dao.download(active)!!.state)
            assertFalse(repairMarker(app, active, 0).exists())
            image(id, 1); writePageDigest(pageFile(app, id, 1)); repairMarker(app, id, 1).delete()
            image(id, 2); writePageDigest(pageFile(app, id, 2)); repairMarker(app, id, 2).delete()
            val second = DownloadMaintenance(app).scan(setOf(id)) { }
            assertEquals(0, second.damaged)
            assertEquals("Complete", app.dao.download(id)!!.state)
        } finally {
            listOf(id, active).forEach { chapterDirectory(app, it).deleteRecursively(); app.dao.removeDownload(it) }
        }
    }

    @Test fun deletePreviewRechecksProgressAndQueueStateAndKeepsLibraryHistory() = runBlocking {
        val ids = List(4) { "cleanup-${UUID.randomUUID()}" }
        try {
            for (id in ids) {
                app.dao.putManga(Manga(id, id, "", ""))
                app.dao.putShelf(Shelf(id, saved = true))
                app.dao.putProgress(Progress(id, id, "$id-1", completed = true))
                image(id, 0)
                app.dao.putDownload(Download(id, id, "Read fixture", 1, 1, "Complete"))
            }
            val repository = DownloadMaintenance(app)
            val full = repository.previewRead()
            val preview = full.copy(chapters = full.chapters.filter { it.releaseId in ids })
            assertEquals(4, preview.chapters.size)
            assertTrue(preview.bytes > 0)
            app.dao.putProgress(app.dao.progress(ids[1])!!.copy(completed = false))
            app.dao.putDownload(app.dao.download(ids[2])!!.copy(state = "Queued"))
            app.dao.putDownload(app.dao.download(ids[3])!!.copy(total = 2))
            assertEquals(1, repository.deleteRead(preview))
            assertNull(app.dao.download(ids[0]))
            assertFalse(chapterDirectory(app, ids[0]).exists())
            assertTrue(app.dao.shelf(ids[0])!!.saved)
            assertTrue(app.dao.progress(ids[0])!!.completed)
            ids.drop(1).forEach { assertNotNull(app.dao.download(it)); assertTrue(pageFile(app, it, 0).exists()) }
        } finally {
            ids.forEach { chapterDirectory(app, it).deleteRecursively(); app.dao.removeDownload(it); app.dao.resetProgress(it)
                app.database.openHelper.writableDatabase.execSQL("DELETE FROM shelf WHERE mangaId = ?", arrayOf(it))
                app.database.openHelper.writableDatabase.execSQL("DELETE FROM manga WHERE id = ?", arrayOf(it)) }
        }
    }
}
