package app.komori.data

import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CoverRecoveryTest {
    private val book = Manga(sourceId("zap", "/gallery/7/"), "Cover fixture", "", "https://cdn.example/thumb.jpg")

    @Test fun failedThumbnailResolvesGalleryCoverAndCoalescesConcurrentLookups() = runBlocking {
        var lookups = 0
        val recovery = CoverRecovery({
            lookups++
            delay(20)
            it.copy(cover = "https://cdn.example/cover.jpg")
        })
        val covers = coroutineScope { List(12) { async { recovery.resolve(book) } }.awaitAll() }
        assertEquals(List(12) { "https://cdn.example/cover.jpg" }, covers)
        assertEquals(1, lookups)
        assertNull(recovery.resolve(book.copy(id = sourceId("nhentai", "/g/7/"))))
        assertEquals(1, lookups)
    }

    @Test fun failedLookupIsBoundedAndCanRecoverAfterCooldown() = runBlocking {
        var now = 0L
        var calls = 0
        val recovery = CoverRecovery({
            if (++calls == 1) throw IOException("Temporary gallery outage")
            it.copy(cover = "https://cdn.example/cover.jpg")
        }, { now })
        repeat(3) { assertNull(recovery.resolve(book)) }
        assertEquals(1, calls)
        now = 60_000_000_000L
        assertEquals("https://cdn.example/cover.jpg", recovery.resolve(book))
        assertEquals(2, calls)
    }

    @Test fun unchangedAndNonHttpsCoversDoNotTriggerAnotherImageRequest() = runBlocking {
        for (cover in listOf(book.cover, "", "http://cdn.example/cover.jpg", "data:image/png;base64,AA")) {
            assertNull(CoverRecovery({ it.copy(cover = cover) }).resolve(book))
        }
    }

    @Test fun cancellingLookupDoesNotPoisonRecoveryCache() = runBlocking {
        var calls = 0
        val started = CompletableDeferred<Unit>()
        val recovery = CoverRecovery({
            if (++calls == 1) { started.complete(Unit); awaitCancellation() }
            it.copy(cover = "https://cdn.example/cover.jpg")
        })
        val job = launch { recovery.resolve(book) }
        started.await()
        job.cancelAndJoin()
        assertEquals("https://cdn.example/cover.jpg", recovery.resolve(book))
        assertEquals(2, calls)
    }
}
