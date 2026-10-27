package app.komori

import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import app.komori.data.*
import app.komori.ui.ReaderViewModel
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReadingAndBackgroundTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as KomoriApp
    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); bitmap.recycle() }.toByteArray()
    }
    private suspend fun until(check: suspend () -> Boolean) = withTimeout(10000) { while (!check()) delay(25) }

    @Test fun lastPageCompletesWithoutFinishAndExplicitReadRestartsAtPageOne() = runBlocking<Unit> {
        val token = UUID.randomUUID().toString()
        val book = Manga(sourceId("local", "/book/$token/"), "Reread fixture", "", "")
        val id = sourceId("local", "/chapter/$token/")
        val chapter = Chapter(id, book.id, JSONObject().put("id", id)
            .put("attributes", JSONObject().put("chapter", "1").put("translatedLanguage", "en").put("pages", 3))
            .put("relationships", JSONArray().put(JSONObject().put("id", book.id).put("type", "manga"))).toString())
        app.dao.putManga(book); app.dao.saveFeed(book.id, "en", listOf(chapter)); app.dao.putShelf(Shelf(book.id, saved = true))
        chapterDirectory(app, id).mkdirs()
        repeat(3) { pageFile(app, id, it).writeBytes(png()) }
        val vm = withContext(Dispatchers.Main) { ReaderViewModel(app) }
        try {
            withContext(Dispatchers.Main) { vm.selected.value = book; vm.chapters.value = listOf(chapter.release().copy(local = true)); vm.read(vm.chapters.value.single()) }
            until { vm.readerReady.value }
            withContext(Dispatchers.Main) { vm.page(2) }
            until { app.dao.progress(id)?.completed == true }
            assertEquals(2, app.dao.progress(id)!!.page)
            val previousSession = vm.readerSession.value
            withContext(Dispatchers.Main) { vm.closeReader(); vm.startReading() }
            until { vm.readerSession.value > previousSession && vm.readerReady.value }
            assertEquals(0, vm.readerPage.value)
        } finally {
            val job = vm.viewModelScope.coroutineContext[Job]
            withContext(Dispatchers.Main) { vm.closeReader(); job?.cancel() }; job?.join()
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM progress WHERE mangaId = ?", arrayOf(book.id))
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM shelf WHERE mangaId = ?", arrayOf(book.id))
            for (table in listOf("chapter_feeds", "chapter_index", "chapters", "chapter_preferences"))
                app.database.openHelper.writableDatabase.execSQL("DELETE FROM $table WHERE mangaId = ?", arrayOf(book.id))
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM manga WHERE id = ?", arrayOf(book.id))
            chapterDirectory(app, id).deleteRecursively()
        }
    }

    @Test fun foregroundRecoveryReplacesInterruptedWorkButPreservesRetryDelay() = runBlocking<Unit> {
        val manager = androidx.work.WorkManager.getInstance(app)
        val id = "recovery-${UUID.randomUUID()}"
        val name = "download-$id"
        try {
            val delayed = downloadRequest(id, Settings(), attemptBase = 2, delayMillis = TimeUnit.HOURS.toMillis(1))
            manager.enqueueUniqueWork(name, androidx.work.ExistingWorkPolicy.KEEP, delayed).result.get()
            app.dao.putDownload(Download(id, id, "Interrupted fixture", 0, state = "Retrying"))
            kickDownloadQueue(app, recoverInterrupted = true)
            assertEquals(androidx.work.WorkInfo.State.ENQUEUED, manager.getWorkInfoById(delayed.id).get()!!.state)
            assertEquals(listOf(delayed.id), manager.getWorkInfosForUniqueWork(name).get().filter { !it.state.isFinished }.map { it.id })

            app.dao.putDownload(Download(id, id, "Interrupted fixture", 0, state = "Downloading"))
            kickDownloadQueue(app, recoverInterrupted = true)
            val old = manager.getWorkInfoById(delayed.id).get()
            assertTrue(old == null || old.state == androidx.work.WorkInfo.State.CANCELLED)
            assertTrue(manager.getWorkInfosForUniqueWork(name).get().any { it.id != delayed.id })
        } finally {
            manager.cancelUniqueWork(name).result.get()
            ChapterFiles.locked(id) { app.dao.removeDownload(id) }
            chapterDirectory(app, id).deleteRecursively()
            kickDownloadQueue(app)
        }
    }

    @Test fun mangaDexDownloadRecoversResetAndContinuesAfterActivityCloses() = runBlocking<Unit> {
        val id = "background-fixture-${UUID.randomUUID()}"
        val started = CountDownLatch(1); val proceed = CountDownLatch(1)
        val imageCalls = AtomicInteger()
        val image = png()
        val api = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val manifest = request.url.encodedPath.startsWith("/at-home/")
            val body = if (manifest) """{"baseUrl":"https://cdn.example","chapter":{"hash":"fixture","dataSaver":["1.png","2.png"],"data":["1.png","2.png"]}}""".toResponseBody()
            else {
                assertTrue(request.header("User-Agent").orEmpty().startsWith("Komori/"))
                assertEquals("https://mangadex.org/", request.header("Referer"))
                if (imageCalls.incrementAndGet() == 1) {
                    started.countDown(); assertTrue(proceed.await(10, TimeUnit.SECONDS)); throw EOFException("Reset fixture")
                }
                image.toResponseBody()
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        }.build()
        val browser = OkHttpClient.Builder().addInterceptor { throw AssertionError("MangaDex pages must not use browser-session transport") }.build()
        val oldRuntime = app.downloadRuntime
        val source = Sources(browser, api)
        app.downloadRuntime = object : DownloadRuntime {
            override fun source(chapter: String) = if (chapter == id) source else oldRuntime?.source(chapter)
        }
        app.dao.putDownload(Download(id, id, "Background fixture", 0))
        val activity = instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        try {
            kickDownloadQueue(app)
            until { started.count == 0L }
            instrumentation.runOnMainSync { activity.finish() }
            proceed.countDown()
            until { app.dao.download(id)?.state == "Complete" }
            assertEquals("Complete", app.dao.download(id)!!.state)
            assertTrue(validImage(pageFile(app, id, 0))); assertTrue(validImage(pageFile(app, id, 1)))
            assertEquals(3, imageCalls.get())
            val info = downloadForegroundInfo(app, "Background fixture", 1, 2)
            assertEquals("Background fixture", info.notification.extras.getString("android.title"))
            if (android.os.Build.VERSION.SDK_INT >= 29)
                assertEquals(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, info.foregroundServiceType)
        } finally {
            proceed.countDown()
            androidx.work.WorkManager.getInstance(app).cancelUniqueWork("download-$id").result.get()
            ChapterFiles.locked(id) { app.dao.removeDownload(id); chapterDirectory(app, id).deleteRecursively() }
            app.downloadRuntime = oldRuntime
            kickDownloadQueue(app)
            api.dispatcher.executorService.shutdown(); browser.dispatcher.executorService.shutdown()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
