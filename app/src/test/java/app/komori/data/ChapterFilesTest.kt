package app.komori.data

import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ChapterFilesTest {
    @Test
    fun deletionWaitsForWriterAndRejectsOldRepair() = runBlocking {
        val id = UUID.randomUUID().toString()
        val token = ChapterFiles.version(id)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val writer = launch {
            ChapterFiles.locked(id) {
                entered.complete(Unit)
                release.await()
                events.add("writer")
            }
        }
        entered.await()
        val deletion =
            launch(start = CoroutineStart.UNDISPATCHED) {
                ChapterFiles.locked(id) {
                    ChapterFiles.invalidate(id)
                    events.add("deleted")
                }
            }
        assertTrue(events.isEmpty())
        release.complete(Unit)
        writer.join()
        deletion.join()
        assertEquals(listOf("writer", "deleted"), events)
        assertNotEquals(token, ChapterFiles.version(id))
    }

    @Test
    fun cancelledWriterReleasesChapterLock() = runBlocking {
        val id = UUID.randomUUID().toString()
        val entered = CompletableDeferred<Unit>()
        val writer = launch {
            ChapterFiles.locked(id) {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await()
        writer.cancelAndJoin()
        withTimeout(1000) { ChapterFiles.locked(id) { ChapterFiles.invalidate(id) } }
    }
}
