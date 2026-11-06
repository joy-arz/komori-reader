package app.komori.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class FilteredDiscoverTest {
    @Test fun zapAuthorLanguageFilterRetainsFailedSupplementAndVerifiesItsOwnerOnRetry() = runBlocking {
        fun card(id: Int) = """<article class='hz-gallery-card'><a class='hz-gallery-card__cover' href='/gallery/$id/'><img src='/cover.jpg'></a><h2 class='hz-gallery-card__title'><a href='/gallery/$id/'>Fixture $id</a></h2></article>"""
        fun metadata(owner: String) = "<section class='hz-gallery-metadata'><a href='/artist/$owner/'>$owner</a><a href='/language/english/'>English</a></section>"
        MockWebServer().use { server ->
            var failed = false
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                    "/artist/creator/" -> MockResponse().setBody("<section class='hz-gallery-results'>${card(1)}</section>")
                    "/search/" -> MockResponse().setBody("<section class='hz-gallery-results'>${card(2)}</section><a rel='next' href='?page=2'>Next</a>")
                    "/gallery/1/" -> MockResponse().setBody(metadata("creator"))
                    "/gallery/2/" -> if (!failed) { failed = true; MockResponse().setResponseCode(404) } else MockResponse().setBody(metadata("other"))
                    else -> error("Unexpected request")
                }
            }
            val source = WebSource("zap", server.url("/").toString().trimEnd('/'), MangaDex.client())
            val creator = Creator(sourceId("zap", "/artist/creator/"), "Creator", "artist")
            val filter = BrowseFilter(language = "en")
            val page = source.browse("", 1, filter, creator)
            assertEquals(listOf("Fixture 1"), page.titles.map { it.title })
            assertEquals(listOf("Fixture 2"), page.incompleteMetadata.map { it.title })
            assertTrue(page.hasMore)
            assertEquals(4, server.requestCount)
            val retried = source.refineMetadata(BrowsePage(page.incompleteMetadata, false), filter, creator.id)
            assertTrue("A matching language does not prove the requested author", retried.titles.isEmpty())
            assertTrue(retried.incompleteMetadata.isEmpty())
            assertEquals(5, server.requestCount)
        }
    }

    @Test fun demonicGenreSearchKeepsGoodTitleAndRetriesOnlyFailedMetadata() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                var retry = false
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path!!.startsWith("/search.php") -> MockResponse().setBody("""<a href='/manga/good'><img alt='Good'></a><a href='/manga/bad'><img alt='Bad'></a>""")
                    request.path == "/advanced.php" -> MockResponse().setBody("""<input name='genres[]' value='1'>Action""")
                    request.path == "/manga/bad" && !retry -> { retry = true; MockResponse().setResponseCode(404) }
                    else -> MockResponse().setBody("""<h1>Fixture</h1><ul class='genres-list'><li>Action</li></ul>""")
                }
            }
            val source = WebSource("demonic", server.url("/").toString().trimEnd('/'), MangaDex.client())
            // The taxonomy label is text on the input's parent.
            val filter = BrowseFilter(tags = setOf("1"))
            val page = source.browse("fixture", 1, filter)
            assertEquals(listOf("Good"), page.titles.map { it.title })
            assertEquals(listOf("Bad"), page.incompleteMetadata.map { it.title })
            assertEquals(4, server.requestCount)
            val retried = source.refineMetadata(BrowsePage(page.incompleteMetadata, false), filter)
            assertEquals(listOf("Bad"), retried.titles.map { it.title })
            assertTrue(retried.incompleteMetadata.isEmpty())
            assertEquals(5, server.requestCount)
        }
    }
}
