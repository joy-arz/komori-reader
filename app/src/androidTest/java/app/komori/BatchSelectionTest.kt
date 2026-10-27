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
        val marker = prefix.takeLast(8)
        val titles = (1..2).map { Manga("$prefix-$it", "Batch book $marker $it", "Fixture", "") }
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
                Creator("$prefix-author1", "Batch creator $marker 1", "author"),
                Creator("$prefix-author2", "Batch creator $marker 2", "artist"),
            )
        followed.forEach { app.dao.putCreator(it) }
        val oldSettings = app.settings.flow.first()
        app.settings.set("showContinue", false)
        for ((index, title) in titles.withIndex()) {
            app.dao.putManga(title)
            app.dao.putShelf(Shelf(title.id, saved = false, touched = System.currentTimeMillis()))
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
        var launched: MainActivity? = null
        try {
        val activity =
            instrumentation.startActivitySync(
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ) as MainActivity
        launched = activity
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
        fun tapDescription(description: String) {
            assertTrue(
                "Missing $description",
                device.wait(Until.hasObject(By.pkg(app.packageName).desc(description)), 8000),
            )
            device.findObject(By.pkg(app.packageName).desc(description)).click()
            device.waitForIdle()
        }
            tapText("Downloaded")
            assertTrue(device.wait(Until.hasObject(By.desc(titles[0].title)), 5000))
            assertTrue(device.hasObject(By.desc(titles[1].title)))
            device.takeScreenshot(
                java.io.File(app.getExternalFilesDir(null), "downloaded-library.png")
            )
            assertFalse(device.hasObject(By.text("Select")))
            device.findObject(By.desc(titles[0].title)).longClick()
            tapText("Select all")
            tapText("Actions")
            tapText("Like")
            withTimeout(5000) {
                while (titles.any { app.dao.shelf(it.id)?.liked != true }) delay(30)
            }
            assertTrue(titles.all { app.dao.shelf(it.id)?.saved == false })
            device.findObject(By.desc(titles[0].title)).click()
            assertTrue(device.wait(Until.hasObject(By.text("Saved chapter list · Offline")), 5000))
            tapDescription("Back")
            tapDescription("Authors")
            tapText("Manage")
            val findCreator = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
                ?: throw AssertionError("Creator search is missing")
            findCreator.text = "Batch creator $marker"
            assertFalse(device.hasObject(By.text("Select")))
            val authorRow = device.wait(Until.findObject(By.longClickable(true).hasDescendant(By.desc("Manage author ${followed.first().name}"))), 5000)
                ?: throw AssertionError("Author management row did not become interactive")
            authorRow.longClick()
            assertTrue("Long press did not activate selection", device.wait(Until.hasObject(By.text("1 selected")), 5000))
            device.dumpWindowHierarchy(java.io.File(app.getExternalFilesDir(null), "batch-after-long-press.xml"))
            android.util.Log.i("KomoriBatch", "afterLongPress selectedLabel=${device.hasObject(By.text("1 selected"))} firstVisible=${device.hasObject(By.text(followed.first().name))} secondVisible=${device.hasObject(By.text(followed.last().name))}")
            for (creator in followed.drop(1)) {
                assertTrue(device.wait(Until.hasObject(By.desc("Select ${creator.name}")), 5000))
                device.findObject(By.desc("Select ${creator.name}")).click()
            }
            assertTrue(device.wait(Until.hasObject(By.text("2 selected")), 5000))
            tapDescription("End selection")
            assertTrue(device.wait(Until.gone(By.text("2 selected")), 5000))
            device.findObject(By.desc("Manage author ${followed.first().name}")).click()
            tapText("Manage")
            val rowAgain = device.wait(Until.findObject(By.longClickable(true).hasDescendant(By.desc("Manage author ${followed.first().name}"))), 5000)!!
            rowAgain.longClick()
            assertTrue(device.wait(Until.hasObject(By.text("1 selected")), 5000))
            device.findObject(By.clazz("android.widget.EditText")).text = "Batch creator $marker"
            assertTrue(device.wait(Until.gone(By.text("1 selected")), 5000))
            device.wait(Until.findObject(By.longClickable(true).hasDescendant(By.desc("Manage author ${followed.first().name}"))), 5000)!!.longClick()
            assertTrue(device.wait(Until.hasObject(By.desc("Select ${followed.last().name}")), 5000))
            device.findObject(By.desc("Select ${followed.last().name}")).click()
            tapText("Actions")
            tapText("Unfollow selected")
            tapText("Confirm")
            withTimeout(5000) {
                while (
                    app.dao.observeCreators().first().any { it.id in followed.map { c -> c.id } }
                ) delay(30)
            }
            tapDescription("Downloads")
            assertFalse(device.hasObject(By.text("Select")))
            device.takeScreenshot(
                java.io.File(app.getExternalFilesDir(null), "downloads-compact.png")
            )
            (device.wait(Until.findObject(By.text(titles[0].title)), 8000)
                ?: throw AssertionError("Downloaded title group did not appear")).longClick()
            tapText(titles[1].title)
            tapText("Actions")
            tapText("Delete downloads")
            tapText("Cancel")
            assertTrue(chapters.all { pageFile(app, it.id, 0).exists() })
            tapText("Actions")
            tapText("Delete downloads")
            tapText("Confirm")
            withTimeout(5000) { while (chapters.any { app.dao.download(it.id) != null }) delay(30) }
            assertTrue(chapters.none { chapterDirectory(app, it.id).exists() })
        } catch (error: Throwable) {
            device.executeShellCommand("screencap -p /data/local/tmp/komori-batch-failure.png")
            device.dumpWindowHierarchy(java.io.File(app.getExternalFilesDir(null), "batch-failure.xml"))
            android.util.Log.e("KomoriBatch", "selection failure: ${error.javaClass.simpleName}; fixtureCreators=${runBlocking { app.dao.observeCreators().first().count { it.id.startsWith(prefix) } }}")
            throw error
        } finally {
            instrumentation.runOnMainSync { launched?.finish() }
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
