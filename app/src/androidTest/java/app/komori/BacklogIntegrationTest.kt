package app.komori

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.komori.data.*
import app.komori.ui.ReaderViewModel
import app.komori.ui.SupportPage
import app.komori.ui.UpdateViewModel
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BacklogIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as KomoriApp
    private fun chapter(book: String, id: String, number: String, language: String = "en") = Chapter(id, book,
        JSONObject().put("id", id).put("attributes", JSONObject().put("chapter", number).put("translatedLanguage", language).put("pages", 3))
            .put("relationships", JSONArray().put(JSONObject().put("id", book).put("type", "manga"))).toString())

    @Test fun unreadCountsRetireMissingReleasesAndRespectLanguageAndAlternates() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).build()
        try {
            val dao = db.dao()
            val first = chapter("book", "one", "1")
            val alternate = chapter("book", "alt", "1")
            val second = chapter("book", "two", "2")
            dao.saveFeed("book", "en", listOf(first, alternate, second))
            dao.putChapters(listOf(chapter("book", "jp", "3", "ja")))
            var count = dao.observeLibraryCounts("en").first().single()
            assertEquals(2, count.total); assertEquals(2, count.unread)
            dao.putProgress(Progress(alternate.id, "book", alternate.release().key, 2, true))
            count = dao.observeLibraryCounts("en").first().single()
            assertEquals(1, count.unread)
            dao.saveFeed("book", "en", listOf(first, alternate))
            count = dao.observeLibraryCounts("en").first().single()
            assertEquals(1, count.total); assertEquals(0, count.unread)
            dao.putTitlePreferences(TitlePreferences("book", language = "ja"))
            count = dao.observeLibraryCounts("en").first().single()
            assertEquals(1, count.total); assertEquals(1, count.unread)
            dao.putCategory(LibraryCategory("c", "Favorites"))
            dao.putMembership(CategoryMembership("book", "c"))
            dao.deleteCategory("c")
            assertTrue(dao.backupMemberships().isEmpty())
            assertNotNull(dao.progress("alt"))
        } finally { db.close() }
    }

    @Test fun queueOrderSurvivesReopenAndCancellationRetainsRecord() = runBlocking {
        val name = "queue-${UUID.randomUUID()}"
        var db = Room.databaseBuilder(app, LibraryDatabase::class.java, name).build()
        try {
            db.dao().putDownload(Download("one", "book", "1", 2, 1, "Queued", queuePosition = 2))
            db.dao().putDownload(Download("two", "book", "2", 2, 0, "Waiting for storage", queuePosition = 1))
            db.close()
            db = Room.databaseBuilder(app, LibraryDatabase::class.java, name).build()
            assertEquals("two", db.dao().queueHead()!!.releaseId)
            db.dao().queuePosition("one", 0)
            assertEquals(listOf("one", "two"), db.dao().orderedQueue().map { it.releaseId })
            db.dao().putDownload(db.dao().download("one")!!.copy(state = "Cancelled"))
            assertEquals(1, db.dao().download("one")!!.done)
            assertEquals("two", db.dao().queueHead()!!.releaseId)
        } finally { db.close(); app.deleteDatabase(name) }
    }

    @Test fun cbzImportSortsPagesAndRejectsInvalidContentWithoutPublishing() = runBlocking {
        val token = UUID.randomUUID().toString()
        val archive = File(app.cacheDir, "$token.cbz")
        val bad = File(app.cacheDir, "$token-bad.cbz")
        var imported: Manga? = null
        fun png(color: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.RGB_565).apply { eraseColor(color) }
            return java.io.ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); bitmap.recycle(); out.toByteArray() }
        }
        val blue = png(android.graphics.Color.BLUE)
        val red = png(android.graphics.Color.RED)
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                listOf("pages/10.png" to blue, "pages/2.png" to red).forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            }
            imported = LocalFiles.importArchive(app, Uri.fromFile(archive)) { }
            val chapter = app.dao.chapters(imported.id).single()
            assertEquals(2, app.dao.download(chapter.id)!!.total)
            // Nested entry names are naturally sorted; destination paths are generated.
            assertArrayEquals(red, pageFile(app, chapter.id, 0).readBytes())
            assertArrayEquals(blue, pageFile(app, chapter.id, 1).readBytes())
            val before = app.dao.allMangaIds().toSet()
            ZipOutputStream(bad.outputStream()).use { zip -> zip.putNextEntry(ZipEntry("page.png")); zip.write("invalid".toByteArray()); zip.closeEntry() }
            try { LocalFiles.importArchive(app, Uri.fromFile(bad)) { }; fail("Invalid image must fail") } catch (_: ContentFailure) { }
            assertEquals(before, app.dao.allMangaIds().toSet())
            ZipOutputStream(bad.outputStream()).use { zip -> zip.putNextEntry(ZipEntry("nested/../escape.png")); zip.write(red); zip.closeEntry() }
            try { LocalFiles.importArchive(app, Uri.fromFile(bad)) { }; fail("Unsafe paths must fail on every Android version") } catch (_: ContentFailure) { }
            assertEquals(before, app.dao.allMangaIds().toSet())
        } finally {
            imported?.let { title ->
                app.dao.chapters(title.id).forEach { chapter -> chapterDirectory(app, chapter.id).deleteRecursively(); app.dao.removeDownload(chapter.id) }
                app.database.openHelper.writableDatabase.apply {
                    listOf("chapter_index", "chapters", "chapter_feeds", "shelf").forEach { execSQL("DELETE FROM $it WHERE mangaId = ?", arrayOf(title.id)) }
                    execSQL("DELETE FROM manga WHERE id = ?", arrayOf(title.id))
                }
            }
            archive.delete(); bad.delete()
        }
    }

    @Test fun targetedResetExpiresSourcePathCookiesAndKeepsOtherSource() = runBlocking {
        val manager = CookieManager.getInstance()
        withContext(Dispatchers.Main) {
            manager.setCookie("https://nhentai.net/g/1/", "komori_root_test=one; Path=/; Secure")
            manager.setCookie("https://nhentai.net/g/1/", "komori_path_test=two; Path=/g/; Secure")
            manager.setCookie("https://hentaizap.com/", "komori_other_test=keep; Path=/; Secure")
            manager.flush()
        }
        try {
            assertTrue(manager.getCookie("https://nhentai.net/g/1/").contains("komori_path_test"))
            app.sessions.resetSource("nhentai")
            assertFalse(manager.getCookie("https://nhentai.net/g/1/").orEmpty().contains("komori_"))
            assertTrue(manager.getCookie("https://hentaizap.com/").contains("komori_other_test=keep"))
        } finally { withContext(Dispatchers.Main) { manager.setCookie("https://hentaizap.com/", "komori_other_test=; Max-Age=0; Path=/; Secure"); manager.flush() } }
    }

    @Test fun supportTabUsesKofiAndOpensExactDestination() {
        val device = UiDevice.getInstance(instrumentation)
        val activity = instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val opened = AtomicReference<String>()
        try {
            instrumentation.runOnMainSync {
                val vm = ViewModelProvider(activity)[ReaderViewModel::class.java]
                ViewModelProvider(activity)[UpdateViewModel::class.java].dismiss()
                vm.currentTab.value = 4
            }
            assertTrue(device.wait(Until.hasObject(By.text("Support")), 8000))
            device.findObject(By.text("Support")).click()
            repeat(4) {
                if (!device.hasObject(By.text("Support joyarz on Ko-fi"))) {
                    device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                        device.displayWidth / 2, device.displayHeight / 3, 24)
                    device.waitForIdle()
                }
            }
            assertTrue(device.wait(Until.hasObject(By.text("Support joyarz on Ko-fi")), 5000))
            device.executeShellCommand("screencap -p /data/local/tmp/komori-support.png")
            instrumentation.runOnMainSync {
                activity.setContent { MaterialTheme { CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                    override fun openUri(uri: String) { opened.set(uri) }
                }) { SupportPage() } } }
            }
            repeat(4) {
                if (!device.hasObject(By.text("Support joyarz on Ko-fi"))) {
                    device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                        device.displayWidth / 2, device.displayHeight / 3, 24)
                    device.waitForIdle()
                }
            }
            assertTrue(device.wait(Until.hasObject(By.text("Support joyarz on Ko-fi")), 5000))
            device.findObject(By.text("Support joyarz on Ko-fi")).click()
            instrumentation.waitForIdleSync()
            assertEquals("https://ko-fi.com/joyarz", opened.get())
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun scopedProgressQueryUsesIndexAtFiftyThousandRecords() = runBlocking<Unit> {
        val name = "benchmark-${UUID.randomUUID()}"
        val db = Room.databaseBuilder(app, LibraryDatabase::class.java, name).build()
        try {
            val sql = db.openHelper.writableDatabase
            val statement = sql.compileStatement("INSERT INTO progress(releaseId,mangaId,chapterKey,page,completed,scroll) VALUES(?,?,?,?,?,?)")
            val titleStatement = sql.compileStatement("INSERT INTO manga(id,title,description,cover,creatorsJson,status,tagsJson) VALUES(?,?, '', '', '[]', '', '[]')")
            sql.beginTransaction()
            try {
                repeat(1000) { book ->
                    titleStatement.bindString(1, "benchmark-$book"); titleStatement.bindString(2, "Fixture $book"); titleStatement.executeInsert()
                    repeat(50) { chapter ->
                        statement.bindString(1, "r-$book-$chapter"); statement.bindString(2, "benchmark-$book")
                        statement.bindString(3, "k-$book-$chapter"); statement.bindLong(4, 2); statement.bindLong(5, 0); statement.bindDouble(6, 0.0); statement.executeInsert()
                    }
                }
                sql.setTransactionSuccessful()
            } finally { sql.endTransaction(); statement.close(); titleStatement.close() }
            val allTimes = mutableListOf<Long>(); val scopedTimes = mutableListOf<Long>()
            repeat(5) {
                var start = System.nanoTime(); assertEquals(50000, db.dao().observeProgress().first().size); allTimes += System.nanoTime() - start
                start = System.nanoTime(); assertEquals(50, db.dao().observeProgressForManga("benchmark-500").first().size); scopedTimes += System.nanoTime() - start
            }
            val plan = sql.query("EXPLAIN QUERY PLAN SELECT * FROM progress WHERE mangaId = 'benchmark-500'").use { cursor ->
                buildString { while(cursor.moveToNext()) append(cursor.getString(3)) }
            }
            assertTrue(plan, plan.contains("index_progress_mangaId"))
            android.util.Log.i("KomoriBenchmark", "PROGRESS_QUERY_BENCHMARK records=50000 titles=1000 warm_median_all_ms=${allTimes.sorted()[2] / 1000000.0} warm_median_scoped_ms=${scopedTimes.sorted()[2] / 1000000.0} plan=$plan")
        } finally { db.close(); app.deleteDatabase(name) }
    }

    @Test fun readerVolumeNavigationJumpGapAndBrightnessWorkOffline() = runBlocking {
        val device = UiDevice.getInstance(instrumentation)
        val id = "reader-controls-${UUID.randomUUID()}"
        val book = Manga(id, "Reader tools fixture", "Synthetic pages", "")
        val rows = listOf(chapter(id, "$id-1", "1"), chapter(id, "$id-3", "3"))
        val old = app.settings.flow.first()
        var activity: MainActivity? = null
        try {
            app.settings.set("webtoon", false); app.settings.set("volumeKeys", true)
            app.settings.set("gapWarnings", true); app.settings.number("preloadPages", 0)
            app.dao.putManga(book); app.dao.putChapters(rows)
            rows.forEach { row ->
                chapterDirectory(app, row.id).mkdirs()
                repeat(3) { page ->
                    val bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.RGB_565).apply { eraseColor(android.graphics.Color.BLUE) }
                    pageFile(app, row.id, page).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                }
                app.dao.putDownload(Download(row.id, id, row.release().label, 3, 3, "Complete"))
            }
            activity = instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            lateinit var vm: ReaderViewModel
            instrumentation.runOnMainSync {
                vm = ViewModelProvider(activity!!)[ReaderViewModel::class.java]
                ViewModelProvider(activity!!)[UpdateViewModel::class.java].dismiss()
                vm.selected.value = book; vm.chapters.value = rows.map { it.release() }
                vm.read(rows.last().release(), requestedPage = 0)
            }
            suspend fun waitUntil(check: () -> Boolean) = withTimeout(8000) { while (!check()) delay(30) }
            waitUntil { vm.readerReady.value && activity!!.volumeNavigation != null }
            assertTrue(device.wait(Until.hasObject(By.text("1 / 3")), 5000))
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_VOLUME_UP)
            waitUntil { vm.reader.value?.id == rows.first().id && vm.readerReady.value && vm.readerPage.value == 2 }
            assertTrue(device.wait(Until.hasObject(By.text("3 / 3")), 5000))
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
            assertTrue(device.wait(Until.hasObject(By.text("Chapter gap")), 5000))
            assertEquals(rows.first().id, vm.reader.value!!.id)
            device.findObject(By.text("Continue")).click()
            waitUntil { vm.reader.value?.id == rows.last().id && vm.readerReady.value }
            assertTrue(device.wait(Until.hasObject(By.text("1 / 3")), 5000))
            device.findObject(By.text("1 / 3")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Go to chapter / page")), 5000))
            val field = device.findObject(By.clazz("android.widget.EditText"))
            assertNotNull(field); field.text = "2"
            device.findObject(By.text("Go")).click()
            waitUntil { vm.readerReady.value && vm.readerPage.value == 1 }
            app.settings.number("brightness", 40)
            waitUntil { kotlin.math.abs(activity!!.window.attributes.screenBrightness - .4f) < .01f }
            instrumentation.runOnMainSync { vm.closeReader() }
            waitUntil { activity!!.volumeNavigation == null }
            waitUntil { kotlin.math.abs(activity!!.window.attributes.screenBrightness - (-1f)) < .01f }
            assertEquals(-1f, activity!!.window.attributes.screenBrightness, .01f)
            val queued = app.dao.download(rows.first().id)!!.copy(state = "Queued")
            app.dao.putDownload(queued)
            instrumentation.runOnMainSync { vm.cancelDownload(queued) }
            withTimeout(8000) { while (app.dao.download(queued.releaseId)?.state != "Cancelled") delay(30) }
            assertTrue(pageFile(app, queued.releaseId, 0).exists())
        } finally {
            activity?.let { owner ->
                val model = withContext(Dispatchers.Main) { ViewModelProvider(owner)[ReaderViewModel::class.java] }
                instrumentation.runOnMainSync { model.closeReader(); model.viewModelScope.coroutineContext[Job]?.cancel(); owner.finish() }
                model.viewModelScope.coroutineContext[Job]?.join()
            }
            instrumentation.waitForIdleSync()
            app.settings.set("webtoon", old.webtoon); app.settings.set("volumeKeys", old.volumeKeys)
            app.settings.set("gapWarnings", old.gapWarnings); app.settings.number("preloadPages", old.preloadPages)
            app.settings.number("brightness", old.brightness)
            rows.forEach { chapterDirectory(app, it.id).deleteRecursively(); app.dao.removeDownload(it.id) }
            app.database.openHelper.writableDatabase.apply {
                listOf("chapters", "chapter_index", "progress", "shelf", "chapter_preferences").forEach { execSQL("DELETE FROM $it WHERE mangaId = ?", arrayOf(id)) }
                execSQL("DELETE FROM manga WHERE id = ?", arrayOf(id))
            }
        }
    }

}
