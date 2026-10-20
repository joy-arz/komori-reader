package app.komori.data

import java.io.IOException
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class SourceConnectionRecoveryTest {
    private val request =
        Request.Builder()
            .url("https://nhentai.net/")
            .header("User-Agent", "browser-session")
            .build()

    private fun response(request: Request, code: Int = 200) =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("fixture")
            .body("catalog".toResponseBody())
            .build()

    @Test
    fun resetRetriesOnceWithSessionIdentityIntact() = runBlocking {
        val attempts = AtomicInteger()
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    assertEquals("browser-session", chain.request().header("User-Agent"))
                    assertEquals("session=fixture", chain.request().header("Cookie"))
                    if (attempts.incrementAndGet() == 1) throw SocketException("Connection reset")
                    response(chain.request())
                }
                .build()
        val recovery = SourceConnectionRecovery(client, "nhentai", 0)
        assertEquals(
            "catalog",
            recovery.read(request.newBuilder().header("Cookie", "session=fixture").build()) {
                it.body!!.string()
            },
        )
        assertEquals(2, attempts.get())
    }

    @Test
    fun successfulFallbackReusesItsPoolUntilPreferenceExpires() = runBlocking {
        okhttp3.mockwebserver.MockWebServer().use { server ->
            repeat(3) { server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("catalog")) }
            val attempts = AtomicInteger()
            val connections = java.util.Collections.synchronizedList(mutableListOf<Connection>())
            val client =
                OkHttpClient.Builder()
                    .eventListener(
                        object : EventListener() {
                            override fun connectionAcquired(call: Call, connection: Connection) {
                                connections.add(connection)
                            }
                        }
                    )
                    .addInterceptor { chain ->
                        if (attempts.incrementAndGet() == 1)
                            throw SocketException("Connection reset")
                        chain.proceed(chain.request())
                    }
                    .build()
            var now = 1L
            val recovery = SourceConnectionRecovery(client, "nhentai", 0) { now }
            val input = Request.Builder().url(server.url("/")).build()
            repeat(2) { recovery.read(input) { it.body!!.string() } }
            now += java.util.concurrent.TimeUnit.MINUTES.toNanos(16)
            recovery.read(input) { it.body!!.string() }
            assertSame(connections[0], connections[1])
            assertNotSame(connections[1], connections[2])
            assertEquals(4, attempts.get())
        }
    }

    @Test
    fun repeatedResetsAreBoundedAndExplainVerificationLimit() = runBlocking {
        val attempts = AtomicInteger()
        val client =
            OkHttpClient.Builder()
                .addInterceptor {
                    attempts.incrementAndGet()
                    throw SocketException("Connection reset")
                }
                .build()
        try {
            SourceConnectionRecovery(client, "nhentai", 0).read(request) { it.code }
            fail("Expected a transport failure")
        } catch (error: SourceConnectionResetException) {
            assertTrue(connectionMessage(error).contains("fresh-connection retry"))
            assertFalse(requiresSourceAction(error))
        }
        assertEquals(2, attempts.get())
    }

    @Test
    fun tlsFailureDoesNotRetry() = runBlocking {
        val attempts = AtomicInteger()
        val client =
            OkHttpClient.Builder()
                .addInterceptor {
                    attempts.incrementAndGet()
                    throw SSLHandshakeException("certificate rejected")
                }
                .build()
        try {
            SourceConnectionRecovery(client, "nhentai", 0).read(request) { it.code }
            fail("Expected TLS failure")
        } catch (_: SSLHandshakeException) {}
        assertEquals(1, attempts.get())
    }

    @Test
    fun httpAccessFailureDoesNotRetry() = runBlocking {
        val attempts = AtomicInteger()
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    attempts.incrementAndGet()
                    response(chain.request(), 403)
                }
                .build()
        assertEquals(403, SourceConnectionRecovery(client, "nhentai", 0).read(request) { it.code })
        assertEquals(1, attempts.get())
    }

    @Test
    fun otherSourcesAndPostsAreNotRetried() = runBlocking {
        for ((source, input) in
            listOf(
                "zap" to request,
                "nhentai" to
                    request
                        .newBuilder()
                        .post("fixture".toRequestBody("text/plain".toMediaType()))
                        .build(),
            )) {
            val attempts = AtomicInteger()
            val client =
                OkHttpClient.Builder()
                    .addInterceptor {
                        attempts.incrementAndGet()
                        throw SocketException("Connection reset")
                    }
                    .build()
            try {
                SourceConnectionRecovery(client, source, 0).read(input) { it.code }
                fail("Expected reset")
            } catch (_: SocketException) {}
            assertEquals(1, attempts.get())
        }
    }

    @Test
    fun cancellationDuringBackoffPreventsSecondCall() = runBlocking {
        val attempted = CompletableDeferred<Unit>()
        val attempts = AtomicInteger()
        val client =
            OkHttpClient.Builder()
                .addInterceptor {
                    attempts.incrementAndGet()
                    attempted.complete(Unit)
                    throw SocketException("Connection reset")
                }
                .build()
        val job = launch {
            SourceConnectionRecovery(client, "nhentai", 60_000).read(request) { it.code }
        }
        attempted.await()
        job.cancelAndJoin()
        assertEquals(1, attempts.get())
    }

    @Test
    fun socketFailuresAreNotAllReportedAsResets() {
        assertFalse(connectionMessage(SocketException("Network unreachable")).contains("reset"))
        assertTrue(connectionMessage(SocketException("Connection reset")).contains("reset"))
        assertFalse(isConnectionReset(IOException("unrelated")))
    }
}
