package app.komori

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.data.*
import app.komori.ui.ReaderViewModel
import app.komori.ui.WebtoonStripCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ImageLoadingTest {
    @Test
    fun workerCompletesPagesAndClearsTransientProgress() = runBlocking {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as KomoriApp
        val id = "worker-test-${UUID.randomUUID()}"
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        val png =
            ByteArrayOutputStream()
                .also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                .toByteArray()
        bitmap.recycle()
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val body =
                        if (chain.request().url.encodedPath.startsWith("/at-home/"))
                            """{"baseUrl":"https://images.example","chapter":{"hash":"test","dataSaver":["1.png","2.png"]}}"""
                                .toResponseBody()
                        else png.toResponseBody()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body)
                        .build()
                }
                .build()
        try {
            app.dao.putDownload(Download(id, id, "Test chapter", 0))
            val worker =
                androidx.work.testing
                    .TestListenableWorkerBuilder<ChapterDownloadWorker>(app)
                    .setInputData(androidx.work.workDataOf("chapter" to id))
                    .build()
            assertEquals(
                androidx.work.ListenableWorker.Result.success(),
                worker.download(Sources(client)),
            )
            assertEquals("Complete", app.dao.download(id)!!.state)
            assertEquals(2, app.dao.download(id)!!.done)
            assertTrue(validImage(pageFile(app, id, 0)))
            assertTrue(validImage(pageFile(app, id, 1)))
            assertNull(DownloadTransfers.progress.value[id])
        } finally {
            chapterDirectory(app, id).deleteRecursively()
            app.dao.removeDownload(id)
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun transferReportsBytesAndStoresAValidImage() = runBlocking {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as KomoriApp
        val file = File.createTempFile("transfer-test", ".img", app.cacheDir)
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        // Noise prevents PNG compression from hiding intermediate progress updates.
        val random = java.util.Random(42)
        for (y in 0 until 200) for (x in 0 until 200) bitmap.setPixel(
            x,
            y,
            random.nextInt() or Color.BLACK,
        )
        val data =
            ByteArrayOutputStream()
                .also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                .toByteArray()
        bitmap.recycle()
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(data.toResponseBody())
                        .build()
                }
                .build()
        try {
            val updates = mutableListOf<Long>()
            transferImage(
                client,
                Request.Builder().url("https://images.example/page").build(),
                file,
            ) { bytes, total ->
                assertEquals(data.size.toLong(), total)
                updates += bytes
            }
            assertTrue(validImage(file))
            assertTrue(updates.size > 1)
            assertEquals(data.size.toLong(), updates.last())
        } finally {
            file.delete()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun cachedPageDoesNotWaitForNetworkLocksAndStripIsReused() = runBlocking {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as KomoriApp
        val vm = ReaderViewModel(app)
        val id = "image-test-${UUID.randomUUID()}"
        val chapter =
            Chapter(
                    id,
                    id,
                    JSONObject()
                        .put("id", id)
                        .put("attributes", JSONObject().put("chapter", "1").put("pages", 1))
                        .put(
                            "relationships",
                            org.json
                                .JSONArray()
                                .put(JSONObject().put("type", "manga").put("id", id)),
                        )
                        .toString(),
                )
                .release()
        chapterDirectory(app, id).mkdirs()
        val file = pageFile(app, id, 0)
        val bitmap = Bitmap.createBitmap(2048, 2048, Bitmap.Config.RGB_565)
        bitmap.eraseColor(Color.GREEN)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        @Suppress("UNCHECKED_CAST")
        val locks =
            ReaderViewModel::class
                .java
                .getDeclaredField("imageFetch")
                .apply { isAccessible = true }
                .get(vm) as Array<Mutex>
        locks.forEach { it.lock() }
        try {
            assertEquals(file, withTimeout(1500) { vm.pageFileFor(chapter, 0) })
            val strip = WebtoonStripCache.load(file, 0, 1024, 2048, 512)
            assertEquals(512, strip.width)
            assertEquals(256, strip.height)
            assertSame(strip, WebtoonStripCache.load(file, 0, 1024, 2048, 512))
            assertEquals(Color.GREEN, strip.getPixel(0, 0))
        } finally {
            locks.forEach { it.unlock() }
            chapterDirectory(app, id).deleteRecursively()
        }
    }
}
