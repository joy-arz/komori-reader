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

    @Test
    fun sourceSortsAreExplicitForLatestAndPopularity() {
        assertEquals("Latest updates", AuthorSort.LATEST.sourceSort("mangadex"))
        for (key in listOf("zap", "nhentai", "imhentai")) assertEquals(
            "Latest",
            AuthorSort.LATEST.sourceSort(key),
        )
        assertEquals("Popularity", AuthorSort.POPULAR.sourceSort("mangadex"))
        assertEquals("Popular all time", AuthorSort.POPULAR.sourceSort("nhentai"))
        assertEquals("Popular", AuthorSort.POPULAR.sourceSort("zap"))
        assertEquals("Popular", AuthorSort.POPULAR.sourceSort("imhentai"))
    }

    @Test
    fun blendsSourceRanksInsteadOfAppendingOneCreatorFirst() = runBlocking {
        val catalog =
            AuthorCatalog(creators.take(2)) { creator, page ->
                BrowsePage(
                    listOf(
                        title("${creator.id}-${page}-first"),
                        title("${creator.id}-${page}-second"),
                    ),
                    page < 2,
                )
            }
        catalog.loadNext()
        assertEquals(
            listOf("1-1-first", "2-1-first", "1-1-second", "2-1-second"),
            catalog.items.map { it.id },
        )
        catalog.loadNext()
        assertEquals(
            listOf("1-2-first", "2-2-first", "1-2-second", "2-2-second"),
            catalog.items.takeLast(4).map { it.id },
        )
    }

    @Test
    fun alphabeticalAndAuthorOrderUseEveryLoadedWorkAndDeduplicateOwners() = runBlocking {
        for (sort in listOf(AuthorSort.ALPHABETICAL, AuthorSort.AUTHOR)) {
            val catalog =
                AuthorCatalog(creators.take(2).reversed(), sort) { creator, _ ->
                    BrowsePage(
                        if (creator.id == "1") listOf(title("zebra"), title("shared"))
                        else listOf(title("apple"), title("shared")),
                        false,
                    )
                }
            catalog.loadNext()
            assertEquals(
                if (sort == AuthorSort.ALPHABETICAL) listOf("apple", "shared", "zebra")
                else listOf("shared", "zebra", "apple"),
                catalog.items.map { it.id },
            )
            assertFalse(catalog.hasMore)
        }
    }

    @Test
    fun localReorderingPreservesLoadedPagesWithoutRefetching() = runBlocking {
        var calls = 0
        val catalog =
            AuthorCatalog(creators.take(1)) { _, page ->
                calls++
                BrowsePage(listOf(title(if (page == 1) "zebra" else "apple")), page < 2)
            }
        catalog.loadNext()
        catalog.loadNext()
        catalog.reorder(AuthorSort.ALPHABETICAL)
        assertEquals(listOf("apple", "zebra"), catalog.items.map { it.id })
        catalog.reorder(AuthorSort.LATEST)
        assertEquals(listOf("zebra", "apple"), catalog.items.map { it.id })
        assertEquals(2, calls)
        assertFalse(catalog.hasMore)
        try {
            catalog.reorder(AuthorSort.POPULAR)
            fail("A different source ranking needs new pages")
        } catch (_: IllegalArgumentException) {}
    }
}
