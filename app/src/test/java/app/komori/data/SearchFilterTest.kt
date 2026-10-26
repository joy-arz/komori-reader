package app.komori.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class SearchFilterTest {
    @Test fun mangaDexFiltersReachTheApiAndSurviveSavedSearches() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[]}"""))
            val filter = BrowseFilter("Title", setOf("tag-id"), language = "id", category = "ko", status = "completed", ratings = setOf("safe", "erotica"))
            MangaDex(MangaDex.client(), server.url("/").toString().trimEnd('/')).browse("fixture", 30, null, filter)
            val url = server.takeRequest().requestUrl!!
            assertEquals("id", url.queryParameter("availableTranslatedLanguage[]"))
            assertEquals("ko", url.queryParameter("originalLanguage[]"))
            assertEquals("completed", url.queryParameter("status[]"))
            assertEquals(setOf("safe", "erotica"), url.queryParameterValues("contentRating[]").toSet())
            assertEquals("30", url.queryParameter("offset"))
            val saved = NavigationState(source = "mangadex", query = "fixture", filter = filter)
            assertEquals(saved, NavigationState.decode(saved.encode()))
            assertEquals(setOf("safe", "suggestive"), NavigationState.decode("{}")!!.filter.ratings)
        }
    }

    @Test fun zapMetadataLanguageRefinementRetainsUnfilteredPageIdentity() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""<section class='hz-gallery-results'><article class='hz-gallery-card'><a class='hz-gallery-card__cover'><img src='https://cdn.example/1.jpg'></a><h2 class='hz-gallery-card__title'><a href='/gallery/1/'>Fixture</a></h2></article></section><a rel='next'>Next</a>"""))
            server.enqueue(MockResponse().setBody("""<section class='hz-gallery-metadata'><a href='/language/japanese/'>Japanese</a><a href='/category/manga/'>Manga</a></section>"""))
            val source = WebSource("zap", server.url("/").toString().trimEnd('/'), MangaDex.client())
            val page = source.browse("", 1, BrowseFilter(facetPath = "/parody/fixture/", language = "en", category = "m"))
            assertTrue(page.titles.isEmpty())
            assertEquals(1, page.paginationIds.size)
            assertTrue(page.hasMore)
            assertEquals("/parody/fixture/", server.takeRequest().requestUrl!!.encodedPath)
            assertEquals("/gallery/1/", server.takeRequest().requestUrl!!.encodedPath)
        }
    }
    @Test fun demonicTitleSearchCanBeRefinedByGenre() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<a href='/manga/fixture' title='Fixture'><img src='https://cdn.example/cover.jpg'></a>"))
            server.enqueue(MockResponse().setBody("<label><input name='genres[]' value='59'>Action</label>"))
            server.enqueue(MockResponse().setBody("<div class='genres-list'><li>Action</li><li>Fantasy</li></div><div class='white-font'>Description</div>"))
            val source = WebSource("demonic", server.url("/").toString().trimEnd('/'), MangaDex.client())
            val page = source.browse("fixture", 1, BrowseFilter(tags = setOf("59")))
            assertEquals("Fixture", page.titles.single().title)
            assertEquals(listOf("Action", "Fantasy"), page.titles.single().metadata().map { it.name })
            assertEquals("/search.php", server.takeRequest().requestUrl!!.encodedPath)
            assertEquals("/advanced.php", server.takeRequest().requestUrl!!.encodedPath)
            assertEquals("/manga/fixture", server.takeRequest().requestUrl!!.encodedPath)
        }
    }

    @Test fun mangaDexFallsBackToAvailableImageQuality() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"baseUrl":"https://cdn.example","chapter":{"hash":"fixture","dataSaver":[],"data":["1.jpg"]}}"""))
            val pages = MangaDex(MangaDex.client(), server.url("/").toString().trimEnd('/')).pages("chapter", true)
            assertEquals(listOf("https://cdn.example/data/fixture/1.jpg"), pages)
        }
    }

    @Test fun mangaDexAccessFailuresDoNotSuggestAnUnsupportedBrowserSession() {
        val error = SourceHttpException(403, "mangadex")
        assertFalse(requiresSourceAction(error))
        assertFalse(automaticRetryAllowed(error))
        assertFalse(error.message.orEmpty().contains("Verify source"))
        assertTrue(requiresSourceAction(SourceHttpException(403, "zap")))
    }

    @Test fun demonicDetailGenreNamesResolveToNativeNumericFilters() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<label><input name='genres[]' value='1'>Action</label>"))
            server.enqueue(MockResponse().setBody(""))
            WebSource("demonic", server.url("/").toString().trimEnd('/'), MangaDex.client())
                .browse("", 1, BrowseFilter(tags = setOf("Action")))
            assertEquals("/advanced.php", server.takeRequest().requestUrl!!.encodedPath)
            val search = server.takeRequest()
            assertEquals("POST", search.method)
            assertTrue(search.body.readUtf8().contains("genres%5B%5D=1"))
        }
    }

    @Test fun textSearchSortScopeRemainsExplicitWhenTheQueryChanges() {
        for (source in listOf("demonic", "manhwa18cc")) {
            assertEquals("Title (this page)", contextualSearchSort(source, "fixture", "Title"))
            assertEquals("Title", contextualSearchSort(source, "", "Title (this page)"))
            assertEquals("Source order", contextualSearchSort(source, "fixture", "Popularity"))
            assertEquals("", contextualSearchSort(source, "", "Source order"))
        }
        assertEquals("Popularity", contextualSearchSort("mangadex", "fixture", "Popularity"))
    }

}
