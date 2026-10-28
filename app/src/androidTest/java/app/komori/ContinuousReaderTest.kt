package app.komori

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.komori.data.*
import app.komori.ui.ReaderViewModel
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ContinuousReaderTest {
    @Test
    fun scrollsAcrossFourOfflineChaptersWithoutButtonsAndKeepsProgress() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as KomoriApp
        val id = "continuous-test-${UUID.randomUUID()}"
        val old = app.settings.flow.first()
        var activity: MainActivity? = null
        try {
            app.settings.set("webtoon", true)
            app.settings.set("continuousChapters", true)
            app.settings.set("resume", false)
            app.dao.putManga(Manga(id, "Continuous reader test", "Synthetic test pages", ""))
            val chapters =
                (1..4).map { number ->
                    val chapterId = "$id-$number"
                    val json =
                        JSONObject()
                            .put("id", chapterId)
                            .put(
                                "attributes",
                                JSONObject()
                                    .put("chapter", "$number")
                                    .put("translatedLanguage", "en")
                                    .put("pages", 1),
                            )
                            .put(
                                "relationships",
                                JSONArray().put(JSONObject().put("type", "manga").put("id", id)),
                            )
                    val bitmap = Bitmap.createBitmap(500, 900, Bitmap.Config.RGB_565)
                    Canvas(bitmap).apply {
                        drawColor(
                            listOf(Color.BLUE, Color.GREEN, Color.YELLOW, Color.CYAN)[number - 1]
                        )
                        drawText(
                            "Chapter $number",
                            40f,
                            150f,
                            Paint().apply {
                                color = Color.BLACK
                                textSize = 50f
                            },
                        )
                    }
                    chapterDirectory(app, chapterId).mkdirs()
                    pageFile(app, chapterId, 0).outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                    app.dao.putDownload(Download(chapterId, id, "Ch. $number", 1, 1, "Complete"))
                    Chapter(chapterId, id, json.toString())
                }
            app.dao.putChapters(chapters)
            activity =
                instrumentation.startActivitySync(
                    Intent(instrumentation.targetContext, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                ) as MainActivity
            lateinit var vm: ReaderViewModel
            instrumentation.runOnMainSync {
                vm = ViewModelProvider(activity!!)[ReaderViewModel::class.java]
            }
            val download = app.dao.download("$id-1")!!
            instrumentation.runOnMainSync { vm.readOffline(download) }
            waitUntil { vm.readerReady.value && vm.reader.value?.id == "$id-1" }
            val session = vm.readerSession.value
            val device = UiDevice.getInstance(instrumentation)
            assertTrue("Reader must be in the foreground before injected gestures", device.wait(Until.hasObject(By.desc("Reading area")), 8000))
            assertFalse("Reader starts with controls hidden", device.hasObject(By.desc("Close reader")))
            SystemClock.sleep(1000)
            fun scroll(from: Float, to: Float) {
                val viewport = device.findObject(By.desc("Reading area"))?.visibleBounds
                    ?: throw AssertionError("Reader viewport missing before scroll")
                assertTrue("Reader swipe was not injected", device.swipe(viewport.centerX(),
                    viewport.top + (viewport.height() * from).toInt(), viewport.centerX(),
                    viewport.top + (viewport.height() * to).toInt(), 52))
            }
            repeat(18) {
                if (vm.reader.value?.id != "$id-4") {
                    scroll(.8f, .3f)
                    SystemClock.sleep(450)
                }
            }
            try { waitUntil { vm.reader.value?.id == "$id-4" } }
            catch (error: Throwable) {
                device.executeShellCommand("screencap -p /data/local/tmp/komori-continuous-failure.png")
                throw AssertionError("Forward continuity: reader=${vm.reader.value?.id} ready=${vm.readerReady.value} error=${vm.readerError.value}", error)
            }
            assertFalse("Scrolling must not reveal controls", device.hasObject(By.desc("Close reader")))
            assertEquals(
                "Continuous transitions must keep the same reader session",
                session,
                vm.readerSession.value,
            )
            waitUntil { runBlocking { app.dao.progress("$id-3")?.completed == true } }
            for (number in 1..3) assertTrue(
                "Chapter $number should be completed",
                app.dao.progress("$id-$number")!!.completed,
            )
            waitUntil { runBlocking { app.dao.shelf(id)?.lastRelease == "$id-4" } }
            assertEquals("$id-4", app.dao.shelf(id)?.lastRelease)
            repeat(30) {
                if (vm.reader.value?.id != "$id-1") {
                    scroll(.25f, .75f)
                    SystemClock.sleep(450)
                }
            }
            waitUntil { vm.reader.value?.id == "$id-1" }
            assertEquals("Backward loading retains the reader session", session, vm.readerSession.value)
            instrumentation.runOnMainSync {
                vm.read(chapters.first().release().copy(local = true))
                vm.scrollChapterPosition(chapters.last().release(), 0, 0f, session)
                assertEquals(
                    "Old scroll callbacks must not undo a manual chapter change",
                    "$id-1",
                    vm.reader.value?.id,
                )
            }
        } finally {
            activity?.let { owner ->
                val model = withContext(kotlinx.coroutines.Dispatchers.Main) { ViewModelProvider(owner)[ReaderViewModel::class.java] }
                instrumentation.runOnMainSync { model.closeReader(); model.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancel(); owner.finish() }
                model.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.join()
            }
            instrumentation.waitForIdleSync()
            app.settings.set("webtoon", old.webtoon)
            app.settings.set("continuousChapters", old.continuousChapters)
            app.settings.set("resume", old.resume)
            for (number in 1..4) chapterDirectory(app, "$id-$number").deleteRecursively()
            for (table in
                listOf(
                    "progress",
                    "downloads",
                    "chapters",
                    "shelf",
                    "chapter_preferences",
                    "chapter_feeds",
                    "chapter_index",
                )) app.database.openHelper.writableDatabase.execSQL(
                "DELETE FROM $table WHERE mangaId = ?",
                arrayOf(id),
            )
            app.database.openHelper.writableDatabase.execSQL(
                "DELETE FROM manga WHERE id = ?",
                arrayOf(id),
            )
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        assertTrue("Timed out waiting for reader state", condition())
    }
}
