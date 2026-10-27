package app.komori.ui

import app.komori.data.*
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AuthorMetadataTest {
    private val creators = listOf(Creator("zap:a", "A", "artist"), Creator("zap:b", "B", "artist"))
    private fun title(id: String) = Manga("zap:$id", id, "", "")
    @Test fun persistentDetailFailureDoesNotBlockPagingOrAnotherCreatorAndDoesNotAutoRetry() = runBlocking {
        val calls = mutableMapOf<String, Int>()
        val catalog = AuthorCatalog(creators, filter = AuthorWorkFilter(tags = "" , language = "en"), enrich = { book ->
            calls[book.id] = (calls[book.id] ?: 0) + 1
            if (book.id == "zap:bad") throw IOException("404")
            book.copy(tagsJson = "[{\"id\":\"fixture\",\"type\":\"language\",\"name\":\"English\"}]")
        }) { creator, page -> BrowsePage(if (creator == creators[0] && page == 1) listOf(title("good"), title("bad")) else listOf(title("${creator.name}-$page")), page < 2) }
        catalog.loadNext()
        assertEquals(1, catalog.incompleteMetadataCount)
        assertEquals(2, catalog.items.size)
        catalog.loadNext()
        assertFalse(catalog.hasMore)
        assertEquals(4, catalog.items.size)
        assertEquals(1, calls["zap:bad"])
        catalog.retryMetadata()
        assertEquals(1, catalog.incompleteMetadataCount)
        assertEquals(2, calls["zap:bad"])
    }
    @Test fun targetedRetryRecoversExactlyOnceAndKeepsCatalogErrorsSeparate() = runBlocking {
        var failDetails = true; var failPage = true
        val catalog = AuthorCatalog(creators.take(1), filter = AuthorWorkFilter(category = "Manga"), enrich = { book ->
            if (failDetails && book.id == "zap:bad") throw IOException("temporary")
            book.copy(tagsJson = "[{\"id\":\"fixture\",\"type\":\"category\",\"name\":\"Manga\"}]")
        }) { _, page ->
            if (failPage) throw IOException("page failed")
            BrowsePage(listOf(title("good"), title("bad")), page < 2)
        }
        assertEquals(1, catalog.loadNext().size)
        assertEquals(0, catalog.incompleteMetadataCount)
        assertTrue(catalog.hasMore)
        failPage = false
        assertTrue(catalog.loadNext().isEmpty())
        assertEquals(listOf("zap:good"), catalog.items.map { it.id })
        failDetails = false; catalog.retryMetadata(); catalog.retryMetadata(); catalog.loadNext()
        assertEquals(setOf("zap:good", "zap:bad"), catalog.items.map { it.id }.toSet())
        assertEquals(2, catalog.items.size)
        assertEquals(0, catalog.incompleteMetadataCount)
    }
    @Test fun unknownOrNonmatchingMetadataNeverCountsAsMatch() = runBlocking {
        val catalog = AuthorCatalog(creators.take(1), filter = AuthorWorkFilter(category = "Manga"), enrich = { if (it.id.endsWith("bad")) throw IOException() else it }) { _, _ -> BrowsePage(listOf(title("good"), title("bad")), false) }
        assertTrue(catalog.loadNext().isEmpty()); assertTrue(catalog.items.isEmpty())
        assertFalse(catalog.hasMore); assertEquals(1, catalog.incompleteMetadataCount)
    }
    @Test fun cancellationDoesNotCommitHalfEnrichedPageAndConcurrencyIsBounded() = runBlocking {
        var active = 0; var maximum = 0
        val started = CompletableDeferred<Unit>()
        val catalog = AuthorCatalog(creators.take(1), filter = AuthorWorkFilter(category = "Manga"), enrich = {
            active++; maximum = maxOf(active, maximum); started.complete(Unit)
            try { awaitCancellation() } finally { active-- }
        }) { _, _ -> BrowsePage((1..12).map { title("$it") }, false) }
        val job = launch { catalog.loadNext() }; started.await(); yield(); job.cancelAndJoin()
        assertTrue(maximum <= 2); assertEquals(0, active)
        assertTrue(catalog.items.isEmpty()); assertTrue(catalog.hasMore); assertEquals(0, catalog.incompleteMetadataCount)
    }
}
