package app.komori.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class SourceFacetsTest {
    @Test fun metadataGroupsAreStoredWithoutSidebarEntitiesOrCounts() {
        for (key in gallerySources) {
            val scope = when (key) {
                "zap" -> "class='hz-gallery-metadata'"
                "nhentai" -> "id='tags'"
                else -> "class='right_details'"
            }
            val document = Jsoup.parse("""
                <section $scope>
                  <a href='/parody/space-saga/'><span class='hz-gallery-tag__name'>Space Saga</span><span class='hz-gallery-tag__count'>19K</span></a>
                  <a href='/parody/space-saga/'>Duplicate</a>
                  <a href='/language/english/'><span class='name'>English</span><span class='count'>100</span></a>
                  <a href='/category/manga/'>Manga</a>
                  <a href='/tag/colour/'>Colour</a>
                  <a href='/artist/example/'>Example artist</a>
                  <a href='https://other.example/parody/injected/'>Injected</a>
                </section>
                <section class='hz-home-ranking'><a href='/tag/sidebar/'>Unrelated ranking</a></section>
            """, "https://example.com/gallery/1/")
            val source = WebSource(key, "https://example.com", MangaDex.client())
            val manga = source.parseDetails(Manga(sourceId(key, "/gallery/1/"), "Fixture", "", ""), document)
            assertEquals(listOf("parody", "language", "category", "tag"), manga.metadata().map { it.type })
            assertEquals(listOf("Space Saga", "English", "Manga", "Colour"), manga.metadata().map { it.name })
            assertEquals(1, JSONArray(manga.creatorsJson).length())
        }
    }

    @Test fun storedLegacyTagsRemainReadableWithoutAMigration() {
        val manga = Manga("legacy", "Fixture", "", "", tagsJson = """[{"id":"/tag/colour/","name":"Colour"}]""")
        assertEquals(listOf(SourceTag("/tag/colour/", "Colour")), manga.metadata())
    }

    @Test fun galleryTitleFiltersSendOnlySelectedLanguageAndCategoryOnEveryPage() = runBlocking {
        for (key in listOf("zap", "imhentai")) MockWebServer().use { server ->
            val source = WebSource(key, server.url("/").toString().trimEnd('/'), MangaDex.client())
            for (page in listOf(1, 3)) {
                server.enqueue(MockResponse().setBody(""))
                source.browse("forest", page, BrowseFilter("Popular", language = "en", category = "m"))
                val url = server.takeRequest().requestUrl!!
                assertEquals("/search/", url.encodedPath)
                assertEquals("forest", url.queryParameter("key"))
                assertEquals(page.toString(), url.queryParameter("page"))
                assertEquals("1", url.queryParameter("pp"))
                assertEquals("1", url.queryParameter("m"))
                assertEquals("1", url.queryParameter("en"))
                assertEquals(if (key == "zap") null else "0", url.queryParameter("d"))
                assertEquals(if (key == "zap") null else "0", url.queryParameter("jp"))
            }
        }
    }

    @Test fun languageOnlyFilterDoesNotFallThroughToUnfilteredHome() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(""))
            WebSource("zap", server.url("/").toString().trimEnd('/'), MangaDex.client())
                .browse("", 2, BrowseFilter(language = "jp"))
            val url = server.takeRequest().requestUrl!!
            assertEquals("/search/", url.encodedPath)
            assertEquals("yes", url.queryParameter("filter"))
            assertEquals("1", url.queryParameter("jp"))
            assertNull(url.queryParameter("en"))
            assertEquals("1", url.queryParameter("m"))
            assertEquals("1", url.queryParameter("d"))
        }
    }

    @Test fun nhentaiCombinesParodyLanguageCategoryTagsAndPopularity() = runBlocking {
        MockWebServer().use { server ->
            val source = WebSource("nhentai", server.url("/").toString().trimEnd('/'), MangaDex.client())
            server.enqueue(MockResponse().setBody(""))
            source.browse("Space Saga", 4, BrowseFilter("Popular this month", setOf("/tag/colour/"),
                searchType = "Parody", language = "english", category = "manga"))
            val url = server.takeRequest().requestUrl!!
            assertEquals("/search/", url.encodedPath)
            assertEquals("parody:\"space saga\" tag:\"colour\" language:\"english\" category:\"manga\"", url.queryParameter("q"))
            assertEquals("popular-month", url.queryParameter("sort"))
            assertEquals("4", url.queryParameter("page"))
        }
    }

    @Test fun nhentaiCreatorFilteringKeepsTheCreatorWhenLanguageIsSelected() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(""))
            WebSource("nhentai", server.url("/").toString().trimEnd('/'), MangaDex.client())
                .browse("", 2, BrowseFilter(language = "japanese"),
                    Creator(sourceId("nhentai", "/artist/example/"), "Example", "artist"))
            assertEquals("artist:\"example\" language:\"japanese\"", server.takeRequest().requestUrl!!.queryParameter("q"))
        }
    }

    @Test fun metadataSearchUsesCanonicalPathsAndZapPopularityRoute() = runBlocking {
        for ((type, value, path) in listOf(
            Triple("Parody", "Space Saga", "/parody/space-saga/"),
            Triple("Language", "English", "/language/english/"),
            Triple("Category", "Manga", "/category/manga/"),
        )) MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(""))
            WebSource("zap", server.url("/").toString().trimEnd('/'), MangaDex.client())
                .browse(value, 2, BrowseFilter("Popular", searchType = type))
            val url = server.takeRequest().requestUrl!!
            assertEquals(path + "popular/", url.encodedPath)
            assertEquals("2", url.queryParameter("page"))
        }
    }

    @Test fun unsupportedMetadataCombinationsFailBeforeSendingARequest() = runBlocking {
        MockWebServer().use { server ->
            val source = WebSource("zap", server.url("/").toString().trimEnd('/'), MangaDex.client())
            for (filter in listOf(
                BrowseFilter(facetPath = "/parody/space-saga/", language = "en"),
                BrowseFilter(language = "not-a-language"),
                BrowseFilter(category = "not-a-category"),
            )) {
                try {
                    source.browse("", 1, filter)
                    fail("Invalid filters must not be silently ignored")
                } catch (_: IllegalArgumentException) {}
            }
            assertEquals(0, server.requestCount)
        }
    }
}
