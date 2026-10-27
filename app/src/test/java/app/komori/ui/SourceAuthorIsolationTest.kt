package app.komori.ui

import app.komori.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class SourceAuthorIsolationTest {
    private fun card(id: Int) = """<article class='hz-gallery-card'><a class='hz-gallery-card__cover' href='/gallery/$id/'><img src='/cover.jpg'></a><h2 class='hz-gallery-card__title'><a href='/gallery/$id/'>Fixture $id</a></h2></article>"""
    private fun listing(vararg ids: Int, next: Boolean = false) = "<section class='hz-gallery-results'>${ids.joinToString("") { card(it) }}</section>" + if (next) "<a rel='next' href='?page=2'>Next</a>" else ""
    private fun metadata(artist: String) = "<section class='hz-gallery-metadata'><a href='/artist/$artist/'>$artist</a><a href='/language/english/'>English</a></section>"

    @Test fun realZapOwnershipAndLanguagePathsAdvancePastOne404AndRetryOnlyTheIncompleteTitle() = runBlocking {
        MockWebServer().use { server ->
            var fail = true
            var failures = 0
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    val page = request.requestUrl!!.queryParameter("page") ?: "1"
                    return when {
                        path.startsWith("/artist/") -> MockResponse().setBody(if (page == "1") listing(1, next = true) else listing(4))
                        path == "/search/" -> MockResponse().setBody(if (page == "1") listing(2, 3, next = true) else listing(2))
                        path == "/gallery/2/" -> { failures++; if (fail) MockResponse().setResponseCode(404) else MockResponse().setBody(metadata("creator")) }
                        path == "/gallery/3/" -> MockResponse().setBody(metadata("different"))
                        else -> MockResponse().setBody(metadata("creator"))
                    }
                }
            }
            val source = WebSource("zap", server.url("/").toString().trimEnd('/'), MangaDex.client())
            val creator = Creator(sourceId("zap", "/artist/creator/"), "Creator", "artist")
            val filter = AuthorWorkFilter(language = "en")
            val catalog = AuthorCatalog(listOf(creator), filter = filter, enrich = { source.details(it) }) { owner, page ->
                val native = authorBrowseFilter("zap", AuthorSort.LATEST, filter)
                assertEquals("", native.language)
                source.browse("", page, native, owner, deferCreatorDetails = true)
            }
            assertTrue(catalog.loadNext().isEmpty())
            assertEquals(listOf("/gallery/1/"), catalog.items.map { sourcePath(it.id) })
            assertEquals(1, catalog.incompleteMetadataCount); assertTrue(catalog.hasMore)
            assertTrue(catalog.loadNext().isEmpty()); assertFalse(catalog.hasMore)
            assertEquals(setOf("/gallery/1/", "/gallery/4/"), catalog.items.map { sourcePath(it.id) }.toSet())
            assertEquals("Persistent detail error must not be automatically retried on a later page", 1, failures)
            fail = false; catalog.retryMetadata(); catalog.retryMetadata()
            assertEquals(2, failures); assertEquals(0, catalog.incompleteMetadataCount)
            assertEquals(setOf("/gallery/1/", "/gallery/2/", "/gallery/4/"), catalog.items.map { sourcePath(it.id) }.toSet())
            assertEquals(3, catalog.items.size)
        }
    }
    @Test fun unknownOwnershipNeverMatchesAndRetryCannotAssignAnotherCreatorsTitle() = runBlocking {
        val owner = Creator("zap:owner", "Owner", "artist")
        val candidate = Manga("zap:candidate", "Name-only match", "", "")
        var metadata = candidate
        val catalog = AuthorCatalog(listOf(owner), enrich = { metadata }) { _, _ ->
            BrowsePage(emptyList(), false, listOf(candidate.id), listOf(candidate))
        }
        catalog.loadNext(); assertTrue(catalog.items.isEmpty()); assertEquals(1, catalog.incompleteMetadataCount)
        catalog.retryMetadata(); assertTrue(catalog.items.isEmpty()); assertEquals(1, catalog.incompleteMetadataCount)
        metadata = candidate.copy(creatorsJson = """[{"id":"different","name":"Different","role":"artist"}]""")
        catalog.retryMetadata(); assertTrue(catalog.items.isEmpty()); assertEquals(0, catalog.incompleteMetadataCount)
    }
}
