package app.komori.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class SourcesTest {
    @Test
    fun idsAreSeparatedAndSafeForDownloadedFiles() {
        val path = "/chaptered.php?manga=7&chapter=1.5"
        val first = sourceId("demonic", path)
        assertNotEquals(first, sourceId("weeb", path))
        assertFalse(first.contains('/'))
        assertEquals(path, sourcePath(first))
        assertEquals("mangadex", sourceKey("a-legacy-uuid"))
        assertEquals(
            listOf("weeb", "demonic", "mangadex", "zap", "imhentai", "nhentai"),
            sourceOrder("weeb,weeb,unknown"),
        )
    }

    @Test
    fun warnsOnlyAboutSavedMatchingTitles() {
        val a = Manga("one", "One-Punch Man", "", "")
        val b = Manga("two", "ONE PUNCH MAN", "", "")
        val c = Manga("three", "One Punch Man 2", "", "")
        assertEquals(
            listOf(b),
            duplicateTitles(
                a,
                listOf(a, b, c),
                listOf(Shelf("two", saved = true), Shelf("three", saved = true)),
            ),
        )
        assertTrue(duplicateTitles(a, listOf(b), listOf(Shelf("two", liked = true))).isEmpty())
    }

    @Test
    fun parserExcludesAdsAndPreservesImageOrder() {
        val source = WebSource("demonic", "https://example.com", MangaDex.client())
        val doc =
            Jsoup.parse(
                """<img class="imgholder" src="/ad.jpg"><img class="imgholder" alt="chapter 1 page 1" src="https://cdn.example.com/1.webp"><img class="imgholder" alt="chapter 1 page 2" src="https://cdn.example.com/2.webp"><img src="/avatar.png">""",
                "https://example.com",
            )
        assertEquals(
            listOf("https://cdn.example.com/1.webp", "https://cdn.example.com/2.webp"),
            source.parsePages(doc),
        )
    }

    @Test
    fun demonicBrowseSendsSortAndTagsOnEveryPage() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setBody(
                        """<div class="advanced-element"><a href="/manga/Test" title="Test"><img src="/cover.jpg"></a></div><a href="/advanced.php?list=3">Next</a>"""
                    )
            )
            val source =
                WebSource("demonic", server.url("/").toString().trimEnd('/'), MangaDex.client())
            val page = source.browse("", 2, BrowseFilter("Views", setOf("1", "2")))
            assertTrue(page.hasMore)
            assertEquals("Test", page.titles.single().title)
            val request = server.takeRequest()
            assertEquals("/advanced.php?list=2", request.path)
            assertEquals("POST", request.method)
            assertTrue(request.body.readUtf8().contains("genres%5B%5D=1"))
        }
    }

    @Test
    fun allChaptersRetainedUntilNativeReaderResolvesPageCount() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setBody(
                        """<a class="chplinks" href="/chaptered.php?manga=7&amp;chapter=2">Chapter 2</a><a class="chplinks" href="/chaptered.php?manga=7&amp;chapter=1">Chapter 1</a>"""
                    )
            )
            val source =
                WebSource("demonic", server.url("/").toString().trimEnd('/'), MangaDex.client())
            val chapters = source.chapters(sourceId("demonic", "/manga/Test")).map { it.release() }
            assertEquals(2, chapters.size)
            assertTrue(chapters.all { it.readable && it.pages == -1 })
            assertEquals(
                "1",
                app.komori.domain.ChapterPolicy.start(chapters, null, emptySet(), false)?.number,
            )
        }
    }

    @Test
    fun tagsUseSourceIdentifiers() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody("""<li><input name="genres[]" value="1">Action</li>""")
            )
            val source =
                WebSource("demonic", server.url("/").toString().trimEnd('/'), MangaDex.client())
            assertEquals(listOf(SourceTag("1", "Action")), source.tags())
        }
    }

    @Test
    fun weebUsesFullChapterEndpointAndImageEndpoint() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setBody(
                        """<div x-data="{}"><a href="/chapters/ABC"><span class="flex"><span>Chapter 3</span></span></a></div>"""
                    )
            )
            server.enqueue(
                MockResponse()
                    .setBody(
                        """<section x-data="scroll"><img src="https://cdn.example.com/1.webp"></section>"""
                    )
            )
            val source =
                WebSource("weeb", server.url("/").toString().trimEnd('/'), MangaDex.client())
            val chapters = source.chapters(sourceId("weeb", "/series/ID/Title"))
            assertEquals("/series/ID/full-chapter-list", server.takeRequest().path)
            assertEquals("3", chapters.single().release().number)
            assertEquals(1, source.pages(chapters.single().id).size)
            assertEquals(
                "/chapters/ABC/images?is_prev=False&reading_style=long_strip",
                server.takeRequest().path,
            )
        }
    }

    @Test
    fun blockedResponseDoesNotReplaceCachedChaptersWithEmptyList() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<title>Just a moment...</title>"))
            val source =
                WebSource("weeb", server.url("/").toString().trimEnd('/'), MangaDex.client())
            try {
                source.chapters(sourceId("weeb", "/series/ID/Title"))
                fail("Expected access error")
            } catch (_: IOException) {}
        }
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/sources/$name.html")).readText()

    @Test
    fun zapUsesGalleryReaderAndCompletePageManifest() = runBlocking {
        MockWebServer().use { server ->
            val source = WebSource("zap", server.url("/").toString().trimEnd('/'), MangaDex.client())
            server.enqueue(MockResponse().setBody(fixture("zap-browse")))
            val page = source.browse("", 1, BrowseFilter())
            assertEquals("/?page=1", server.takeRequest().path)
            assertTrue(page.hasMore)
            val title = page.titles.single()
            assertEquals("/gallery/7/", sourcePath(title.id))
            assertEquals("https://cdn.example.com/zap/thumb.jpg", title.cover)
            server.enqueue(MockResponse().setBody(fixture("zap-gallery")))
            val details = source.details(title)
            assertEquals("/gallery/7/", server.takeRequest().path)
            assertEquals("Sample gallery description", details.description)
            assertEquals("https://cdn.example.com/zap/cover.jpg", details.cover)
            server.enqueue(MockResponse().setBody(fixture("zap-gallery")))
            val chapter = source.chapters(title.id).single()
            assertEquals("/gallery/7/", server.takeRequest().path)
            assertNull(chapter.release().number)
            assertEquals("Full gallery", chapter.release().label)
            assertTrue(chapter.release().readable)
            server.enqueue(MockResponse().setBody(fixture("zap-reader")))
            assertEquals(listOf("https://cdn.example.com/zap/1.webp", "https://cdn.example.com/zap/2.webp"), source.pages(chapter.id))
            val request = server.takeRequest()
            assertEquals("/g/7/1/", request.path)
            assertEquals(server.url("/").toString(), request.getHeader("Referer"))
        }
    }

    @Test
    fun imhentaiUsesGalleryReaderAndEveryHtmlPage() = runBlocking {
        MockWebServer().use { server ->
            val source = WebSource("imhentai", server.url("/").toString().trimEnd('/'), MangaDex.client())
            server.enqueue(MockResponse().setBody(fixture("imhentai-browse")))
            val page = source.browse("", 1, BrowseFilter())
            assertEquals("/?page=1", server.takeRequest().path)
            assertTrue(page.hasMore)
            val title = page.titles.single()
            assertEquals("https://cdn.example.com/im/thumb.jpg", title.cover)
            server.enqueue(MockResponse().setBody(fixture("imhentai-gallery")))
            val details = source.details(title)
            assertEquals("/gallery/8/", server.takeRequest().path)
            assertEquals("https://cdn.example.com/im/cover.jpg", details.cover)
            assertEquals("Sample gallery description", details.description)
            server.enqueue(MockResponse().setBody(fixture("imhentai-gallery")))
            val chapter = source.chapters(title.id).single()
            assertEquals("/gallery/8/", server.takeRequest().path)
            assertNull(chapter.release().number)
            assertEquals("Full gallery", chapter.release().label)
            assertTrue(chapter.release().readable)
            server.enqueue(MockResponse().setBody(fixture("imhentai-reader")))
            server.enqueue(MockResponse().setBody(fixture("imhentai-reader").replace("image_1", "image_2").replace("/1.webp", "/2.png")))
            assertEquals(listOf("https://cdn.example.com/im/1.webp", "https://cdn.example.com/im/2.png"), source.pages(chapter.id))
            assertEquals("/view/8/1/", server.takeRequest().path)
            assertEquals("/view/8/2/", server.takeRequest().path)
        }
    }

    @Test
    fun gallerySearchKeepsSortAndCategoriesOnLaterPages() = runBlocking {
        for (key in listOf("zap", "imhentai")) {
            MockWebServer().use { server ->
                val source = WebSource(key, server.url("/").toString().trimEnd('/'), MangaDex.client())
                server.enqueue(MockResponse().setBody(fixture("$key-browse")))
                source.browse("sample & title", 2, BrowseFilter("Popular", setOf("m")))
                val request = server.takeRequest()
                val url = checkNotNull(request.requestUrl)
                assertEquals("GET", request.method)
                assertEquals("/search/", url.encodedPath)
                assertEquals("sample & title", url.queryParameter("key"))
                assertEquals("2", url.queryParameter("page"))
                assertEquals("1", url.queryParameter("pp"))
                assertEquals(if (key == "zap") null else "0", url.queryParameter("lt"))
                assertEquals("1", url.queryParameter("m"))
                assertEquals(if (key == "zap") null else "0", url.queryParameter("d"))
                assertEquals("1", url.queryParameter("en"))
                if (key == "zap") assertEquals("yes", url.queryParameter("filter"))
            }
        }
    }

    @Test
    fun incompleteGalleryReadersFailInsteadOfSavingPartialPages() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/").toString().trimEnd('/')
            server.enqueue(MockResponse().setBody(fixture("zap-reader").replace("data-reader-total=\"2\"", "data-reader-total=\"3\"")))
            try {
                WebSource("zap", base, MangaDex.client()).pages(sourceId("zap", "/g/7/1/"))
                fail("Expected incomplete manifest error")
            } catch (e: IOException) {
                assertTrue(e.message.orEmpty().contains("Incomplete"))
            }
            server.enqueue(MockResponse().setBody(fixture("imhentai-reader")))
            server.enqueue(MockResponse().setBody(fixture("imhentai-reader")))
            try {
                WebSource("imhentai", base, MangaDex.client()).pages(sourceId("imhentai", "/view/8/1/"))
                fail("Expected repeated reader page error")
            } catch (e: IOException) {
                assertTrue(e.message.orEmpty().contains("page 2"))
            }
        }
    }

    @Test
    fun galleryKeysPreserveLegacyOrderAndImageReferers() {
        val keys = listOf("zap", "imhentai", "nhentai")
        assertTrue(keys.all { Regex("[a-z0-9]+").matches(it) })
        assertEquals(6, sourceNames.size)
        assertEquals(listOf("demonic", "weeb", "mangadex") + keys,
            sourceOrder("demonic,weeb,comix,mangadex"))
        val sources = Sources(MangaDex.client())
        for ((key, base) in listOf("zap" to "https://hentaizap.com", "imhentai" to "https://imhentai.xxx")) {
            val id = sourceId(key, "/gallery/7/")
            assertEquals("$base/gallery/7/", sourcePageUrl(id))
            assertEquals("$base/", sources.imageRequest("https://cdn.example.com/1.webp", id).header("Referer"))
        }
    }

    @Test
    fun nhentaiUsesGalleryReaderAndEmbeddedApiManifest() = runBlocking {
        MockWebServer().use { server ->
            val source = WebSource("nhentai", server.url("/").toString().trimEnd('/'), MangaDex.client())
            server.enqueue(MockResponse().setBody(fixture("nhentai-browse")))
            val page = source.browse("", 1, BrowseFilter())
            assertEquals("/?page=1", server.takeRequest().path)
            assertTrue(page.hasMore)
            val title = page.titles.single()
            assertEquals("/g/9/", sourcePath(title.id))
            assertEquals("Sample gallery", title.title)
            assertEquals("https://thumbs.example.com/galleries/90/thumb.webp", title.cover)
            server.enqueue(MockResponse().setBody(fixture("nhentai-gallery")))
            val details = source.details(title)
            assertEquals("/g/9/", server.takeRequest().path)
            assertEquals("[Artist] Sample gallery", details.title)
            assertEquals("Sample gallery description\n\nTags: colour", details.description)
            assertEquals("https://thumbs.example.com/galleries/90/cover.webp.webp", details.cover)
            server.enqueue(MockResponse().setBody(fixture("nhentai-gallery")))
            val chapter = source.chapters(title.id).single()
            assertEquals("/g/9/", server.takeRequest().path)
            assertNull(chapter.release().number)
            assertEquals("Full gallery", chapter.release().label)
            assertTrue(chapter.release().readable)
            server.enqueue(MockResponse().setBody(fixture("nhentai-reader")))
            assertEquals(listOf("https://images.example.com/galleries/90/1.webp", "https://images.example.com/galleries/90/2.webp.webp"), source.pages(chapter.id))
            val request = server.takeRequest()
            assertEquals("/g/9/1/", request.path)
            assertEquals(server.url("/").toString(), request.getHeader("Referer"))
            assertEquals(4, server.requestCount)
            assertEquals("https://nhentai.net/g/9/", sourcePageUrl(title.id))
            assertEquals("https://nhentai.net/", Sources(MangaDex.client())
                .imageRequest("https://images.example.com/galleries/90/1.webp", chapter.id).header("Referer"))
        }
    }

    @Test
    fun nhentaiSearchUsesVerifiedQueryField() = runBlocking {
        MockWebServer().use { server ->
            val source = WebSource("nhentai", server.url("/").toString().trimEnd('/'), MangaDex.client())
            server.enqueue(MockResponse().setBody(fixture("nhentai-browse")))
            source.browse("sample & title", 2, BrowseFilter("Latest"))
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/search/", request.requestUrl!!.encodedPath)
            assertEquals("sample & title", request.requestUrl!!.queryParameter("q"))
            assertEquals("2", request.requestUrl!!.queryParameter("page"))
            assertTrue(Sources(MangaDex.client()).sorts("nhentai").contains("Popular all time"))
        }
    }

    @Test
    fun nhentaiRejectsIncompleteAndMismatchedManifests() {
        val source = WebSource("nhentai", "https://example.com", MangaDex.client())
        val valid = fixture("nhentai-reader")
        val escaped = JSONObject(checkNotNull(Jsoup.parse(valid)
            .selectFirst("script[data-url=/api/v2/galleries/9]")).data())
        val payload = JSONObject(escaped.getString("body"))
        val invalid = listOf(
            "Incomplete" to JSONObject(escaped.toString()).put("body", JSONObject(payload.toString()).put("num_pages", 3).toString()),
            "identity does not match" to JSONObject(escaped.toString()).put("body", JSONObject(payload.toString()).put("id", 10).toString()),
            "Invalid Nhentai page path" to JSONObject(escaped.toString()).put("body", JSONObject(payload.toString()).put("media_id", "91").toString()),
            "unavailable" to JSONObject(escaped.toString()).put("status", 403),
        )
        for ((message, manifest) in invalid) {
            val d = Jsoup.parse(valid, "https://example.com/g/9/1/")
            d.selectFirst("script[data-url=/api/v2/galleries/9]")!!.html(manifest.toString())
            try {
                source.parsePages(d)
                fail("Expected invalid manifest error")
            } catch (e: IOException) {
                assertTrue(e.message.orEmpty().contains(message))
            }
        }
        val missing = Jsoup.parse(valid, "https://example.com/g/9/1/")
        missing.select("script[data-url=/api/v2/galleries/9]").remove()
        try {
            source.parsePages(missing)
            fail("Expected missing manifest error")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("No page manifest"))
        }
    }
}
