package app.komori.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class ManhwaSourceTest {
    private fun comCard(slug: String = "fixture") = """<div class='thumb-item-flow'><div class='lazy-bg' data-bg='https://cdn.example/cover.jpg'></div><h4 class='series-title'><a href='/manga/$slug'>Fixture $slug</a></h4></div>"""
    private fun ccCard(slug: String = "fixture") = """<div class='page-item-detail'><h3><a href='/webtoon/$slug'>Fixture $slug</a></h3><img src='/placeholder.gif' data-src='https://cdn.example/cover.jpg'></div>"""

    @Test fun comFiltersChaptersAndLazyReaderImagesRoundTrip() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/").toString().trimEnd('/')
            val source = ManhwaSource("manhwa18", base, MangaDex.client())
            server.enqueue(MockResponse().setBody(comCard() + "<div class='pagination_wrap'><a class='next'>Next</a></div>"))
            val page = source.browse("fixture", 3, BrowseFilter("Views", setOf("59"), language = "en", category = "26", status = "completed"))
            val url = server.takeRequest().requestUrl!!
            assertEquals("/tim-kiem", url.encodedPath)
            assertEquals("fixture", url.queryParameter("q"))
            assertEquals("top", url.queryParameter("sort"))
            assertEquals("3", url.queryParameter("page"))
            assertEquals(setOf("59", "26"), url.queryParameter("accept_genres")!!.split(',').toSet())
            assertEquals("3", url.queryParameter("status"))
            val book = page.titles.single()
            assertEquals("https://cdn.example/cover.jpg", book.cover)
            assertTrue(page.hasMore)
            server.enqueue(MockResponse().setBody("""<ul class='list-chapters'><a href='/manga/fixture/chapter-1' title='Chapter 1'>Read</a><a href='https://evil.example/manga/fixture/chapter-2'>Injected</a></ul>"""))
            val chapter = source.chapters(book.id).single().release()
            assertEquals("1", chapter.number)
            assertEquals("en", chapter.language)
            server.enqueue(MockResponse().setBody("""<img src='https://ad.example/ad.jpg'><div id='chapter-content'><img class='lazy' data-src='https://cdn.example/1.jpg'><img data-src='https://cdn.example/2.jpg'></div>"""))
            assertEquals(listOf("https://cdn.example/1.jpg", "https://cdn.example/2.jpg"), source.pages(chapter.id))
        }
    }

    @Test fun ccKeepsCanonicalPathsLazyImagesAndRawLanguageSeparate() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/").toString().trimEnd('/')
            val source = ManhwaSource("manhwa18cc", base, MangaDex.client())
            server.enqueue(MockResponse().setBody(ccCard() + ccCard("fixture-raw")))
            val page = source.browse("", 2, BrowseFilter(language = "ko"))
            assertEquals("/raw/2", server.takeRequest().requestUrl!!.encodedPath)
            assertEquals("/webtoon/fixture-raw", sourcePath(page.titles.single().id))
            assertEquals(2, page.paginationIds.size)
            server.enqueue(MockResponse().setBody("<ul class='row-content-chapter'><li><a href='/webtoon/fixture-raw/chapter-2'>Chapter 2</a></li></ul>"))
            assertEquals("ko", source.chapters(page.titles.single().id).single().release().language)
            assertEquals(listOf("https://cdn.example/page.webp"), source.parsePages(Jsoup.parse("""<div class='read-content'><img src='https://cdn.example/placeholder.gif' data-src='https://cdn.example/page.webp'></div><img src='https://ad.example/ad.png'>""", base)))
        }
    }

    @Test fun ccSearchRefinesMetadataWithoutLosingPaginationOnEmptyMatches() = runBlocking {
        MockWebServer().use { server ->
            val source = ManhwaSource("manhwa18cc", server.url("/").toString().trimEnd('/'), MangaDex.client())
            server.enqueue(MockResponse().setBody(ccCard() + "<div id='blog-pager'><li class='next'><a>Next</a></li></div>"))
            server.enqueue(MockResponse().setBody("""<div class='genres-content'><a href='/webtoon-genre/romance'>Romance</a></div>"""))
            val page = source.browse("a'b", 4, BrowseFilter(tags = setOf("/webtoon-genre/action")))
            assertTrue(page.titles.isEmpty())
            assertTrue(page.hasMore)
            assertEquals(1, page.paginationIds.size)
            assertEquals("a’b", server.takeRequest().requestUrl!!.queryParameter("q"))
            assertEquals("/webtoon/fixture", server.takeRequest().requestUrl!!.encodedPath)
        }
    }

    @Test fun sourceNamespacesAndImageReferersNeverOverlap() {
        val sources = Sources(MangaDex.client())
        for ((key, origin) in listOf("manhwa18" to "https://manhwa18.com", "manhwa18cc" to "https://manhwa18.cc")) {
            val id = sourceId(key, "/manga/fixture")
            assertEquals("$origin/manga/fixture", sourcePageUrl(id))
            assertEquals("$origin/", sources.imageRequest("https://cdn.example/1.jpg", id).header("Referer"))
            assertTrue(allowsVerificationNavigation(key, "$origin/manga/fixture", true))
            assertFalse(allowsVerificationNavigation(key, "https://evil.example/", true))
        }
    }
    @Test fun ccTextSearchAlphabetizesOnlyItsFetchedPage() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(ccCard("zebra") + ccCard("alpha")))
            val source = ManhwaSource("manhwa18cc", server.url("/").toString().trimEnd('/'), MangaDex.client())
            val page = source.browse("fixture", 2, BrowseFilter(sort = "Title (this page)"))
            assertEquals(listOf("Fixture alpha", "Fixture zebra"), page.titles.map { it.title })
            assertEquals(listOf("/webtoon/zebra", "/webtoon/alpha"), page.paginationIds.map(::sourcePath))
        }
    }

}
