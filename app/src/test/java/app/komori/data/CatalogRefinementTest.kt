package app.komori.data

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CatalogRefinementTest {
    private val rows = (1..3).map { Manga("demonic:$it", "Title $it", "", "") }
    @Test fun failuresRemainRetryableWithoutBlockingPaginationOrMatchingUnknownMetadata() = runBlocking {
        var calls = 0
        suspend fun fetch(title: Manga): Manga { calls++; if (title == rows[1]) throw IOException("404"); return title }
        val page = refineCatalog(BrowsePage(rows, true), ::fetch) { it != rows[2] }
        assertEquals(listOf(rows[0]), page.titles)
        assertEquals(listOf(rows[1]), page.incompleteMetadata)
        assertTrue(page.hasMore)
        assertEquals(rows.map { it.id }, page.paginationIds)
        assertEquals(rows.map { it.id }, page.orderingIds)
        val retry = refineCatalog(BrowsePage(page.incompleteMetadata, false), { calls++; it }) { true }
        assertEquals(listOf(rows[1]), retry.titles)
        assertTrue(retry.incompleteMetadata.isEmpty())
        assertEquals(4, calls)
        val persistent = refineCatalog(BrowsePage(page.incompleteMetadata, false), ::fetch) { true }
        assertTrue(persistent.titles.isEmpty()); assertEquals(page.incompleteMetadata, persistent.incompleteMetadata)
    }
    @Test fun predicateFailuresAndWrongOwnershipAreIncompleteAndCancellationPropagates() = runBlocking {
        val page = refineCatalog(BrowsePage(rows, false), { if (it == rows[0]) it.copy(id = "foreign") else it }) {
            if (it == rows[1]) throw IllegalArgumentException("Bad metadata"); false
        }
        assertTrue(page.titles.isEmpty()); assertEquals(rows.take(2), page.incompleteMetadata)
        val began = CompletableDeferred<Unit>()
        val work = async { refineCatalog(BrowsePage(rows, true), { began.complete(Unit); awaitCancellation() }) { true } }
        began.await(); work.cancel()
        try { work.await(); fail("Cancellation must propagate") } catch (_: CancellationException) {}
    }
}
