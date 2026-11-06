package app.komori.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class FilteredDiscoverTest {
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
