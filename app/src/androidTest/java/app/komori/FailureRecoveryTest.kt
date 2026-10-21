package app.komori

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import app.komori.data.*
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class FailureRecoveryTest {
    private val app
        get() =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as KomoriApp

    private fun client(status: Int, requests: AtomicInteger = AtomicInteger()) =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(status)
                    .message("Test response")
                    .header("Retry-After", "120")
                    .body("".toResponseBody())
                    .build()
            }
            .build()

    @Test
    fun missingImageAndChallengeKeepTheirActionableTypes() = runBlocking {
        for (status in listOf(404, 403)) {
            val connection = client(status)
            val file = File.createTempFile("failed-page", ".part", app.cacheDir)
            try {
                try {
                    transferImage(
                        connection,
                        Request.Builder().url("https://${UUID.randomUUID()}.example/page").build(),
                        file,
                    )
                    fail("Expected HTTP failure")
                } catch (e: SourceHttpException) {
                    assertEquals(status, e.status)
                    assertFalse(automaticRetryAllowed(e))
                    assertEquals(status == 403, requiresSourceAction(e))
                }
                assertFalse(file.exists())
            } finally {
                file.delete()
                connection.dispatcher.executorService.shutdown()
            }
        }
    }

    @Test
    fun workerStopsImmediatelyForMissingManifest() = runBlocking {
        val id = "missing-${UUID.randomUUID()}"
        val connection = client(404)
        try {
            app.dao.putDownload(Download(id, id, "Missing", 0))
            val worker =
                TestListenableWorkerBuilder<ChapterDownloadWorker>(app)
                    .setInputData(workDataOf("chapter" to id))
                    .build()
            assertEquals(ListenableWorker.Result.failure(), worker.download(Sources(connection)))
            assertEquals("Failed", app.dao.download(id)!!.state)
            assertTrue(app.dao.download(id)!!.error.contains("404"))
        } finally {
            app.dao.removeDownload(id)
            connection.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun rateLimitSchedulesCancelableContinuationAndCarriesRetryBudget() = runBlocking {
        val id = "rate-${UUID.randomUUID()}"
        // Untrusted numeric Retry-After values cannot overflow WorkManager's timestamp.
        assertNotNull(downloadRequest(id, Settings(), delayMillis = Long.MAX_VALUE))
        val wm = WorkManager.getInstance(app)
        val requests = AtomicInteger()
        val connection = client(429, requests)
        try {
            app.dao.putDownload(Download(id, id, "Limited", 0))
            val worker =
                TestListenableWorkerBuilder<ChapterDownloadWorker>(app)
                    .setInputData(workDataOf("chapter" to id))
                    .build()
            assertEquals(ListenableWorker.Result.success(), worker.download(Sources(connection)))
            assertEquals("Retrying", app.dao.download(id)!!.state)
            val pending = wm.getWorkInfosForUniqueWork("download-$id").get(5, TimeUnit.SECONDS)
            assertEquals(1, pending.size)
            assertEquals(WorkInfo.State.ENQUEUED, pending.single().state)
            assertTrue(pending.single().initialDelayMillis >= 115_000)
            wm.cancelUniqueWork("download-$id").result.get(5, TimeUnit.SECONDS)
            assertEquals(
                WorkInfo.State.CANCELLED,
                wm.getWorkInfoById(pending.single().id).get()!!.state,
            )
            val exhausted =
                TestListenableWorkerBuilder<ChapterDownloadWorker>(app)
                    .setInputData(workDataOf("chapter" to id, "attemptBase" to 3))
                    .build()
            assertEquals(ListenableWorker.Result.failure(), exhausted.download(Sources(connection)))
            assertEquals("Failed", app.dao.download(id)!!.state)
            assertEquals(1, requests.get())
        } finally {
            wm.cancelUniqueWork("download-$id").result.get()
            SourceCooldowns.check(
                Request.Builder().url("https://api.mangadex.org").build(),
                now = Long.MAX_VALUE,
            )
            app.dao.removeDownload(id)
            connection.dispatcher.executorService.shutdown()
        }
    }
}
