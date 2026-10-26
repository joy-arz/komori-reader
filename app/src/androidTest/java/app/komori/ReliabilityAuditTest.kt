package app.komori

import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import app.komori.data.*
import app.komori.domain.Release
import app.komori.ui.*
import java.io.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReliabilityAuditTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KomoriApp
    private fun directory() = File(app.cacheDir, "audit-${UUID.randomUUID()}").apply { mkdirs() }
    private fun image(): ByteArray {
        val bitmap = Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); bitmap.recycle() }.toByteArray()
    }
    private fun release(id: String, book: String = id) = Release(id, book, "1", null, "", "en", emptyList(), "", 10)
    private fun chapter(book: String, id: String, language: String) = Chapter(id, book, JSONObject().put("id", id)
        .put("attributes", JSONObject().put("chapter", "1").put("translatedLanguage", language).put("pages", 1))
        .put("relationships", JSONArray().put(JSONObject().put("id", book).put("type", "manga"))).toString())

    @Test fun cleanupReclaimsOrphansWithoutDeletingActiveTransfersOrPins() = runBlocking<Unit> {
        val dir = directory(); val owner = Any()
        val active = ReaderCacheFiles.temporary(dir).apply { writeBytes(ByteArray(20)) }
        val orphan = File(dir, "old.part").apply { writeBytes(ByteArray(40)) }
        val visible = File(dir, "visible.img").apply { writeBytes(ByteArray(10)) }
        try {
            ReaderCacheFiles.pin(owner, listOf(visible))
            assertEquals(40L, ReaderCacheFiles.clean(dir, images = true))
            assertFalse(orphan.exists()); assertTrue(active.exists()); assertTrue(visible.exists())
            ReaderCacheFiles.release(owner)
            assertEquals(10L, ReaderCacheFiles.clean(dir, images = true))
            assertTrue(active.exists())
        } finally { ReaderCacheFiles.release(owner); ReaderCacheFiles.discard(active); dir.deleteRecursively() }
    }

    @Test fun evictionPreservesEveryVisibleAndDecodingFile() = runBlocking<Unit> {
        val dir = directory(); val visibleOwner = Any()
        fun sparse(name: String, mb: Int) = File(dir, name).also { RandomAccessFile(it, "rw").use { out -> out.setLength(mb * 1024L * 1024) } }
        val first = sparse("first.img", 20); val second = sparse("second.img", 20); val ahead = sparse("ahead.img", 32)
        val temporary = ReaderCacheFiles.temporary(dir).also { RandomAccessFile(it, "rw").use { out -> out.setLength(10L * 1024 * 1024) } }
        try {
            ReaderCacheFiles.pin(visibleOwner, listOf(first))
            ReaderCacheFiles.leased(second) {
                ReaderCacheFiles.publish(temporary, File(dir, "target.img"), 64L * 1024 * 1024)
                assertTrue(first.exists()); assertTrue(second.exists()); assertFalse(ahead.exists())
            }
        } finally { ReaderCacheFiles.release(visibleOwner); ReaderCacheFiles.discard(temporary); dir.deleteRecursively() }
    }

    @Test fun rawUnreadCountsUseTheSameEffectiveLanguageAsReading() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).build()
        try {
            val book = sourceId("manhwa18cc", "/webtoon/audit-raw/")
            db.dao().saveFeed(book, "ko", listOf(chapter(book, "audit-ko", "ko")))
            val raw = db.dao().observeLibraryCounts("en").first().single()
            assertEquals(1, raw.unread)
            db.dao().putTitlePreferences(TitlePreferences(book, language = "en"))
            assertTrue(db.dao().observeLibraryCounts("en").first().isEmpty())
            db.dao().putTitlePreferences(TitlePreferences(book, language = "*"))
            db.dao().saveFeed(book, "en", listOf(chapter(book, "audit-en", "en")))
            assertEquals(2, db.dao().observeLibraryCounts("en").first().single().total)
        } finally { db.close() }
    }

    @Test fun failingAuthorChecksDoNotStarveOtherCreators() {
        val rotation = AuthorCheckRotation(app)
        val creators = (1..5).map { Creator("audit-${UUID.randomUUID()}-$it", "Author $it", "artist") }
        try {
            val first = rotation.candidates(creators, 100)
            first.forEach { rotation.attempted(it.id, false, 100) }
            val next = rotation.candidates(creators, 101)
            assertEquals(2, next.size); assertTrue(next.none { it in first })
            next.forEach { rotation.attempted(it.id, true, 102) }
            assertEquals(3, rotation.candidates(creators, 100 + 1800001).size)
        } finally {
            val edit = app.getSharedPreferences("author-check-rotation", 0).edit()
            creators.forEach { edit.remove("attempt:${it.id}").remove("due:${it.id}").remove("failures:${it.id}") }; edit.apply()
        }
    }

    @Test fun speculativeFailuresDoNotConsumeVisiblePageRetries() = runBlocking<Unit> {
        val id = UUID.randomUUID().toString(); var failing = true; val calls = AtomicInteger(); val bytes = image()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.encodedPath.startsWith("/at-home/"))
                """{"baseUrl":"https://cdn.example","chapter":{"hash":"audit","data":["0.png"],"dataSaver":["0.png"]}}""".toResponseBody()
            else { calls.incrementAndGet(); if (failing) throw IOException("Offline fixture"); bytes.toResponseBody() }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        }.build()
        val vm = withContext(Dispatchers.Main) { ReaderViewModel(app, Sources(client, client)) }
        try {
            repeat(4) { try { vm.pageFileFor(release(id), 0, speculative = true); fail() } catch (e: IOException) { assertFalse(e is PageRetryLimit) } }
            failing = false
            assertTrue(validImage(vm.pageFileFor(release(id), 0)))
            assertEquals(5, calls.get())
        } finally {
            val job = vm.viewModelScope.coroutineContext[Job]
            withContext(Dispatchers.Main) { vm.viewModelScope.cancel() }; job?.join()
            File(app.cacheDir, "reader").listFiles().orEmpty().filter { it.name.startsWith(id) }.forEach { it.delete() }
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun plannedDownloadSlicesDoNotSpendFailureBudget() = runBlocking<Unit> {
        app.startupRecovery?.join()
        val id = UUID.randomUUID().toString(); var time = 0L; val bytes = image()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.encodedPath.startsWith("/at-home/"))
                """{"baseUrl":"https://cdn.example","chapter":{"hash":"audit","data":["0.png","1.png"],"dataSaver":["0.png","1.png"]}}""".toResponseBody()
            else { time += 9 * 60 * 1000; bytes.toResponseBody() }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        }.build()
        app.dao.putDownload(Download(id, id, "Slice fixture", 2))
        try {
            val worker = TestListenableWorkerBuilder<ChapterDownloadWorker>(app).setRunAttemptCount(1).setInputData(workDataOf("chapter" to id)).build()
            worker.elapsedMillis = { time }
            var carried = -1L
            worker.continuation = { _, chapter, failures, delay -> assertEquals(id, chapter); assertEquals(0L, delay); carried = failures }
            assertEquals(ListenableWorker.Result.success(), worker.download(Sources(client, client)))
            assertEquals(1L, carried); assertEquals(1, app.dao.download(id)!!.done); assertEquals("Queued", app.dao.download(id)!!.state)
        } finally { app.dao.removeDownload(id); chapterDirectory(app, id).deleteRecursively(); client.dispatcher.executorService.shutdown() }
    }

    @Test fun archiveStreamAcceptsExportsAboveTheOldLimitButRemainsBounded() {
        class VirtualInput(var remaining: Long) : InputStream() {
            override fun read(): Int = if (remaining-- > 0) 0 else -1
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(length.toLong(), remaining).toInt(); remaining -= count; return count
            }
        }
        val buffer = ByteArray(32768)
        LimitedArchive(VirtualInput(300L * 1024 * 1024)).use { while (it.read(buffer) >= 0) { } }
        try {
            LimitedArchive(VirtualInput(517L * 1024 * 1024)).use { while (it.read(buffer) >= 0) { } }; fail()
        } catch (_: ContentFailure) { }
    }

    @Test fun repeatedScrollSamplesReduceWritesAndFinalCheckpointIsExact() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).build()
        var progressWrites = 0; var shelfWrites = 0
        val real = db.dao()
        val proxy = java.lang.reflect.Proxy.newProxyInstance(LibraryDao::class.java.classLoader, arrayOf(LibraryDao::class.java)) { _, method, args ->
            if (method.name == "putProgress") progressWrites++
            if (method.name == "putShelf") shelfWrites++
            try { method.invoke(real, *(args ?: emptyArray())) } catch (e: java.lang.reflect.InvocationTargetException) { throw e.targetException }
        } as LibraryDao
        try {
            val store = ReaderProgressStore(proxy, Mutex()); val chapter = release("audit-progress")
            val started = System.nanoTime()
            repeat(100) { store.save(chapter, 0, false, it / 1000f, true, false) }
            store.save(chapter, 0, false, 0.099f, true, true)
            assertEquals(0.099f, real.progress(chapter.id)!!.scroll, 0.0001f)
            assertTrue(progressWrites < 100); assertEquals(1, shelfWrites)
            android.util.Log.i("KomoriBenchmark", "samples=101 progressWrites=$progressWrites shelfWrites=$shelfWrites elapsedMs=${(System.nanoTime() - started) / 1000000}")
        } finally { db.close() }
    }

    @Test fun unchangedProgressStillSelectsTheReopenedChapter() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).build()
        try {
            val chapter = release("old-chapter", "book")
            db.dao().putProgress(Progress(chapter.id, chapter.mangaId, chapter.key, 2, false, 0.25f))
            db.dao().putShelf(Shelf(chapter.mangaId, saved = true, lastRelease = "new-chapter"))
            ReaderProgressStore(db.dao(), Mutex()).save(chapter, 2, false, 0.25f, true, true)
            assertEquals(chapter.id, db.dao().shelf(chapter.mangaId)!!.lastRelease)
            assertTrue(db.dao().shelf(chapter.mangaId)!!.saved)
        } finally { db.close() }
    }

    @Test fun interruptedImportCleanupSkipsAnOwnedChapterThenRemovesTheOrphan() = runBlocking<Unit> {
        app.startupRecovery?.join()
        val id = sourceId("local", "/chapter/${UUID.randomUUID()}/")
        val directory = chapterDirectory(app, id).apply { mkdirs() }
        val page = File(directory, "0.img").apply { writeBytes(image()) }
        try {
            ChapterFiles.locked(id) {
                LocalFiles.recoverInterrupted(app)
                assertTrue(page.exists())
            }
            LocalFiles.recoverInterrupted(app)
            assertFalse(directory.exists())
        } finally { directory.deleteRecursively() }
    }

    @Test fun mixedGifAndPngExportCanBeImportedWithoutDroppingPages() = runBlocking<Unit> {
        app.startupRecovery?.join()
        val archive = File(app.cacheDir, "mixed-${UUID.randomUUID()}.cbz")
        val exported = File(app.cacheDir, "mixed-export-${UUID.randomUUID()}.cbz")
        val titles = mutableListOf<Manga>()
        val gif = android.util.Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", android.util.Base64.DEFAULT)
        val png = image()
        try {
            java.util.zip.ZipOutputStream(archive.outputStream()).use { zip ->
                listOf("1.gif" to gif, "2.png" to png).forEach { (name, bytes) ->
                    zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                }
            }
            val first = LocalFiles.importArchive(app, android.net.Uri.fromFile(archive)) {}.also { titles += it }
            val firstChapter = app.dao.chapters(first.id).single()
            LocalFiles.export(app, app.dao.download(firstChapter.id)!!, android.net.Uri.fromFile(exported), null) {}
            val second = LocalFiles.importArchive(app, android.net.Uri.fromFile(exported)) {}.also { titles += it }
            val secondChapter = app.dao.chapters(second.id).single()
            assertEquals(2, app.dao.download(secondChapter.id)!!.total)
            assertArrayEquals(gif, pageFile(app, secondChapter.id, 0).readBytes())
            assertArrayEquals(png, pageFile(app, secondChapter.id, 1).readBytes())
        } finally {
            archive.delete(); exported.delete()
            titles.forEach { title ->
                app.dao.chapters(title.id).forEach { chapter -> chapterDirectory(app, chapter.id).deleteRecursively(); app.dao.removeDownload(chapter.id) }
                app.database.openHelper.writableDatabase.apply {
                    listOf("chapter_index", "chapters", "chapter_feeds", "shelf").forEach { execSQL("DELETE FROM $it WHERE mangaId = ?", arrayOf(title.id)) }
                    execSQL("DELETE FROM manga WHERE id = ?", arrayOf(title.id))
                }
            }
        }
    }

    @Test fun pauseAndDeleteStaySerializedWithForegroundRecovery() = runBlocking<Unit> {
        app.startupRecovery?.join()
        val id = "pause-audit-${UUID.randomUUID()}"
        val row = Download(id, id, "Pause fixture", 1, state = "Downloading")
        val held = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val owner = launch(Dispatchers.IO) { ChapterFiles.locked(id) { held.complete(Unit); release.await() } }
        val manager = androidx.work.WorkManager.getInstance(app)
        app.dao.putDownload(row)
        chapterDirectory(app, id).mkdirs()
        val retained = pageFile(app, id, 0).apply { writeBytes(image()) }
        try {
            held.await()
            val pause = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) { DownloadCoordinator(app).stop(row, "Paused") }
            delay(100)
            val recovery = async(Dispatchers.IO) { kickDownloadQueue(app, recoverInterrupted = true) }
            delay(100); assertFalse(pause.isCompleted)
            release.complete(Unit)
            withTimeout(10000) { pause.await(); recovery.await(); owner.join() }
            assertEquals("Paused", app.dao.download(id)!!.state)
            assertTrue(retained.exists())
            assertTrue(manager.getWorkInfosForUniqueWork("download-$id").get().all { it.state.isFinished })
            coroutineScope {
                launch(Dispatchers.IO) { DownloadCoordinator(app).delete(app.dao.download(id)!!) }
                launch(Dispatchers.IO) { kickDownloadQueue(app, recoverInterrupted = true) }
            }
            assertNull(app.dao.download(id)); assertFalse(chapterDirectory(app, id).exists())
        } finally {
            release.complete(Unit); owner.cancelAndJoin()
            manager.cancelUniqueWork("download-$id").result.get()
            app.dao.removeDownload(id); chapterDirectory(app, id).deleteRecursively()
        }
    }

    @Test fun closingReaderFlushesFinalScrollWithoutReopeningIt() = runBlocking<Unit> {
        val id = "exit-audit-${UUID.randomUUID()}"
        val chapter = release(id).copy(local = true)
        val vm = withContext(Dispatchers.Main) { ReaderViewModel(app) }
        try {
            withContext(Dispatchers.Main) { vm.read(chapter) }
            withTimeout(5000) { vm.readerReady.first { it } }
            val session = vm.readerSession.value
            withContext(Dispatchers.Main) {
                vm.closeReader()
                vm.scrollChapterPosition(chapter, 0, 0.345f, session, flush = true)
            }
            withTimeout(5000) { while (app.dao.progress(id)?.scroll != 0.345f) delay(25) }
            assertNull(vm.reader.value)
            assertEquals(0.345f, app.dao.progress(id)!!.scroll, 0.0001f)
        } finally {
            val job = vm.viewModelScope.coroutineContext[Job]
            withContext(Dispatchers.Main) { vm.viewModelScope.cancel() }; job?.join()
            app.dao.resetProgress(id)
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM shelf WHERE mangaId = ?", arrayOf(id))
        }
    }

}
