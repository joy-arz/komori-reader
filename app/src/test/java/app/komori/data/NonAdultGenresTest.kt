package app.komori.data

import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class NonAdultGenresTest {
    @Test fun weebUsesNativeTaxonomyCachesItAndCombinesGenreTypeStatusAndSearch() = runBlocking {
        MockWebServer().use { server ->
            val source = WebSource("weeb", server.url("/").toString().trimEnd('/'), MangaDex.client())
            server.enqueue(MockResponse().setBody("""<input id='tag-Psychological-value' value='Psychological'>
                <input id='tag-Shounen-value' value='Shounen'><input id='tag-Other-value' value='Other'>
                <input id='tag-Shounen-value' value='Shounen'>"""))
            assertEquals(listOf("Psychological", "Shounen", "Other"), source.tags().map { it.name })
            assertEquals(source.tags(), source.tags())
            assertEquals(1, server.requestCount)
            assertEquals("/search", server.takeRequest().requestUrl!!.encodedPath)
            server.enqueue(MockResponse().setBody("<html></html>"))
            source.browse("school", 1, BrowseFilter(tags = setOf("Psychological", "Shounen"), category = "Manga", status = "completed"))
            val request = server.takeRequest().requestUrl!!
            assertEquals("school", request.queryParameter("text"))
            assertEquals(listOf("Psychological", "Shounen"), request.queryParameterValues("included_tag"))
            assertEquals("Manga", request.queryParameter("included_type"))
            assertEquals("Complete", request.queryParameter("included_status"))
            assertEquals("False", request.queryParameter("adult"))
        }
    }

    @Test fun detailsExposeRealClickableGenresAndCategoryWithoutSidebarOrForeignLinks() {
        val source = WebSource("weeb", "https://weebcentral.com", MangaDex.client())
        val manga = Manga(sourceId("weeb", "/series/fixture"), "Fixture", "", "", tagsJson = """[{"id":"stale","name":"Old tag"}]""")
        val details = source.parseDetails(manga, Jsoup.parse("""<ul>
            <li><strong>Tags(s):</strong><a href='/search?included_tag=Psychological'>Psychological</a>
                <a href='/search?included_tag=Shounen'>Shounen</a><a href='https://other.example/search?included_tag=wrong'>Wrong</a></li>
            <li><strong>Type:</strong><a href='/search?included_type=Manga'>Manga</a></li>
            <li><strong>Status:</strong><a href='/search?included_status=Ongoing'>Ongoing</a></li>
            <li><strong>Description</strong><p>Full synopsis.</p></li></ul>
            <aside><a href='/search?included_tag=Sidebar'>Sidebar</a></aside>""", "https://weebcentral.com"))
        assertEquals(listOf("Psychological", "Shounen"), details.metadata().filter { it.type == "tag" }.map { it.id })
        assertEquals("Manga", details.metadata().single { it.type == "category" }.id)
        assertEquals("ongoing", details.status)
        assertEquals("Full synopsis.", details.description)
        assertEquals("en", details.metadata().single { it.type == "language" }.id)
    }

    @Test fun demonicGenreNamesRemainClickableAndDoNotRetainRemovedTags() {
        val source = WebSource("demonic", "https://demonicscans.org", MangaDex.client())
        val book = Manga(sourceId("demonic", "/manga/fixture"), "Fixture", "", "", tagsJson = """[{"id":"old","name":"Old"}]""")
        val result = source.parseDetails(book, Jsoup.parse("""<ul class='genres-list'><li>Action</li><li>Fantasy</li></ul>"""))
        assertEquals(listOf("Action", "Fantasy"), result.metadata().filter { it.type == "tag" }.map { it.name })
    }

    @Test fun hiveRequestsCanonicalPathsWithoutDowngradingRedirectsAndKeepsQueries() = runBlocking {
        MockWebServer().use { server ->
            val source = ScanlationSource("hive", server.url("/").toString().trimEnd('/'), MangaDex.client().newBuilder().followSslRedirects(false).build())
            server.enqueue(MockResponse().setBody("<html></html>"))
            source.browse("one & two", 2, BrowseFilter(tags = setOf("+5"), status = "ongoing"))
            val catalog = server.takeRequest().requestUrl!!
            assertEquals("/series/", catalog.encodedPath)
            assertEquals("one & two", catalog.queryParameter("searchTerm"))
            assertEquals("+5", catalog.queryParameter("genres"))
            assertEquals("ONGOING", catalog.queryParameter("seriesStatus"))
            server.enqueue(MockResponse().setBody("<h1>Fixture</h1>"))
            source.details(Manga(sourceId("hive", "/series/fixture"), "", "", ""))
            assertEquals("/series/fixture/", server.takeRequest().requestUrl!!.encodedPath)
            server.enqueue(MockResponse().setBody("<img data-reader-page-image='true' src='https://images.example/1.webp'>"))
            source.pages(sourceId("hive", "/series/fixture/chapter-1"))
            assertEquals("/series/fixture/chapter-1/", server.takeRequest().requestUrl!!.encodedPath)
        }
    }

    @Test fun capturedNativeGenreAndHivePagesParseWithoutImageDownloads() {
        val root = System.getenv("KOMORI_GENRE_CAPTURE")
        assumeTrue("Optional native HTML capture", root != null)
        fun doc(name: String, base: String) = Jsoup.parse(File(root, "komori-$name.html"), "UTF-8", base)
        val weeb = WebSource("weeb", "https://weebcentral.com", MangaDex.client())
        assertEquals(38, weeb.parseSourceTags(doc("weeb-search", "https://weebcentral.com")).size)
        val details = weeb.parseDetails(Manga(sourceId("weeb", "/series/fixture"), "", "", ""), doc("weeb-real-detail", "https://weebcentral.com"))
        assertEquals(7, details.metadata().count { it.type == "tag" })
        assertEquals("Manga", details.metadata().single { it.type == "category" }.id)
        val hive = ScanlationSource("hive", "https://hivetoons.org", MangaDex.client())
        assertTrue(hive.parseBrowse(doc("hive-series", "https://hivetoons.org/series/"), 1).hasMore)
        assertTrue(hive.parseTags(doc("hive-series", "https://hivetoons.org/series/")).isNotEmpty())
        val id = sourceId("hive", "/series/crocodile")
        val h = doc("hive-detail", "https://hivetoons.org/series/crocodile/")
        assertTrue(hive.parseDetails(h, Manga(id, "", "", "")).metadata().any { it.type == "tag" })
        assertEquals(28, hive.parseChapters(h, id).size)
        assertEquals(40, hive.parsePages(doc("hive-chapter", "https://hivetoons.org/series/crocodile/chapter-1/")).size)
    }
}
