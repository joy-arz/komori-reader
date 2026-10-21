package app.komori

import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.data.*
import app.komori.ui.ReaderViewModel
import java.util.UUID
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PartialOfflineRefreshTest {
    @Test fun refreshingPartialOfflineReleaseDoesNotRequireAnOnlineManifest() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KomoriApp
        val book = UUID.randomUUID().toString()
        val id = UUID.randomUUID().toString()
        val chapter = Chapter(id, book, JSONObject().put("id", id)
            .put("attributes", JSONObject().put("chapter", "1").put("translatedLanguage", "en").put("pages", 3))
            .put("relationships", JSONArray().put(JSONObject().put("type", "manga").put("id", book))).toString())
        val vm = withContext(Dispatchers.Main) { ReaderViewModel(app) }
        try {
            app.dao.putManga(Manga(book, "Partial offline fixture", "", ""))
            app.dao.putDownload(Download(id, book, "Chapter 1", 3, 1, "Needs repair"))
            chapterDirectory(app, id).mkdirs()
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            pageFile(app, id, 1).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val retained = pageFile(app, id, 1).readBytes()
            withContext(Dispatchers.Main) { vm.read(chapter.release().copy(local = true), requestedPage = 1) }
            withTimeout(5000) { while (!vm.readerReady.value) delay(25) }
            val session = vm.readerSession.value
            withContext(Dispatchers.Main) { vm.refreshReader() }
            withTimeout(5000) { while (vm.readerSession.value <= session || !vm.readerReady.value) delay(25) }
            assertEquals(1, vm.readerPage.value)
            assertTrue(vm.reader.value!!.local)
            assertArrayEquals(retained, pageFile(app, id, 1).readBytes())
            assertFalse(repairMarker(app, id, 1).exists())
            assertEquals("Needs repair", app.dao.download(id)!!.state)
        } finally {
            withContext(Dispatchers.Main) { vm.closeReader(); vm.viewModelScope.cancel() }
            chapterDirectory(app, id).deleteRecursively()
            app.dao.removeDownload(id); app.dao.resetProgress(book)
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM shelf WHERE mangaId = ?", arrayOf(book))
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM manga WHERE id = ?", arrayOf(book))
        }
    }
}
