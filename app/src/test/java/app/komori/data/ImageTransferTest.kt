package app.komori.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

class ImageTransferTest {
    @Test
    fun reportsBytesDuringCopyForUnknownLengths() {
        val updates = mutableListOf<Long>()
        val bytes = ByteArray(100_000)
        val output = ByteArrayOutputStream()
        copyImageBytes(ByteArrayInputStream(bytes), output, progress = { updates += it })
        assertTrue(updates.size > 1)
        assertEquals(100_000L, updates.last())
        assertTrue(updates.zipWithNext().all { (a, b) -> b > a })
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test
    fun acceptsExactLimit() {
        val output = ByteArrayOutputStream()
        copyImageBytes(ByteArrayInputStream(byteArrayOf(1, 2, 3)), output, 3)
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
    }

    @Test
    fun refusesUnknownLengthOverflowBeforeWriting() {
        val output = ByteArrayOutputStream()
        assertThrows(IOException::class.java) {
            copyImageBytes(ByteArrayInputStream(ByteArray(10)), output, 9)
        }
        assertEquals(0, output.size())
    }

    @Test
    fun stopsWhenCancellationOrStorageCheckFails() {
        val output = ByteArrayOutputStream()
        assertThrows(IOException::class.java) {
            copyImageBytes(ByteArrayInputStream(ByteArray(100)), output) {
                throw IOException("Stopped")
            }
        }
        assertEquals(0, output.size())
    }

    @Test
    fun cancellationClosesSlowResponse() =
        kotlinx.coroutines.runBlocking {
            val server = okhttp3.mockwebserver.MockWebServer()
            val client = okhttp3.OkHttpClient()
            val file = java.io.File.createTempFile("komori-transfer", ".part")
            try {
                server.enqueue(
                    okhttp3.mockwebserver
                        .MockResponse()
                        .setBody("x".repeat(100_000))
                        .throttleBody(1, 1, java.util.concurrent.TimeUnit.SECONDS)
                )
                val job =
                    launch(kotlinx.coroutines.Dispatchers.IO) {
                        transferImage(
                            client,
                            okhttp3.Request.Builder().url(server.url("/slow")).build(),
                            file,
                        )
                    }
                check(server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS) != null)
                job.cancel()
                kotlinx.coroutines.withTimeout(2000) { job.join() }
                kotlinx.coroutines.withTimeout(2000) {
                    while (client.dispatcher.runningCallsCount() > 0) kotlinx.coroutines.delay(10)
                }
                assertEquals(0, client.dispatcher.runningCallsCount())
            } finally {
                client.dispatcher.executorService.shutdownNow()
                server.close()
                file.delete()
            }
        }
}
