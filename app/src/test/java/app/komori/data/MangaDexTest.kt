package app.komori.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MangaDexTest {
    @Test
    fun `connection reset retries once with HTTP1 compatibility and identity`() = runBlocking {
        var requests = 0
        val client =
            MangaDex.client()
                .newBuilder()
                .addInterceptor { chain ->
                    requests++
                    assertTrue(chain.request().header("User-Agent")!!.startsWith("Komori/"))
                    if (requests == 1) throw java.net.SocketException("Connection reset")
                    okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            okhttp3.ResponseBody.create(
                                null,
                                "{\"data\":[],\"offset\":0,\"total\":0}",
                            )
                        )
                        .build()
                }
                .build()
        assertTrue(MangaDex(client).chapters("test", "en").isEmpty())
        assertEquals(2, requests)
    }

    @Test
    fun `rate limits are not immediately retried`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
            val source = MangaDex(MangaDex.client(), server.url("/").toString().trimEnd('/'))
            try {
                source.chapters("test", "en")
                fail("Expected failure")
            } catch (_: IOException) {}
            try {
                source.chapters("another", "en")
                fail("Expected origin cooldown")
            } catch (e: SourceHttpException) {
                assertTrue(e.deferred)
                assertTrue(e.retryAtMillis!! > System.currentTimeMillis())
            }
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `image servers use standard HTTPS port`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"baseUrl":"https://images.example","chapter":{"hash":"h","dataSaver":["1.jpg"]}}"""
                    )
            )
            val source = MangaDex(MangaDex.client(), server.url("/").toString().trimEnd('/'))
            assertEquals(
                listOf("https://images.example/data-saver/h/1.jpg"),
                source.pages("test", true),
            )
            assertEquals("true", server.takeRequest().requestUrl!!.queryParameter("forcePort443"))
        }
    }

    @Test
    fun `HTTP2 stream resets qualify but HTTP status and TLS errors do not`() {
        assertTrue(isConnectionReset(IOException("stream was reset: INTERNAL_ERROR")))
        assertFalse(isConnectionReset(IOException("MangaDex returned 403")))
        assertFalse(isConnectionReset(javax.net.ssl.SSLHandshakeException("Certificate rejected")))
    }

    @Test
    fun `successful compatibility recovery is reused for later requests`() = runBlocking {
        var attempts = 0
        val client =
            MangaDex.client()
                .newBuilder()
                .addInterceptor { chain ->
                    attempts++
                    if (attempts == 1) throw IOException("stream was reset: INTERNAL_ERROR")
                    okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            okhttp3.ResponseBody.create(
                                null,
                                "{\"data\":[],\"offset\":0,\"total\":0}",
                            )
                        )
                        .build()
                }
                .build()
        val source = MangaDex(client)
        source.chapters("one", "en")
        source.chapters("two", "en")
        assertEquals(3, attempts)
        val field =
            MangaDex::class.java.getDeclaredField("compatibilityUntil").apply {
                isAccessible = true
            }
        assertTrue(field.getLong(source) > System.nanoTime())
    }

    @Test
    fun `MangaDex uses API client independently of browser sessions`() = runBlocking {
        var calls = 0
        val api =
            MangaDex.client()
                .newBuilder()
                .addInterceptor { chain ->
                    calls++
                    assertTrue(chain.request().header("User-Agent")!!.startsWith("Komori/"))
                    okhttp3.Response.Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(okhttp3.ResponseBody.create(null, "{\"data\":[]}"))
                        .build()
                }
                .build()
        val web =
            MangaDex.client()
                .newBuilder()
                .addInterceptor { throw AssertionError("Browser client reached") }
                .build()
        assertTrue(Sources(web, api).tags("mangadex").isEmpty())
        assertEquals(1, calls)
    }

    private fun response(offset: Int, total: Int, ids: List<String>): MockResponse =
        MockResponse()
            .setBody(
                JSONObject()
                    .put("offset", offset)
                    .put("total", total)
                    .put("data", JSONArray(ids.map { JSONObject().put("id", it) }))
                    .toString()
            )

    @Test
    fun `walks every feed page and keeps hosted and external releases`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response(0, 503, (0 until 500).map { "$it" }))
            server.enqueue(response(500, 503, listOf("500", "501", "502")))
            val source = MangaDex(MangaDex.client(), server.url("/").toString().trimEnd('/'))
            assertEquals(503, source.chapters("manga", "en").size)
            val first = server.takeRequest().requestUrl!!
            assertEquals("en", first.queryParameter("translatedLanguage[]"))
            assertEquals("1", first.queryParameter("includeUnavailable"))
            assertNull(first.queryParameter("includeExternalUrl"))
            assertEquals("500", server.takeRequest().requestUrl!!.queryParameter("offset"))
        }
    }

    @Test
    fun `all language feed omits server language restriction`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response(0, 0, emptyList()))
            val source = MangaDex(MangaDex.client(), server.url("/").toString().trimEnd('/'))
            assertTrue(source.chapters("manga", "*").isEmpty())
            assertNull(server.takeRequest().requestUrl!!.queryParameter("translatedLanguage[]"))
        }
    }

    @Test
    fun `truncated feed fails instead of returning a partial success`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response(0, 2, listOf("one")))
            server.enqueue(response(1, 2, emptyList()))
            val source = MangaDex(MangaDex.client(), server.url("/").toString().trimEnd('/'))
            try {
                source.chapters("manga", "en")
                fail("A partial result must not replace the cache")
            } catch (_: IOException) {}
        }
    }

    @Test
    fun `parses source unavailability without losing metadata`() {
        val chapter =
            JSONObject(
                """{"id":"one","attributes":{"chapter":"1","volume":null,"pages":30,"translatedLanguage":"en","isUnavailable":true},"relationships":[{"type":"manga","id":"manga"}]}"""
            )
        val release = releaseFromJson(chapter)
        assertTrue(release.unavailable)
        assertFalse(release.readable)
        assertEquals("1", release.number)
    }
}
