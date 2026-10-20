package app.komori

import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.komori.data.*
import app.komori.ui.ReaderViewModel
import app.komori.ui.UpdateViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BatchSelectionTest {
    @Test
    fun downloadedIncludesUnsavedTitlesAndBatchActionsAreExplicit() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as KomoriApp
        val device = UiDevice.getInstance(instrumentation)
        val prefix = "batch-test-${java.util.UUID.randomUUID()}"
        val titles = (1..2).map { Manga("$prefix-$it", "Batch book $it", "Fixture", "") }
        val chapters =
            titles.map { title ->
                val id = "${title.id}-chapter"
                Chapter(
                    id,
                    title.id,
                    JSONObject()
                        .put("id", id)
                        .put(
                            "attributes",
                            JSONObject()
                                .put("chapter", "1")
                                .put("pages", 1)
                                .put("translatedLanguage", "en"),
                        )
                        .put(
                            "relationships",
                            JSONArray().put(JSONObject().put("type", "manga").put("id", title.id)),
                        )
                        .toString(),
                )
            }
        val followed =
            listOf(
                Creator("$prefix-author1", "Batch creator 1", "author"),
                Creator("$prefix-author2", "Batch creator 2", "artist"),
            )
        followed.forEach { app.dao.putCreator(it) }
        val oldSettings = app.settings.flow.first()
        app.settings.set("showContinue", false)
        for ((index, title) in titles.withIndex()) {
            app.dao.putManga(title)
            app.dao.putShelf(Shelf(title.id, saved = false))
            val chapter = chapters[index]
            app.dao.putChapters(listOf(chapter))
            app.dao.putDownload(Download(chapter.id, title.id, "Ch. 1", 1, 1, "Complete"))
            chapterDirectory(app, chapter.id).mkdirs()
            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565)
            pageFile(app, chapter.id, 0).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        val activity =
            instrumentation.startActivitySync(
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ) as MainActivity
        lateinit var vm: ReaderViewModel
        instrumentation.runOnMainSync {
            vm = ViewModelProvider(activity)[ReaderViewModel::class.java]
            ViewModelProvider(activity)[UpdateViewModel::class.java].dismiss()
        }
        fun tapText(text: String) {
            assertTrue("Missing $text", device.wait(Until.hasObject(By.text(text)), 8000))
            device.findObject(By.text(text)).click()
            device.waitForIdle()
        }
        try {
            tapText("Downloaded")
            assertTrue(device.wait(Until.hasObject(By.desc("Batch book 1")), 5000))
            assertTrue(device.hasObject(By.desc("Batch book 2")))
            device.takeScreenshot(
                java.io.File(app.getExternalFilesDir(null), "downloaded-library.png")
            )
            tapText("Select")
            tapText("Select all")
            tapText("Actions")
            tapText("Like")
            withTimeout(5000) {
                while (titles.any { app.dao.shelf(it.id)?.liked != true }) delay(30)
            }
            assertTrue(titles.all { app.dao.shelf(it.id)?.saved == false })
            device.findObject(By.desc("Batch book 1")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Saved chapter list · Offline")), 5000))
            device.findObject(By.desc("Back")).click()
            device.findObject(By.desc("Authors")).click()
            tapText("Manage")
            tapText("Select")
            for (creator in followed) {
                assertTrue(device.wait(Until.hasObject(By.desc("Select ${creator.name}")), 5000))
                device.findObject(By.desc("Select ${creator.name}")).click()
            }
            tapText("Actions")
            tapText("Unfollow selected")
            tapText("Confirm")
            withTimeout(5000) {
                while (
                    app.dao.observeCreators().first().any { it.id in followed.map { c -> c.id } }
                ) delay(30)
            }
            device.findObject(By.desc("Downloads")).click()
            tapText("Select")
            tapText("Batch book 1")
            tapText("Batch book 2")
            tapText("Actions")
            tapText("Delete downloads")
            tapText("Cancel")
            assertTrue(chapters.all { pageFile(app, it.id, 0).exists() })
            tapText("Actions")
            tapText("Delete downloads")
            tapText("Confirm")
            withTimeout(5000) { while (chapters.any { app.dao.download(it.id) != null }) delay(30) }
            assertTrue(chapters.none { chapterDirectory(app, it.id).exists() })
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            app.settings.set("showContinue", oldSettings.showContinue)
            followed.forEach { app.dao.removeCreator(it.id) }
            chapters.forEach {
                app.dao.removeDownload(it.id)
                chapterDirectory(app, it.id).deleteRecursively()
            }
            withContext(Dispatchers.IO) {
                val db = app.database.openHelper.writableDatabase
                db.execSQL("DELETE FROM shelf WHERE mangaId LIKE ?", arrayOf("$prefix%"))
                db.execSQL("DELETE FROM manga WHERE id LIKE ?", arrayOf("$prefix%"))
                db.execSQL("DELETE FROM chapters WHERE mangaId LIKE ?", arrayOf("$prefix%"))
            }
        }
    }
}
