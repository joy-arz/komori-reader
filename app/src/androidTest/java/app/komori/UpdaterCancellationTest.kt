package app.komori

import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.data.*
import app.komori.ui.UpdateViewModel
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class UpdaterCancellationTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KomoriApp

    @Test fun switchingChannelsCancelsThePreviousCheck() = runBlocking<Unit> {
        val started = CompletableDeferred<Unit>(); val canceled = CompletableDeferred<Unit>()
        val update = AppUpdate("99.0.0-preview", 999, "https://example.invalid/update.apk", "0".repeat(64), 32)
        var calls = 0
        val fake = object : AppUpdates(app) {
            override suspend fun check(): AppUpdate? {
                calls++
                if (calls == 1) { started.complete(Unit); try { awaitCancellation() } finally { canceled.complete(Unit) } }
                return update
            }
        }
        val original = fake.channel()
        val vm = withContext(Dispatchers.Main) { UpdateViewModel(app, fake) }
        try {
            withContext(Dispatchers.Main) { vm.check(true) }
            withTimeout(5000) { started.await() }
            withContext(Dispatchers.Main) { vm.channel("preview") }
            withTimeout(5000) { canceled.await(); vm.state.first { !it.checking && it.update == update } }
            assertEquals("preview", vm.state.value.channel)
        } finally {
            withContext(Dispatchers.Main) { vm.viewModelScope.cancel() }
            fake.channel(original)
        }
    }

    @Test fun cancelingAnUpdaterTransferCancelsItsNetworkCall() = runBlocking<Unit> {
        val started = CountDownLatch(1); val unblock = CountDownLatch(1); val canceled = AtomicBoolean()
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun canceled(call: Call) { canceled.set(true) }
        }).addInterceptor { chain ->
            val source = object : Source {
                override fun read(sink: Buffer, byteCount: Long): Long { started.countDown(); unblock.await(10, TimeUnit.SECONDS); throw IOException("Fixture released") }
                override fun timeout() = Timeout.NONE
                override fun close() { unblock.countDown() }
            }.buffer()
            val body = object : ResponseBody() {
                override fun contentType(): MediaType? = null
                override fun contentLength() = 32L
                override fun source() = source
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        }.build()
        val updater = AppUpdates(app, client)
        val work = launch(Dispatchers.IO) { updater.download(AppUpdate("99.0.0", 999, "https://example.invalid/update.apk", "0".repeat(64), 32)) {} }
        try {
            assertTrue(withContext(Dispatchers.IO) { started.await(5000, TimeUnit.MILLISECONDS) })
            work.cancelAndJoin()
            assertTrue(canceled.get())
        } finally { unblock.countDown(); work.cancelAndJoin(); client.dispatcher.executorService.shutdown() }
    }
}
