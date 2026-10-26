package app.komori.domain

import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ReaderPrefetchTest {
    private fun chapter(id: String, pages: Int) = Release(id, "book", id, null, "", "en", emptyList(), "", pages)

    @Test fun lookaheadIsBoundedAndMovesWithTheVisiblePage() = runBlocking {
        val pages = mutableListOf<Int>()
        val release = chapter("one", 40)
        prefetchReaderPages(release, 0, 50, { null }, { it }) { _, page -> pages += page }
        assertEquals((1..10).toList(), pages)
        pages.clear()
        prefetchReaderPages(release, 6, 10, { null }, { it }) { _, page -> pages += page }
        assertEquals((7..16).toList(), pages)
    }

    @Test fun continuousLookaheadSharesOneBudgetAcrossChapters() = runBlocking {
        val first = chapter("one", 3)
        val second = chapter("two", 20)
        val calls = mutableListOf<Pair<String, Int>>()
        prefetchReaderPages(first, 1, 10, { if (it.id == first.id) second else null }, { it }) { release, page -> calls += release.id to page }
        assertEquals(listOf("one" to 2) + (0..8).map { "two" to it }, calls)
    }

    @Test fun disabledContinuityStopsAtTheChapterBoundary() = runBlocking {
        val pages = mutableListOf<Int>()
        prefetchReaderPages(chapter("one", 3), 1, 10, { null }, { error("Must not resolve another chapter") }) { _, page -> pages += page }
        assertEquals(listOf(2), pages)
    }

    @Test fun changingPositionCancelsAnInflightLookahead() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val calls = mutableListOf<Int>()
        val job = launch {
            prefetchReaderPages(chapter("one", 30), 0, 10, { null }, { it }) { _, page ->
                calls += page
                started.complete(Unit)
                awaitCancellation()
            }
        }
        withTimeout(2000) { started.await() }
        job.cancelAndJoin()
        assertEquals(listOf(1), calls)
    }

    @Test fun failedSpeculationDoesNotFetchTheRestOfTheWindow() = runBlocking {
        var calls = 0
        try {
            prefetchReaderPages(chapter("one", 30), 0, 10, { null }, { it }) { _, _ -> calls++; throw IOException("Offline") }
            fail("Expected transport failure")
        } catch (_: IOException) { assertEquals(1, calls) }
    }
}
