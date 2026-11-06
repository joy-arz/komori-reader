package app.komori.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class LibraryCountsTest {
    @Test fun completionSnapshotsIgnorePageOnlyInvalidationAndRespectLanguages() = runBlocking {
        val chapters = MutableSharedFlow<List<LibraryChapterKey>>(replay = 1)
        val completed = MutableSharedFlow<List<String>>(replay = 1)
        val snapshots = java.util.concurrent.CopyOnWriteArrayList<List<LibraryLanguageCount>>()
        val job = launch { libraryLanguageCounts(chapters, completed).collect { snapshots += it } }
        try {
            chapters.emit(listOf(LibraryChapterKey("book", "en", "one"), LibraryChapterKey("book", "en", "two"), LibraryChapterKey("book", "ja", "jp")))
            completed.emit(emptyList())
            withTimeout(3000) { while (snapshots.isEmpty()) yield() }
            assertEquals(listOf(LibraryLanguageCount("book", "en", 2, 2), LibraryLanguageCount("book", "ja", 1, 1)), snapshots.last())
            repeat(50) { completed.emit(emptyList()) }
            completed.emit(listOf("one"))
            withTimeout(3000) { while (snapshots.last().first().unread != 1) yield() }
            assertEquals(2, snapshots.size)
            completed.emit(emptyList())
            withTimeout(3000) { while (snapshots.last().first().unread != 2) yield() }
            assertEquals(3, snapshots.size)
        } finally { job.cancelAndJoin() }
    }
}
