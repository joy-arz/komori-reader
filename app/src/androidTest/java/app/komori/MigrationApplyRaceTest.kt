package app.komori

import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.data.*
import app.komori.ui.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MigrationApplyRaceTest {
    @Test fun obsoleteChapterLoadCannotSupplyRowsToLatestTransactionalApply() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KomoriApp
        val prefix = "migration-fixture-${java.util.UUID.randomUUID()}"
        val original = Manga(prefix, "Original", "", "")
        val oldTarget = Manga(sourceId("zap", "/gallery/$prefix-old/"), "Obsolete target", "", "")
        val latest = Manga(sourceId("zap", "/gallery/$prefix-latest/"), "Latest target", "", "")
        fun row(book: Manga) = Chapter("${book.id}-chapter", book.id, JSONObject().put("id", "${book.id}-chapter")
            .put("attributes", JSONObject().put("chapter", "1").put("pages", 20).put("translatedLanguage", "en"))
            .put("relationships", JSONArray().put(JSONObject().put("type", "manga").put("id", book.id))).toString())
        val sourceRow = row(original); val obsoleteRows = CompletableDeferred<List<Chapter>>()
        val detailsStarted = CompletableDeferred<Unit>(); val oldJobFinished = CompletableDeferred<Unit>()
        val requests = object : MigrationRequests {
            override suspend fun browse(source: String, query: String, page: Int) = BrowsePage(listOf(oldTarget, latest), false)
            override suspend fun details(candidate: Manga) = candidate
            override suspend fun chapters(id: String, language: String): List<Chapter> {
                if (id != oldTarget.id) return listOf(row(latest))
                detailsStarted.complete(Unit)
                try { return withContext(NonCancellable) { obsoleteRows.await() } } finally { oldJobFinished.complete(Unit) }
            }
        }
        val vm = withContext(Dispatchers.Main) { MigrationViewModel(app, requests, Unit) }
        suspend fun waitFor(predicate: () -> Boolean) = withTimeout(5000) { while (!predicate()) delay(10) }
        try {
            app.dao.putManga(original); app.dao.putShelf(Shelf(original.id, saved = true))
            app.dao.putChapters(listOf(sourceRow))
            app.dao.putProgress(Progress(sourceRow.id, original.id, sourceRow.release().key, 19, completed = true))
            withContext(Dispatchers.Main) { vm.begin(original); vm.edit("first", "zap"); vm.search() }
            waitFor { !vm.state.value.loading }
            withContext(Dispatchers.Main) { vm.select(oldTarget) }
            withTimeout(5000) { detailsStarted.await() }
            withContext(Dispatchers.Main) { vm.edit("latest", "zap"); vm.search() }
            waitFor { !vm.state.value.loading }
            assertNull(vm.state.value.selected); assertTrue(vm.state.value.targets.isEmpty())
            withContext(Dispatchers.Main) { vm.select(latest) }
            waitFor { vm.state.value.selected == latest }
            obsoleteRows.complete(listOf(row(oldTarget))); oldJobFinished.await()
            withContext(Dispatchers.Main) { yield() }
            assertEquals(latest, vm.state.value.selected)
            assertTrue(vm.state.value.targets.all { it.mangaId == latest.id })
            assertEquals(row(latest).id, vm.state.value.plan.single().target!!.id)
            val applied = CompletableDeferred<Manga>()
            withContext(Dispatchers.Main) { vm.apply(true, false) { applied.complete(it) } }
            assertEquals(latest, withTimeout(5000) { applied.await() })
            assertTrue(app.dao.shelf(latest.id)!!.saved)
            assertTrue(app.dao.progress(row(latest).id)!!.completed)
            assertNull(app.dao.shelf(oldTarget.id)); assertNull(app.dao.chapter(row(oldTarget).id))
        } finally {
            obsoleteRows.complete(emptyList())
            withContext(Dispatchers.Main) { vm.close(); vm.viewModelScope.cancel() }
            app.database.withTransaction {
                val sql = app.database.openHelper.writableDatabase
                for (book in listOf(original, oldTarget, latest)) {
                    for (table in listOf("progress", "shelf", "chapters", "chapter_index", "chapter_feeds", "chapter_preferences"))
                        sql.execSQL("DELETE FROM $table WHERE mangaId = ?", arrayOf(book.id))
                    sql.execSQL("DELETE FROM manga WHERE id = ?", arrayOf(book.id))
                }
                sql.execSQL("DELETE FROM migration_links WHERE fromId = ?", arrayOf(original.id))
            }
        }
    }
}
