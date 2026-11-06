package app.komori

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class LibraryCountsObservationTest {
    @Test fun roomCountsDeduplicateReleasesRespectLanguageAndObserveCompletionChanges() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        val dao = database.dao()
        try {
            dao.putChapterIndex(listOf(
                ChapterIndex("en-1-a", "book", "book|en|1", "en"),
                ChapterIndex("en-1-b", "book", "book|en|1", "en"),
                ChapterIndex("en-2", "book", "book|en|2", "en"),
                ChapterIndex("jp-1", "book", "book|ja|1", "ja"),
                ChapterIndex("retired", "book", "book|en|3", "en", false),
            ))
            val observed = java.util.concurrent.CopyOnWriteArrayList<List<LibraryLanguageCount>>()
            val job = launch { dao.observeLibraryLanguageCounts().collect { observed += it } }
            suspend fun awaitCounts(en: Int, jp: Int) = withTimeout(5000) {
                while (observed.lastOrNull()?.associate { it.language to it.unread } != mapOf("en" to en, "ja" to jp)) yield()
            }
            try {
                awaitCounts(2, 1)
                repeat(50) { dao.putProgress(Progress("en-1-a", "book", "book|en|1", it, false)) }
                dao.putProgress(Progress("en-1-a", "book", "book|en|1", 49, true))
                awaitCounts(1, 1)
                assertEquals(2, observed.size)
                assertEquals(2, observed.last().single { it.language == "en" }.total)
                dao.putProgress(Progress("en-1-b", "book", "book|en|1", 2, true))
                dao.putProgress(Progress("jp-1", "book", "book|ja|1", 1, true))
                awaitCounts(1, 0)
                dao.resetProgress("book")
                awaitCounts(2, 1)
            } finally { job.cancelAndJoin() }
        } finally { database.close() }
    }
}
