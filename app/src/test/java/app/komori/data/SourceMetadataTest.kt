package app.komori.data

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class SourceMetadataTest {
    @Test fun metadataUsesSourceScopedCreatorsAndCleanTagLabels() {
        for (key in listOf("zap", "nhentai")) {
            val source = WebSource(key, "https://example.com", MangaDex.client())
            val container = if (key == "zap") "class='hz-gallery-overview'" else "id='tags'"
            val doc = Jsoup.parse("""
                <section $container>
                <a href='/artist/sample-artist/'><span class='name'>Sample Artist</span><span class='count'>1K</span></a>
                <a href='/author/sample-author/'>Sample Author</a>
                <a href='/tag/colour/'><span>Colour</span><span class='count'>20K</span></a>
                <a href='/tag/colour/'>Duplicate</a>
                <a href='https://untrusted.example/artist/injected/'>Injected</a>
                </section>
                <aside><a href='/artist/unrelated/'>Sidebar creator</a></aside>
            """, "https://example.com/gallery/1/")
            val details = source.parseDetails(Manga(sourceId(key, "/gallery/1/"), "Sample", "", ""), doc)
            val creators = JSONArray(details.creatorsJson).objects()
            assertEquals(listOf("Sample Artist", "Sample Author"), creators.map { it.getString("name") })
            assertEquals(listOf("artist", "author"), creators.map { it.getString("role") })
            assertTrue(creators.all { sourceKey(it.getString("id")) == key })
            assertEquals("/artist/sample-artist/", sourcePath(creators.first().getString("id")))
            val tags = JSONArray(details.tagsJson).objects()
            assertEquals(1, tags.size)
            assertEquals("Colour", tags.single().getString("name"))
            assertEquals("/tag/colour/", tags.single().getString("id"))
        }
    }

    @Test fun followBrowsePreservesSourceSortAndPagination() = runBlocking {
        for (key in listOf("zap", "nhentai")) MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<div></div>"))
            if (key == "zap") server.enqueue(MockResponse().setBody("<div></div>"))
            val source = WebSource(key, server.url("/").toString().trimEnd('/'), MangaDex.client())
            source.browse("", 3, BrowseFilter(if (key == "zap") "Popular" else "Popular all time"),
                Creator(sourceId(key, "/artist/sample-artist/"), "Sample Artist", "artist"))
            val request = server.takeRequest().requestUrl!!
            assertEquals(if (key == "zap") "/artist/sample-artist/popular/" else "/artist/sample-artist/", request.encodedPath)
            assertEquals("3", request.queryParameter("page"))
            if (key == "zap") {
                assertNull(request.queryParameter("pp"))
                val supplementary = server.takeRequest().requestUrl!!
                assertEquals("/search/", supplementary.encodedPath)
                assertEquals("Sample Artist", supplementary.queryParameter("key"))
                assertEquals("1", supplementary.queryParameter("pp"))
                assertEquals("3", supplementary.queryParameter("page"))
            }
            else assertEquals("popular", request.queryParameter("sort"))
        }
    }

    @Test fun metadataSearchEncodesUntrustedInputAsOneSegment() = runBlocking {
        MockWebServer().use { server ->
            val source = WebSource("zap", server.url("/").toString().trimEnd('/'), MangaDex.client())
            server.enqueue(MockResponse().setBody(""))
            server.enqueue(MockResponse().setBody(""))
            source.browse("Sample Artist", 1, BrowseFilter(searchType = "Author"))
            assertEquals("/artist/sample-artist/", server.takeRequest().requestUrl!!.encodedPath)
            assertEquals("/search/", server.takeRequest().requestUrl!!.encodedPath)
            server.enqueue(MockResponse().setBody(""))
            source.browse("colour", 2, BrowseFilter(searchType = "Tag"))
            assertEquals("/tag/colour/", server.takeRequest().requestUrl!!.encodedPath)
            try {
                source.browse("", 1, BrowseFilter(facetPath = "//evil.example/tag/colour/"))
                fail("Expected invalid metadata path")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun nhentaiTagSearchPreservesPopularityAndQueryOnLaterPages() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(""))
            val source = WebSource("nhentai", server.url("/").toString().trimEnd('/'), MangaDex.client())
            source.browse("sample", 4, BrowseFilter("Popular this week", setOf("/tag/colour/")))
            val url = server.takeRequest().requestUrl!!
            assertEquals("sample tag:\"colour\"", url.queryParameter("q"))
            assertEquals("popular-week", url.queryParameter("sort"))
            assertEquals("4", url.queryParameter("page"))
            server.enqueue(MockResponse().setBody("<a href='/tag/colour/'><span class='name'>Colour</span><span class='count'>20</span></a>"))
            assertEquals(listOf(SourceTag("/tag/colour/", "Colour")), source.tags())
            assertEquals("/tags/", server.takeRequest().path)
        }
    }

    @Test fun plain403AndVerificationChallengeRemainDistinct() = runBlocking {
        MockWebServer().use { server ->
            val source = WebSource("zap", server.url("/").toString().trimEnd('/'), MangaDex.client())
            server.enqueue(MockResponse().setResponseCode(403))
            try {
                source.browse("", 1, BrowseFilter())
                fail("Expected HTTP failure")
            } catch (e: SourceHttpException) { assertEquals(403, e.status) }
            server.enqueue(MockResponse().setBody("<title>Just a moment</title>"))
            try {
                source.browse("", 1, BrowseFilter())
                fail("Expected verification failure")
            } catch (e: SourceVerificationRequired) { assertEquals("zap", e.source) }
        }
    }

    @Test fun removedComixIsDiscardedFromExistingSourcePreferences() {
        assertFalse("comix" in sourceNames)
        assertFalse("comix" in sourceOrder("comix,weeb,comix,mangadex"))
        assertEquals("weeb", sourceOrder("comix,weeb,comix,mangadex").first())
    }

    @Test fun cookieJarIsIsolatedToHttpsSourceHosts() {
        val reads = mutableListOf<String>()
        val writes = mutableListOf<String>()
        val jar = SourceCookieJar(object : SourceCookieStore {
            override fun read(url: String): String { reads += url; return "session=abc=123; other=value" }
            override fun write(url: String, cookie: String) { writes += cookie }
            override fun flush() {}
        })
        assertEquals("abc=123", jar.loadForRequest("https://nhentai.net/g/1/".toHttpUrl()).first().value)
        for (url in listOf("https://cdn.example.com/", "http://nhentai.net/", "https://nhentai.net.evil.example/"))
            assertTrue(jar.loadForRequest(url.toHttpUrl()).isEmpty())
        assertEquals(1, reads.size)
        val url = "https://nhentai.net/".toHttpUrl()
        jar.saveFromResponse(url, listOf(okhttp3.Cookie.Builder().name("session").value("new").domain("nhentai.net").path("/g").build()))
        assertEquals(1, writes.size)
        jar.saveFromResponse("https://cdn.example.com/".toHttpUrl(), listOf(okhttp3.Cookie.Builder().name("session").value("new").domain("cdn.example.com").build()))
        assertEquals(1, writes.size)
    }
}
