package app.komori.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class CreatorCompletenessTest {
    private fun card(id: Int) = """<article class='hz-gallery-card'>
        <a class='hz-gallery-card__cover' href='/gallery/$id/'><img src='/cover.jpg'></a>
        <h2 class='hz-gallery-card__title'><a href='/gallery/$id/'>Fixture $id</a></h2></article>"""
    private fun listing(vararg ids: Int, next: Boolean = false) =
        "<section class='hz-gallery-results'>${ids.joinToString("") { card(it) }}</section>" + if (next) "<a rel='next' href='?page=2'>Next</a>" else ""
    private fun metadata(artist: String) = "<section class='hz-gallery-metadata'><a href='/artist/$artist/'>$artist</a></section>"

    @Test fun supplementsIncompleteArtistIndexButRejectsNameOnlyMatchesAndCachesVerification() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setBody(when (request.requestUrl!!.encodedPath) {
                    "/artist/creator/", "/artist/creator/popular/" -> listing(1)
                    "/search/" -> listing(2, 3)
                    "/gallery/2/" -> metadata("creator")
                    "/gallery/3/" -> metadata("other-creator")
                    else -> error("Unexpected request")
                })
            }
            val source = WebSource("zap", server.url("/").toString().trimEnd('/'), MangaDex.client())
            val creator = Creator(sourceId("zap", "/artist/creator/"), "Creator", "artist")
            val latest = source.browse("", 1, BrowseFilter("Latest"), creator)
            assertEquals(listOf("/gallery/2/", "/gallery/1/"), latest.titles.map { sourcePath(it.id) })
            assertEquals(3, latest.paginationIds.size)
            assertFalse(latest.hasMore)
            source.browse("", 1, BrowseFilter("Popular"), creator)
            assertEquals(6, server.requestCount)
        }
    }

    @Test fun filteredEmptyPageRetainsRawContinuation() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(listing()))
            server.enqueue(MockResponse().setBody(listing(3, next = true)))
            server.enqueue(MockResponse().setBody(metadata("different")))
            val source = WebSource("zap", server.url("/").toString().trimEnd('/'), MangaDex.client())
            val page = source.browse("Creator", 1, BrowseFilter(searchType = "Author"))
            assertTrue(page.titles.isEmpty())
            assertTrue(page.hasMore)
            assertEquals(listOf(sourceId("zap", "/gallery/3/")), page.paginationIds)
        }
    }
    @Test fun liveKanamirubonCatalogIncludesReportedMissingGallery() = runBlocking {
        org.junit.Assume.assumeTrue(System.getenv("KOMORI_LIVE_SOURCE_AUDIT") == "1")
        val source = WebSource("zap", "https://hentaizap.com", MangaDex.client())
        val creator = Creator(sourceId("zap", "/artist/kanamirubon/"), "kanamirubon", "artist")
        val page = source.browse("", 1, BrowseFilter("Latest"), creator)
        assertTrue("Reported work must be verified and included", page.titles.any { sourcePath(it.id) == "/gallery/1665352/" })
        assertTrue("Native index must be supplemented", page.titles.size > 1)
        println("Verified creator catalog works: ${page.titles.size}; raw candidates: ${page.paginationIds.size}")
    }

    @Test fun missingNativeArtistPageDoesNotBlockVerifiedSearchContinuation() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setBody(listing(22)))
            server.enqueue(MockResponse().setBody(metadata("creator")))
            val source = WebSource("zap", server.url("/").toString().trimEnd('/'), MangaDex.client())
            val page = source.browse("", 2, BrowseFilter("Latest"), Creator(sourceId("zap", "/artist/creator/"), "Creator", "artist"))
            assertEquals(listOf(sourceId("zap", "/gallery/22/")), page.titles.map { it.id })
        }
    }

    @Test fun deniedNativeArtistPageStillRequiresSourceAction() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403))
            val source = WebSource("zap", server.url("/").toString().trimEnd('/'), MangaDex.client())
            try {
                source.browse("", 2, BrowseFilter("Latest"), Creator(sourceId("zap", "/artist/creator/"), "Creator", "artist"))
                fail("Access denial must remain visible")
            } catch (failure: SourceHttpException) { assertEquals(403, failure.status) }
            assertEquals(1, server.requestCount)
        }
    }

}
