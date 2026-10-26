package app.komori.ui

import app.komori.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AuthorWorkFilterTest {
    private val creator = Creator("zap:creator", "Example Artist", "artist")
    private val title = Manga("zap:book", "Forest letters", "", "", tagsJson = """[{"id":"/language/english/","name":"English","type":"language"},{"id":"/category/doujinshi/","name":"Doujinshi","type":"category"},{"id":"/tag/fantasy/","name":"Fantasy","type":"tag"}]""")

    @Test fun filtersMetadataAndOwnerNamesAndRoundTrips() {
        val filter = AuthorWorkFilter(setOf("zap"), "artist", "en", "doujinshi", "fantasy", "Saved", "Reading")
        assertTrue(filter.matches(title, listOf(creator)))
        assertTrue(filter.matchesLibrary(Shelf(title.id, saved = true)))
        assertFalse(filter.matchesLibrary(null))
        assertEquals(filter, AuthorWorkFilter.decode(filter.encode()))
        assertFalse(filter.copy(language = "jp").matches(title, listOf(creator)))
        assertFalse(filter.copy(tags = "fantasy, missing").matches(title, listOf(creator)))
        assertFalse(filter.copy(sources = setOf("nhentai")).matches(title, listOf(creator)))
    }

    @Test fun emptyFilteredPagesDoNotEndCreatorPagination() = runBlocking {
        val catalog = AuthorCatalog(listOf(creator), filter = AuthorWorkFilter(query = "match")) { _, page ->
            BrowsePage(listOf(title.copy(id = "zap:$page", title = if (page == 2) "A match" else "Other")), page < 2)
        }
        catalog.loadNext(); assertTrue(catalog.items.isEmpty()); assertTrue(catalog.hasMore)
        catalog.loadNext(); assertEquals(listOf("A match"), catalog.items.map { it.title }); assertFalse(catalog.hasMore)
    }
}
