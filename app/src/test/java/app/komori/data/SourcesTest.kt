package app.komori.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

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
            listOf("weeb", "demonic", "comix", "mangadex"),
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
}
