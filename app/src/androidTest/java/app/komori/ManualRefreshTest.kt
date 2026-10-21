package app.komori

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.komori.data.*
import app.komori.ui.KomoriScreen
import app.komori.ui.ReaderViewModel
import app.komori.ui.UpdateViewModel
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ManualRefreshTest {
    @Test fun headersExposeRefreshAndOfflineReaderRefreshPreservesPageAndFiles() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as KomoriApp
        val device = UiDevice.getInstance(instrumentation)
        val token = UUID.randomUUID().toString()
        val book = Manga(sourceId("local", "/book/$token/"), "Refresh fixture", "", "")
        val id = sourceId("local", "/chapter/$token/")
        val chapter = Chapter(id, book.id, JSONObject().put("id", id)
            .put("attributes", JSONObject().put("chapter", "1").put("translatedLanguage", "en").put("pages", 3))
            .put("relationships", JSONArray().put(JSONObject().put("type", "manga").put("id", book.id))).toString())
        val creator = Creator(sourceId("zap", "/artist/refresh-$token/"), "Refresh author", "artist")
        val requests = AtomicInteger()
        val vm = withContext(Dispatchers.Main) { ReaderViewModel(app) { _, _, _ ->
            requests.incrementAndGet()
            BrowsePage(listOf(Manga("refresh-work", "Refresh author work", "", "")), false)
        } }
        app.dao.putManga(book)
        app.dao.putShelf(Shelf(book.id, saved = true))
        app.dao.saveFeed(book.id, "en", listOf(chapter))
        app.dao.putDownload(Download(id, book.id, "Chapter 1", 3, 3, "Complete"))
        app.dao.putProgress(Progress(id, book.id, chapter.release().key, 1, scroll = .25f))
        app.dao.putTitlePreferences(TitlePreferences(book.id, readerMode = "webtoon"))
        app.dao.putCreator(creator)
        chapterDirectory(app, id).mkdirs()
        repeat(3) { index ->
            val bitmap = Bitmap.createBitmap(64, 1024, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(40 + index * 30, 100, 60))
            pageFile(app, id, index).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val original = pageFile(app, id, 1).readBytes()
        val activity = instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun shown(description: String) = device.wait(Until.findObject(By.desc(description)), 10_000)
            ?: throw AssertionError("Missing $description")
        suspend fun settled(condition: () -> Boolean) = withTimeout(15_000) { while (!condition()) delay(50) }
        try {
            instrumentation.runOnMainSync {
                ViewModelProvider(activity)[UpdateViewModel::class.java].dismiss()
                activity.setContent { KomoriScreen(vm) }
            }
            shown("Refresh library")
            instrumentation.runOnMainSync { vm.currentTab.value = 2 }
            val refreshAuthors = shown("Refresh author works")
            val before = requests.get()
            refreshAuthors.click()
            settled { requests.get() > before && !vm.authorFeed.value.loading }
            instrumentation.runOnMainSync { vm.activeSource.value = "zap"; vm.currentTab.value = 1 }
            shown("Refresh discover")
            instrumentation.runOnMainSync { vm.open(book) }
            shown("Refresh title details").click()
            settled { !vm.loading.value && vm.chapters.value.isNotEmpty() }
            assertEquals(book.id, vm.selected.value!!.id)
            assertEquals(id, vm.chapters.value.single().id)
            instrumentation.runOnMainSync { vm.read(chapter.release(), requestedPage = 1) }
            shown("Refresh reading")
            settled { vm.readerReady.value && vm.readerPage.value == 1 }
            val session = vm.readerSession.value
            shown("Refresh reading").click()
            settled { vm.readerSession.value > session && vm.readerReady.value }
            assertEquals(id, vm.reader.value!!.id)
            assertEquals(1, vm.readerPage.value)
            assertArrayEquals(original, pageFile(app, id, 1).readBytes())
            assertFalse(repairMarker(app, id, 1).exists())
            assertEquals("Complete", app.dao.download(id)!!.state)
            device.takeScreenshot(File(app.getExternalFilesDir(null), "reader-manual-refresh.png"))
            val cached = File(app.cacheDir, "reader/$id-1-true.img")
            cached.parentFile!!.mkdirs()
            cached.writeBytes(original)
            instrumentation.runOnMainSync { vm.clearReaderCache() }
            assertTrue("Active reader cache must be retained", cached.exists())
            instrumentation.runOnMainSync { vm.closeReader(); vm.clearReaderCache() }
            settled { !vm.clearingCache.value }
            assertFalse(cached.exists())
            assertArrayEquals(original, pageFile(app, id, 1).readBytes())
        } finally {
            instrumentation.runOnMainSync { vm.closeReader(); vm.viewModelScope.cancel(); activity.finish() }
            chapterDirectory(app, id).deleteRecursively()
            app.dao.removeDownload(id); app.dao.resetProgress(book.id); app.dao.removeCreator(creator.id)
            val db = app.database.openHelper.writableDatabase
            db.execSQL("DELETE FROM chapter_feeds WHERE mangaId = ?", arrayOf(book.id))
            db.execSQL("DELETE FROM chapter_index WHERE mangaId = ?", arrayOf(book.id))
            db.execSQL("DELETE FROM chapters WHERE mangaId = ?", arrayOf(book.id))
            db.execSQL("DELETE FROM chapter_preferences WHERE mangaId = ?", arrayOf(book.id))
            db.execSQL("DELETE FROM shelf WHERE mangaId = ?", arrayOf(book.id))
            db.execSQL("DELETE FROM manga WHERE id = ?", arrayOf(book.id))
        }
    }
}
