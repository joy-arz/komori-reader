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
