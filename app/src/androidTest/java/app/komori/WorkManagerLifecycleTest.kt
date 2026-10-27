package app.komori

import android.graphics.Bitmap
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import app.komori.data.*
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

/** Actual OS-backed WorkManager, foreground execution and sockets; no TestWorkerBuilder. */
class WorkManagerLifecycleTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KomoriApp
    private class Fixture : AutoCloseable {
        val server = MockWebServer()
        val blocked = CountDownLatch(1); val release = CountDownLatch(1)
        val calls = ConcurrentHashMap<String, Int>()
        val manifests = java.util.Collections.synchronizedList(mutableListOf<String>())
        @Volatile var holdSecond = false
        @Volatile var failSecond = false
        @Volatile var resetSecond = false
        val bytes: ByteArray = ByteArrayOutputStream().also { out ->
            val bitmap = Bitmap.createBitmap(48, 64, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.GREEN); bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); bitmap.recycle()
        }.toByteArray()
        init {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    calls.merge(path, 1, Int::plus)
                    if (path.startsWith("/at-home/")) {
                        val id = path.substringAfterLast('/'); manifests += id
                        return MockResponse().setBody("""{"baseUrl":"https://fixture.invalid","chapter":{"hash":"$id","data":["0.png","1.png","2.png"],"dataSaver":["0.png","1.png","2.png"]}}""")
                    }
                    if (path.endsWith("/1.png")) {
                        blocked.countDown()
                        if (holdSecond) check(release.await(20, TimeUnit.SECONDS))
                        if (failSecond) return MockResponse().setResponseCode(404)
                        if (resetSecond) return MockResponse().setBody(Buffer().write(bytes)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                    }
                    return MockResponse().setBody(Buffer().write(bytes)).setHeader("Content-Type", "image/png")
                }
            }
            server.start(InetAddress.getByName("127.0.0.1"), 0)
        }
        fun count(id: String, page: Int) = (calls["/data/$id/$page.png"] ?: 0) + (calls["/data-saver/$id/$page.png"] ?: 0)
        override fun close() { release.countDown(); server.close() }
    }
    private fun command(id: String, method: String, extra: Bundle? = null) = app.contentResolver.call(
        android.net.Uri.parse("content://${app.packageName}.download-fixtures"), method, id, extra)
    private suspend fun waitFor(message: String, predicate: suspend () -> Boolean) {
        withTimeout(15000) { while (!predicate()) delay(20) }
    }
    private suspend fun configure(fixture: Fixture, id: String) {
        app.startupRecovery?.join()
        command(id, "configure", Bundle().apply { putString("endpoint", fixture.server.url("/").newBuilder().host("127.0.0.1").build().toString()) })
    }
    private suspend fun complete(id: String) = waitFor("complete") { app.dao.download(id)?.state == "Complete" }
    private fun id() = "work-fixture-${UUID.randomUUID()}"

    @Test fun pauseAndDeleteDuringSlowTransferKeepCommittedPagesAndContinueTheQueue() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val first = id(); val next = id(); val old = app.settings.flow.first()
            try {
                app.settings.set("wifi", false); configure(fixture, first); configure(fixture, next)
                fixture.holdSecond = true; command(first, "queue")
                assertTrue(withContext(Dispatchers.IO) { fixture.blocked.await(10, TimeUnit.SECONDS) })
                assertEquals(1, app.dao.download(first)!!.done); assertTrue(validImage(pageFile(app, first, 0)))
                command(first, "pause")
                assertEquals("Paused", app.dao.download(first)!!.state)
                fixture.holdSecond = false; fixture.release.countDown()
                command(next, "queue"); complete(next)
                assertEquals("Paused", app.dao.download(first)!!.state)
                command(first, "queue"); complete(first)
                assertEquals(1, fixture.count(first, 0))
                assertEquals(3, app.dao.download(first)!!.done)
                assertEquals(3, (0..2).count { validImage(pageFile(app, first, it)) })
                command(first, "delete")
                waitFor("canceled/finished writer") { WorkManager.getInstance(app).getWorkInfosForUniqueWork("download-$first").get().all { it.state.isFinished } }
                assertNull(app.dao.download(first)); assertFalse(chapterDirectory(app, first).exists())
            } finally { fixture.release.countDown(); command(first, "cleanup"); command(next, "cleanup"); app.settings.set("wifi", old.wifi) }
        }
    }

    @Test fun deleteWhileSocketIsBlockedCannotRecreateDownloadOrStopFollowingTitle() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val first = id(); val next = id()
            try {
                configure(fixture, first); configure(fixture, next); fixture.holdSecond = true
                command(first, "queue")
                assertTrue(withContext(Dispatchers.IO) { fixture.blocked.await(10, TimeUnit.SECONDS) })
                command(first, "delete"); fixture.holdSecond = false; fixture.release.countDown()
                command(next, "queue"); complete(next)
                assertNull(app.dao.download(first)); assertFalse(chapterDirectory(app, first).exists())
                assertFalse(DownloadTransfers.progress.value.containsKey(first))
            } finally { fixture.release.countDown(); command(first, "cleanup"); command(next, "cleanup") }
        }
    }

    @Test fun persistentFailureManualRetryCorruptionRepairAndReaderOwnershipUseOneWriter() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val first = id(); val reader = Any()
            try {
                configure(fixture, first); fixture.failSecond = true; command(first, "queue")
                waitFor("404 failure") { app.dao.download(first)?.state == "Failed" }
                assertEquals(1, app.dao.download(first)!!.done)
                fixture.failSecond = false; command(first, "queue"); command(first, "recover"); complete(first)
                assertEquals(1, fixture.count(first, 0))
                ChapterFiles.reading(reader, listOf(first))
                try { command(first, "delete"); fail("Displayed pages must be retained") } catch (expected: Exception) { assertTrue(expected.message.orEmpty().contains("Close this chapter")) }
                assertTrue(pageFile(app, first, 0).exists()); assertEquals("Complete", app.dao.download(first)!!.state)
                ChapterFiles.releaseReader(reader)
                pageFile(app, first, 1).writeText("corrupted")
                val report = DownloadMaintenance(app).scan(setOf(first)) {}
                assertEquals(1, report.damaged)
                command(first, "queue"); complete(first)
                assertEquals(1, fixture.count(first, 0)); assertEquals(1, fixture.count(first, 2))
                assertTrue(validImage(pageFile(app, first, 1)))
            } finally { ChapterFiles.releaseReader(reader); command(first, "cleanup") }
        }
    }

    @Test fun movingQueuedTitlesChangesTheNextWorkerWithoutInterruptingTheCurrentWriter() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val first = id(); val second = id(); val third = id()
            try {
                for (id in listOf(first, second, third)) configure(fixture, id)
                fixture.holdSecond = true; command(first, "queue")
                assertTrue(withContext(Dispatchers.IO) { fixture.blocked.await(10, TimeUnit.SECONDS) })
                command(second, "queue"); command(third, "queue")
                DownloadCoordinator(app).move(app.dao.download(third)!!, null)
                assertEquals(listOf(first), fixture.manifests.toList())
                fixture.holdSecond = false; fixture.release.countDown()
                complete(first); complete(third); complete(second)
                assertEquals(listOf(first, third, second), fixture.manifests.toList())
            } finally { fixture.release.countDown(); for (id in listOf(first, second, third)) command(id, "cleanup") }
        }
    }

    @Test fun lowStorageWaitAndInterruptedNetworkPreserveBackoffUntilExplicitRetry() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val first = id()
            try {
                configure(fixture, first)
                command(first, "low", Bundle().apply { putBoolean("value", true) }); command(first, "queue")
                waitFor("storage wait") { app.dao.download(first)?.state == "Waiting for storage" }
                assertEquals(0, fixture.server.requestCount)
                command(first, "pause"); command(first, "low", Bundle().apply { putBoolean("value", false) })
                fixture.resetSecond = true; command(first, "queue")
                waitFor("network retry") { app.dao.download(first)?.state == "Retrying" }
                val manager = WorkManager.getInstance(app)
                waitFor("backoff persisted") { manager.getWorkInfosForUniqueWork("download-$first").get().any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0 } }
                val before = manager.getWorkInfosForUniqueWork("download-$first").get().last().id
                command(first, "recover")
                assertEquals(before, manager.getWorkInfosForUniqueWork("download-$first").get().last().id)
                command(first, "pause"); fixture.resetSecond = false; command(first, "queue"); complete(first)
                assertEquals(1, fixture.count(first, 0))
            } finally { command(first, "cleanup") }
        }
    }
}
