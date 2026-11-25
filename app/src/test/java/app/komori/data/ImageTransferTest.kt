package app.komori.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import okhttp3.ResponseBody.Companion.toResponseBody
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
    fun writeFailuresAreStorageFailuresRatherThanNetworkRetries() {
        val output = object : java.io.OutputStream() {
            override fun write(value: Int) { throw IOException("No space left on device") }
        }
        val failure = assertThrows(StorageFailure::class.java) {
            copyImageBytes(ByteArrayInputStream(byteArrayOf(1)), output)
        }
        assertFalse(automaticRetryAllowed(failure))
        assertEquals("No space left on device", failure.cause!!.message)
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
    fun originCertificateFailuresNeverWriteTheHtmlBodyToTheImageFile() =
        kotlinx.coroutines.runBlocking {
            val server = okhttp3.mockwebserver.MockWebServer()
            val client = okhttp3.OkHttpClient()
            val file = java.io.File.createTempFile("komori-526", ".part").apply { delete() }
            try {
                server.enqueue(
                    okhttp3.mockwebserver.MockResponse()
                        .setResponseCode(526)
                        .setBody("<html><body>error code: 526</body></html>")
                )
                val error =
                    runCatching {
                        transferImage(
                            client,
                            okhttp3.Request.Builder().url(server.url("/page.jpg")).build(),
                            file,
                        )
                    }.exceptionOrNull()
                assertTrue(error is SourceHttpException)
                assertEquals(526, (error as SourceHttpException).status)
                assertEquals(server.url("/page.jpg").host, error.host)
                assertFalse(file.exists())
                assertEquals(1, server.requestCount)
            } finally {
                client.dispatcher.executorService.shutdownNow()
                server.close()
                file.delete()
            }
        }

    @Test
    fun redirectedManhwa18ImageUsesTheFinalResponseHostForFallback() =
        kotlinx.coroutines.runBlocking {
            val calls = mutableListOf<okhttp3.Request>()
            fun response(request: okhttp3.Request, code: Int, location: String? = null) =
                okhttp3.Response.Builder()
                    .request(request)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(code)
                    .message("fixture")
                    .apply { location?.let { header("Location", it) } }
                    .body("".toResponseBody())
                    .build()
            val client =
                okhttp3.OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request()
                        synchronized(calls) { calls += request }
                        when (request.url.host) {
                            "www.manhwa18.com" -> {
                                // OkHttp exposes the followed redirect as response.request; model that final URL.
                                val finalRequest =
                                    request.newBuilder()
                                        .url("https://cdn.manhwa18.com/series/202/17.jpeg?token=fixture")
                                        .build()
                                synchronized(calls) { calls += finalRequest }
                                response(finalRequest, 526)
                            }
                            "min.manhwa18.net" -> response(request, 526)
                            else -> throw AssertionError("Unexpected image host ${request.url.host}")
                        }
                    }
                    .build()
            val file = java.io.File.createTempFile("komori-manhwa-fallback", ".part").apply { delete() }
            try {
                val failure =
                    runCatching {
                        Sources(client).transferPage(
                            sourceId("manhwa18", "/manga/fixture/chap-1"),
                            "https://www.manhwa18.com/series/202/17.jpeg?token=fixture",
                            file,
                        )
                    }.exceptionOrNull() as? Exception
                assertTrue(failure is SourceRequestFailure)
                val http = originalFailure(failure) as SourceHttpException
                assertEquals(526, http.status)
                assertEquals("cdn.manhwa18.com", http.host)
                assertEquals(
                    listOf("www.manhwa18.com", "cdn.manhwa18.com", "min.manhwa18.net"),
                    calls.map { it.url.host },
                )
                assertEquals("/series/202/17.jpeg", calls.last().url.encodedPath)
                assertEquals("token=fixture", calls.last().url.query)
                assertEquals("https://manhwa18.com/", calls.last().header("Referer"))
                assertFalse(file.exists())
            } finally {
                client.dispatcher.executorService.shutdownNow()
                file.delete()
            }
        }

    @Test
    fun legacyManhwa18Cdn502UsesTheVerifiedReplacementOnce() =
        kotlinx.coroutines.runBlocking {
            val calls = mutableListOf<okhttp3.Request>()
            val client =
                okhttp3.OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request()
                        synchronized(calls) { calls += request }
                        okhttp3.Response.Builder()
                            .request(request)
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(if (request.url.host == "cdn.manhwa18.net") 502 else 526)
                            .message("fixture")
                            .body("".toResponseBody())
                            .build()
                    }
                    .build()
            val file = java.io.File.createTempFile("komori-manhwa-502", ".part").apply { delete() }
            try {
                val failure =
                    runCatching {
                        Sources(client).transferPage(
                            sourceId("manhwa18", "/manga/fixture/chap-1"),
                            "https://cdn.manhwa18.net/chapters/1.jpg",
                            file,
                        )
                    }.exceptionOrNull() as? Exception
                val http = originalFailure(failure) as SourceHttpException
                assertEquals(502, http.status)
                assertEquals(listOf("cdn.manhwa18.net", "min.manhwa18.net"), calls.map { it.url.host })
                assertEquals("/chapters/1.jpg", calls.last().url.encodedPath)
                assertEquals(526, (http.suppressed.single() as SourceHttpException).status)
                assertFalse(file.exists())
            } finally {
                client.dispatcher.executorService.shutdownNow()
                file.delete()
            }
        }

    @Test
    fun ccAndUnknownManhwa18HostsNeverCrossHostOn526() =
        kotlinx.coroutines.runBlocking {
            for ((source, url) in
                listOf(
                    "manhwa18cc" to "https://img11.manhwa18.cc/chapters/1.jpg",
                    "manhwa18" to "https://cdn.pornwa.club/643/1/1.jpg",
                    "manhwa18" to "https://min.manhwa18.net/chapters/1.jpg",
                )) {
                val calls = mutableListOf<okhttp3.Request>()
                val client =
                    okhttp3.OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            synchronized(calls) { calls += chain.request() }
                            okhttp3.Response.Builder()
                                .request(chain.request())
                                .protocol(okhttp3.Protocol.HTTP_1_1)
                                .code(526)
                                .message("fixture")
                                .body("".toResponseBody())
                                .build()
                        }
                        .build()
                val file = java.io.File.createTempFile("komori-manhwa-known", ".part").apply { delete() }
                try {
                    val failure =
                        runCatching {
                            Sources(client).transferPage(sourceId(source, "/manga/fixture/chap-1"), url, file)
                        }.exceptionOrNull() as? Exception
                    assertEquals(526, (originalFailure(failure) as SourceHttpException).status)
                    assertEquals(1, calls.size)
                    assertFalse(file.exists())
                } finally {
                    client.dispatcher.executorService.shutdownNow()
                    file.delete()
                }
            }
        }

    @Test
    fun concurrentManhwaFallbacksStayBoundedAndKeepTheirOwnPaths() =
        kotlinx.coroutines.runBlocking {
            val calls = mutableListOf<okhttp3.Request>()
            val client =
                okhttp3.OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request()
                        synchronized(calls) { calls += request }
                        okhttp3.Response.Builder()
                            .request(request)
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(526)
                            .message("fixture")
                            .body("".toResponseBody())
                            .build()
                    }
                    .build()
            val sources = Sources(client)
            val files = (0 until 4).map { java.io.File.createTempFile("komori-manhwa-concurrent", ".part").apply { delete() } }
            try {
                val failures =
                    kotlinx.coroutines.coroutineScope {
                        files.indices.map { index ->
                            async(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching {
                                    sources.transferPage(
                                        sourceId("manhwa18", "/manga/fixture/chap-$index"),
                                        "https://cdn.manhwa18.com/chapters/$index.jpg",
                                        files[index],
                                    )
                                }.exceptionOrNull() as? Exception
                            }
                        }.awaitAll()
                    }
                assertTrue(failures.all { originalFailure(it) is SourceHttpException })
                val grouped = calls.groupBy { it.url.encodedPath }
                assertEquals(4, grouped.size)
                for ((path, requests) in grouped) {
                    assertEquals(2, requests.size)
                    assertEquals(
                        setOf("cdn.manhwa18.com", "min.manhwa18.net"),
                        requests.map { it.url.host }.toSet(),
                    )
                    assertTrue(path.matches(Regex("/chapters/[0-3][.]jpg")))
                }
                assertTrue(files.none { it.exists() })
            } finally {
                client.dispatcher.executorService.shutdownNow()
                files.forEach { it.delete() }
            }
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
