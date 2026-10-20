package app.komori.ui

import app.komori.data.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AuthorCatalogTest {
    private fun title(id: String) = Manga(id, id, "", "")

    private val creators = (1..4).map { Creator("$it", "Creator $it", "author") }

    @Test
    fun combinesUnsavedWorksAndRotatesWithBoundedRequests() = runBlocking {
        val calls = mutableListOf<Pair<String, Int>>()
        val catalog =
            AuthorCatalog(creators) { creator, page ->
                calls += creator.id to page
                BrowsePage(listOf(title("shared"), title("${creator.id}-$page")), page < 2)
            }
        assertTrue(catalog.loadNext().isEmpty())
        assertEquals(listOf("1", "2", "3"), calls.map { it.first })
        assertEquals(4, catalog.items.size)
        catalog.loadNext()
        assertEquals("4" to 1, calls[3])
        while (catalog.hasMore) catalog.loadNext()
        assertEquals(9, catalog.items.size)
    }

    @Test
    fun failedCreatorCanRetryWithoutBlockingOtherCreators() = runBlocking {
        var fail = true
        val catalog =
            AuthorCatalog(creators.take(2)) { creator, _ ->
                if (creator.id == "1" && fail) throw IOException("Connection reset")
                BrowsePage(listOf(title(creator.id)), false)
            }
        assertEquals(1, catalog.loadNext().size)
        assertEquals(listOf("2"), catalog.items.map { it.id })
        fail = false
        assertTrue(catalog.loadNext().isEmpty())
        assertEquals(setOf("1", "2"), catalog.items.map { it.id }.toSet())
        assertFalse(catalog.hasMore)
    }

    @Test
    fun repeatedPagesStopAndSelectedCreatorStaysScoped() = runBlocking {
        val catalog =
            AuthorCatalog(creators.take(1)) { creator, _ ->
                assertEquals("1", creator.id)
                BrowsePage(listOf(title("one")), true)
            }
        catalog.loadNext()
        assertTrue(catalog.hasMore)
        catalog.loadNext()
        assertFalse(catalog.hasMore)
        assertEquals(1, catalog.items.size)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotShownAsSourceFailure() {
        runBlocking { AuthorCatalog(creators) { _, _ -> throw CancellationException() }.loadNext() }
    }
}
