package app.komori.data

import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ScanlationSourceTest {
    @Test fun catalogsExcludeNovelsChaptersForeignLinksAndDuplicateCards() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/").toString().trimEnd('/')
            for ((key, root) in listOf("asura" to "comics", "hive" to "series")) {
                val source = ScanlationSource(key, base, MangaDex.client())
                server.enqueue(MockResponse().setBody("""
                    <a href='/$root/test'><img src='https://cdn.example/cover.webp' alt='Test'></a>
                    <a href='/$root/test'><img src='https://cdn.example/cover.webp' alt='Test'></a>
                    <a href='/novels/test'><img src='https://cdn.example/novel.webp' alt='Novel'></a>
                    <a href='/$root/test/chapter-1'><img src='https://cdn.example/page.webp' alt='Chapter'></a>
                    <a href='https://evil.example/$root/wrong'><img src='https://cdn.example/wrong.webp' alt='Wrong'></a>
                    <a href='?page=3'>Next</a>
                """))
                val page = source.browse("one & two", 2, BrowseFilter(sort = if (key == "asura") "Title" else "Popularity"))
                assertEquals("Test", page.titles.single().title)
                assertEquals("/$root/test", sourcePath(page.titles.single().id))
                assertTrue(page.hasMore)
                val request = server.takeRequest().requestUrl!!
                assertEquals("one & two", request.queryParameter(if (key == "asura") "q" else "searchTerm"))
                assertEquals("2", request.queryParameter("page"))
                assertEquals(if (key == "asura") "name" else "popular", request.queryParameter(if (key == "asura") "sort" else "sortBy"))
            }
        }
    }
    @Test fun readerSelectorsExcludeAdsAndKeepOrderAndSignedUrls() {
        for ((key, attribute) in listOf("asura" to "data-page-index='0'", "hive" to "data-reader-page-image='true'")) {
            val source = ScanlationSource(key, "https://source.example", MangaDex.client())
            val pages = source.parsePages(Jsoup.parse("""<img src='https://ads.example/a.webp'>
                <img $attribute src='https://cdn.example/1.webp?v=2'><img $attribute src='https://cdn.example/2.webp'>
                <img $attribute src='https://cdn.example/2.webp'>"""))
            assertEquals(listOf("https://cdn.example/1.webp?v=2", "https://cdn.example/2.webp"), pages)
        }
    }
    @Test fun hiveUsesAllSsrChaptersIncludingUnrenderedRowsAndLockedState() {
        val source = ScanlationSource("hive", "https://hivetoons.org", MangaDex.client())
        val doc = Jsoup.parse("""<a href='/series/test/chapter-9'>Only rendered row</a>
            <script data-tsr-stream-part>initialChapters:[$ R[0]={id:9,slug:"chapter-9",number:9,title:"Locked",isLocked:!0,mangaPost:{id:void 0}},
            $ R[1]={id:1,slug:"chapter-1_01",number:1,title:"First \\"title\\"",chapterStatus:"PUBLIC",isLocked:!1,mangaPost:{id:void 0}}]</script>""".replace("\\\\", "\\"))
        val rows = source.parseChapters(doc, sourceId("hive", "/series/test")).map { it.release() }
        assertEquals(2, rows.size)
        assertTrue(rows[0].unavailable)
        assertTrue(rows[1].readable)
        assertEquals("1", rows[1].number)
        assertEquals("First \"title\"", rows[1].title)
    }
    @Test fun asuraReadsFullEncodedChapterDataAndRejectsLockedReader() {
        val source = ScanlationSource("asura", "https://asurascans.com", MangaDex.client())
        val props = """{"chapters":[1,[[0,{"number":[0,1],"title":[0,"First"],"page_count":[0,12],"is_premium":[0,false]}],[0,{"number":[0,2],"title":[0,"Locked"],"page_count":[0,8],"is_premium":[0,true]}]]]}"""
        val html = "<astro-island component-url='/_astro/ChapterListReact.fixture.js' props='${props}'></astro-island>"
        val rows = source.parseChapters(Jsoup.parse(html), sourceId("asura", "/comics/test")).map { it.release() }
        assertEquals(listOf("1", "2"), rows.map { it.number })
        assertEquals(12, rows[0].pages)
        assertTrue(rows[0].readable)
        assertTrue(rows[1].unavailable)
        assertEquals("https://asurascans.com/comics/test/chapter/2", rows[1].externalUrl)
        val locked = Jsoup.parse("""<astro-island component-url='/_astro/ChapterReader.fixture.js' props='{"isLocked":[0,true]}'></astro-island><img data-page-index='0' src='https://cdn.example/teaser.webp'>""")
        assertThrows(ContentFailure::class.java) { source.parsePages(locked) }
    }
    @Test fun scanlationPageRequestsAdvertiseHtmlToAvoidEdgeChallenges() = runBlocking {
        MockWebServer().use { server ->
            for (key in scanlationSources) {
                val source = ScanlationSource(key, server.url("/").toString().trimEnd('/'), MangaDex.client())
                server.enqueue(MockResponse().setBody("<h1>Fixture</h1>"))
                val root = if (key == "asura") "comics" else "series"
                source.details(Manga(sourceId(key, "/$root/fixture"), "", "", ""))
                val accept = server.takeRequest().getHeader("Accept")
                assertTrue("$key must request HTML", accept != null && accept.startsWith("text/html"))
            }
        }
    }
    @Test fun namespacesOriginsAndFreshOrderingRemainSeparate() {
        val source = Sources(MangaDex.client())
        assertEquals(listOf("asura", "hive", "demonic", "weeb", "atsu"), enabledSourceOrder(Settings()))
        for (key in scanlationSources) {
            val id = sourceId(key, if (key == "asura") "/comics/test" else "/series/test")
            assertEquals(sourceOrigins[key] + sourcePath(id), sourcePageUrl(id))
            assertEquals(sourceOrigins[key] + "/", source.imageRequest("https://cdn.example/page.webp", id).header("Referer"))
            assertFalse(SourceConsentPolicy.needsConfirmation(key, true, false))
        }
        assertEquals(listOf("asura", "hive", "weeb", "atsu"), sourceOrder("weeb,demonic").take(4))
    }
    @Test fun observedPublicPagesParseWhenLocalCaptureIsProvided() {
        val directory = System.getenv("KOMORI_SOURCE_CAPTURE")
        assumeTrue("Optional live HTML capture", directory != null)
        for (key in scanlationSources) {
            val name = if (key == "asura") "asura" else "hive"
            val base = sourceOrigins.getValue(key)
            val source = ScanlationSource(key, base, MangaDex.client())
            fun doc(kind: String) = Jsoup.parse(File(directory, "$name-$kind.html"), "UTF-8", base)
            val catalog = source.parseBrowse(doc("browse"), 1)
            assertTrue(catalog.titles.isNotEmpty())
            assertTrue(catalog.hasMore)
            assertTrue(source.parseTags(doc("browse")).isNotEmpty())
            val id = sourceId(key, if (key == "asura") "/comics/overgeared-ab39de10" else "/series/crocodile")
            val metadata = source.parseDetails(doc("detail"), Manga(id, "Test", "", ""))
            assertTrue(metadata.description.isNotBlank())
            assertTrue(metadata.cover.startsWith("https://"))
            val chapters = source.parseChapters(doc("detail"), id)
            assertEquals(if (key == "asura") 347 else 28, chapters.size)
            assertTrue(chapters.any { it.release().number == "1" })
            assertTrue(source.parsePages(doc("chapter")).size > 2)
            if (key == "hive" && File(directory, "hive-long-detail.html").exists()) {
                val long = Jsoup.parse(File(directory, "hive-long-detail.html"), "UTF-8", base)
                assertEquals(640, source.parseChapters(long, sourceId(key, "/series/lookism")).size)
            }
        }
    }
}
