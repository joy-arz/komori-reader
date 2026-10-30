package app.komori

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.komori.data.*
import app.komori.domain.Release
import app.komori.ui.ReaderViewModel
import app.komori.ui.WebtoonReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StreamingReaderPositionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun image(height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(400, height, Bitmap.Config.RGB_565)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        return try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
        finally { bitmap.recycle() }
    }

    @Test fun delayedShortPageDoesNotSkipIntoFollowingPages() = delayedShortPage(1)

    @Test fun delayedShortLastPageDoesNotSkipIntoNextChapter() = delayedShortPage(3)

    @Test fun delayedTallPageKeepsExistingPixelOffset() = delayedShortPage(1, 1800)

    @Test fun shortFinalPageCanSettleAtTheEndOfTheTitle() = delayedShortPage(3, withNext = false)

    @Test fun downloadedChapterAppendKeepsLastPageVisible() = downloadedChapterBoundary(5)

    @Test fun downloadedChapterAppendKeepsPenultimatePageVisible() = downloadedChapterBoundary(4)

    @Test fun continuingToNextChapterIgnoresItsOldUnfinishedPosition() = downloadedChapterBoundary(5, continueButton = true)

    @Test fun scrollingIntoNextChapterIgnoresItsOldUnfinishedPosition() = downloadedChapterBoundary(5, enterByScrolling = true)

    @Test fun chapterWindowDoesNotReindexDuringActiveScroll() = downloadedChapterBoundary(5, holdScroll = true)

    private fun downloadedChapterBoundary(initialPage: Int, continueButton: Boolean = false, enterByScrolling: Boolean = false, holdScroll: Boolean = false) = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        val token = "offline-position-${UUID.randomUUID()}"
        val book = Manga(token, "Offline position fixture", "", "")
        val chapters = (1..5).map { number -> Release("$token-$number", book.id, "$number", null, "", "en", emptyList(), "", 6, local = true) }
        val previous = app.settings.flow.first()
        val client = OkHttpClient.Builder().addInterceptor { throw AssertionError("Downloaded reader must not use the network") }.build()
        val vm = withContext(Dispatchers.Main) { ReaderViewModel(app, Sources(client, client)) }
        val list = LazyListState(initialPage + 1)
        try {
            app.settings.number("preloadPages", 0)
            app.settings.set("continuousChapters", true)
            app.dao.putManga(book)
            for (chapter in chapters) {
                chapterDirectory(app, chapter.id).mkdirs()
                for (page in 0 until chapter.pages) pageFile(app, chapter.id, page).writeBytes(image(if (page == 5) 800 else 1800))
                app.dao.putDownload(Download(chapter.id, book.id, chapter.label, 6, 6, "Complete"))
            }
            app.settings.set("resume", true)
            if (continueButton || enterByScrolling) app.dao.putProgress(Progress(chapters[1].id, book.id, chapters[1].key, 5, false, .95f))
            compose.waitUntil(5000) { vm.settings.value.let { it.preloadPages == 0 && it.continuousChapters && it.resume } }
            compose.runOnUiThread {
                vm.selected.value = book
                vm.chapters.value = listOf(chapters.first())
                vm.read(chapters.first(), requestedPage = initialPage)
            }
            compose.waitUntil(5000) { vm.readerReady.value }
            if (continueButton) {
                compose.runOnIdle {
                    vm.chapters.value = chapters.take(2)
                    vm.read(chapters[1])
                }
                compose.waitUntil(5000) { vm.readerReady.value && vm.reader.value?.id == chapters[1].id }
                assertEquals("Opening a chapter directly must still resume its saved position", 5, vm.readerPage.value)
                assertEquals(.95f, vm.readerScroll.value, 0f)
                compose.runOnIdle { vm.read(chapters[0], requestedPage = initialPage) }
                compose.waitUntil(5000) { vm.readerReady.value && vm.reader.value?.id == chapters[0].id }
                withContext(Dispatchers.Main) { vm.finishChapter() }.join()
                compose.waitUntil(5000) { vm.readerReady.value && vm.reader.value?.id == chapters[1].id }
                assertEquals("Forward continuation must begin at the first image, not resume old destination progress", 0, vm.readerPage.value)
                assertEquals(0f, vm.readerScroll.value, 0f)
                return@runBlocking
            }
            compose.runOnUiThread { compose.activity.setContent { MaterialTheme {
                Box(Modifier.width(300.dp).height(400.dp).testTag("offline-viewport")) {
                    WebtoonReader(vm, chapters.first(), state = list)
                }
            } } }
            val key = "${chapters.first().id}|$initialPage|0"
            compose.waitUntil(10000) {
                list.layoutInfo.visibleItemsInfo.firstOrNull()?.let { it.key == key && it.size != (420 * compose.activity.resources.displayMetrics.density).toInt() } == true
            }
            compose.waitForIdle()
            assertEquals("Completed downloads must publish all page geometry before reading", 13, list.layoutInfo.totalItemsCount)
            if (initialPage == 4) {
                val offset = (100 * compose.activity.resources.displayMetrics.density).toInt()
                compose.runOnIdle { list.requestScrollToItem(list.firstVisibleItemIndex, offset) }
                compose.waitUntil(5000) {
                    list.layoutInfo.visibleItemsInfo.firstOrNull()?.let { it.key == key && it.offset == -offset } == true
                }
            }
            val before = list.layoutInfo.visibleItemsInfo.first { it.key == key }.offset
            compose.runOnIdle { vm.chapters.value = chapters.take(2) }
            compose.waitUntil(5000) { list.layoutInfo.totalItemsCount >= 15 }
            compose.waitForIdle()
            assertEquals("The next download must enter with its final strip count", 25, list.layoutInfo.totalItemsCount)
            assertEquals("Appending a downloaded chapter must keep the current page", key, list.layoutInfo.visibleItemsInfo.firstOrNull()?.key)
            assertEquals("Appending must keep the current image pixel position", before, list.layoutInfo.visibleItemsInfo.first { it.key == key }.offset)
            if (enterByScrolling) {
                repeat(6) {
                    if (vm.reader.value?.id != chapters[1].id) {
                        compose.onNodeWithTag("offline-viewport").performTouchInput {
                            swipe(start = center.copy(y = height * .75f), end = center.copy(y = height * .4f), durationMillis = 600)
                        }
                        compose.waitForIdle()
                    }
                }
                compose.waitUntil(5000) { vm.reader.value?.id == chapters[1].id }
                assertEquals("Continuous handoff must enter page one even with old destination progress near the end", 0, vm.readerPage.value)
                assertEquals("The viewport must remain on the first image of the next chapter", "${chapters[1].id}|0|0", list.layoutInfo.visibleItemsInfo.firstOrNull { it.key.toString().split('|').size == 3 }?.key)
                return@runBlocking
            }
            for (number in 2..5) {
                val chapter = chapters[number - 1]
                val lastKey = "${chapter.id}|5|0"
                if (holdScroll && number == 3) {
                    val releaseScroll = CompletableDeferred<Unit>()
                    val scroll = withContext(Dispatchers.Main) {
                        vm.viewModelScope.launch { list.scroll { scrollBy(100000f); releaseScroll.await() } }
                    }
                    try {
                        compose.waitUntil(5000) { vm.reader.value?.id == chapter.id && vm.readerPage.value == 5 }
                        compose.waitForIdle()
                        assertTrue("The synthetic gesture must still own scrolling", list.isScrollInProgress)
                        assertEquals("Do not remove chapter rows while a gesture or fling is active", 37, list.layoutInfo.totalItemsCount)
                        val anchor = list.layoutInfo.visibleItemsInfo.first { it.key.toString().split('|').size == 3 }
                        releaseScroll.complete(Unit)
                        scroll.join()
                        compose.waitUntil(5000) { list.layoutInfo.totalItemsCount == 25 }
                        compose.waitForIdle()
                        val retained = list.layoutInfo.visibleItemsInfo.first { it.key.toString().split('|').size == 3 }
                        assertEquals("Settled trimming retains the exact page and strip", anchor.key, retained.key)
                        assertEquals("Settled trimming retains its pixel position", anchor.offset, retained.offset)
                        return@runBlocking
                    } finally { releaseScroll.complete(Unit); scroll.cancelAndJoin() }
                }
                compose.runOnIdle { list.requestScrollToItem(list.layoutInfo.totalItemsCount - 2) }
                compose.waitUntil(10000) {
                    list.layoutInfo.visibleItemsInfo.firstOrNull()?.let { it.key == lastKey && kotlin.math.abs(it.size - (600 * compose.activity.resources.displayMetrics.density).toInt()) <= 1 } == true
                }
                compose.waitUntil(5000) { vm.reader.value?.id == chapter.id && vm.readerPage.value == 5 }
                compose.waitForIdle()
                assertEquals("Trimming older downloaded chapters must not skip the visible page", lastKey, list.layoutInfo.visibleItemsInfo.firstOrNull()?.key)
                if (number < 5) {
                    val oldCount = list.layoutInfo.totalItemsCount
                    val oldOffset = list.layoutInfo.visibleItemsInfo.first().offset
                    compose.runOnIdle { vm.chapters.value = chapters.take(number + 1) }
                    compose.waitUntil(5000) { list.layoutInfo.totalItemsCount >= oldCount + 7 }
                    compose.waitForIdle()
                    assertEquals("Downloaded boundary $number must retain the last page", lastKey, list.layoutInfo.visibleItemsInfo.firstOrNull()?.key)
                    assertEquals(oldOffset, list.layoutInfo.visibleItemsInfo.first().offset)
                }
            }
        } catch (error: Throwable) {
            android.util.Log.e("KomoriOffline", "first=${list.firstVisibleItemIndex}:${list.firstVisibleItemScrollOffset} total=${list.layoutInfo.totalItemsCount} settings=${vm.settings.value.preloadPages}/${vm.settings.value.continuousChapters}/${vm.settings.value.resume} visible=${list.layoutInfo.visibleItemsInfo.map { Triple(it.key, it.offset, it.size) }}", error)
            throw error
        } finally {
            withContext(Dispatchers.Main) { vm.closeReader(); vm.viewModelScope.cancel() }
            vm.viewModelScope.coroutineContext[Job]?.join()
            compose.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            app.settings.number("preloadPages", previous.preloadPages)
            app.settings.set("continuousChapters", previous.continuousChapters)
            app.settings.set("resume", previous.resume)
            chapters.forEach { chapterDirectory(app, it.id).deleteRecursively() }
            for (table in listOf("progress", "downloads", "shelf", "manga")) {
                val column = if (table == "manga") "id" else "mangaId"
                app.database.openHelper.writableDatabase.execSQL("DELETE FROM $table WHERE $column = ?", arrayOf(book.id))
            }
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    @Test fun cachedReadingDoesNotWaitForChapterWriter() = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        val token = "offline-writer-${UUID.randomUUID()}"
        val book = Manga(token, "Offline writer fixture", "", "")
        val chapter = Release("$token-1", book.id, "1", null, "", "en", emptyList(), "", 1, local = true)
        val previous = app.settings.flow.first()
        val client = OkHttpClient.Builder().addInterceptor { throw AssertionError("Cached reader must not use the network") }.build()
        val vm = withContext(Dispatchers.Main) { ReaderViewModel(app, Sources(client, client)) }
        val locked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var writer: Job? = null
        try {
            app.settings.number("preloadPages", 0)
            app.dao.putManga(book)
            chapterDirectory(app, chapter.id).mkdirs()
            pageFile(app, chapter.id, 0).writeBytes(image(1800))
            app.dao.putDownload(Download(chapter.id, book.id, chapter.label, 1, 1, "Complete"))
            writer = launch(Dispatchers.IO) { ChapterFiles.locked(chapter.id) { locked.complete(Unit); release.await() } }
            locked.await()
            compose.runOnUiThread {
                vm.selected.value = book
                vm.chapters.value = listOf(chapter)
                vm.read(chapter, requestedPage = 0)
            }
            compose.waitUntil(5000) { vm.readerReady.value }
            compose.runOnUiThread { compose.activity.setContent { MaterialTheme {
                Box(Modifier.width(300.dp).height(400.dp)) { WebtoonReader(vm, chapter) }
            } } }
            compose.waitUntil(5000) {
                compose.onAllNodesWithContentDescription("Ch. 1, page 1, part 1 of 2").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("Reading must complete while the chapter writer still holds the lock", writer.isActive)
        } finally {
            release.complete(Unit)
            writer?.cancelAndJoin()
            withContext(Dispatchers.Main) { vm.closeReader(); vm.viewModelScope.cancel() }
            vm.viewModelScope.coroutineContext[Job]?.join()
            compose.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            app.settings.number("preloadPages", previous.preloadPages)
            chapterDirectory(app, chapter.id).deleteRecursively()
            for (table in listOf("progress", "downloads", "shelf", "manga")) {
                val column = if (table == "manga") "id" else "mangaId"
                app.database.openHelper.writableDatabase.execSQL("DELETE FROM $table WHERE $column = ?", arrayOf(book.id))
            }
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun delayedShortPage(shrinkingPage: Int, deliveredHeight: Int = 80, withNext: Boolean = true) = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        val token = UUID.randomUUID().toString()
        val book = Manga(sourceId("demonic", "/manga/$token"), "Stream position fixture", "", "")
        val chapter = Release(sourceId("demonic", "/reader/$token"), book.id, "1", null, "", "en", emptyList(), "", 4)
        val next = chapter.copy(id = sourceId("demonic", "/reader/$token/next"), number = "2", pages = 3)
        val previous = app.settings.flow.first()
        app.settings.number("preloadPages", 0)
        app.settings.set("continuousChapters", true)
        app.dao.putManga(book)
        val arrived = CountDownLatch(1)
        val deliver = CountDownLatch(1)
        val tall = image(1800)
        val deliveredImage = image(deliveredHeight)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.host == "demonicscans.org")
                (0 until if (request.url.encodedPath.endsWith("/next")) 3 else 4).joinToString("") { "<img class='imgholder' alt='page' src='https://images.example/$token/$it.png'>" }.toResponseBody()
            else {
                val page = request.url.pathSegments.last().substringBefore('.').toInt()
                if (page == shrinkingPage) { arrived.countDown(); check(deliver.await(30, TimeUnit.SECONDS)) }
                (if (page == shrinkingPage) deliveredImage else tall).toResponseBody()
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        }.build()
        val vm = withContext(Dispatchers.Main) { ReaderViewModel(app, Sources(client, client)) }
        val list = LazyListState()
        try {
            withTimeout(5000) { vm.settings.first { it.preloadPages == 0 && it.continuousChapters } }
            compose.runOnUiThread {
                vm.selected.value = book
                vm.chapters.value = if (withNext) listOf(chapter, next) else listOf(chapter)
                vm.read(chapter, requestedPage = 0)
                compose.activity.setContent { MaterialTheme {
                    Box(Modifier.width(300.dp).height(400.dp).testTag("streaming-viewport")) { WebtoonReader(vm, chapter, state = list) }
                } }
            }
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Ch. 1, page 1, part 1 of 2").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(5000) {
                !list.isScrollInProgress && list.firstVisibleItemIndex == 1 && list.firstVisibleItemScrollOffset == 0
            }
            compose.waitForIdle()
            val offset = ((if (withNext) 300 else 100) * compose.activity.resources.displayMetrics.density).toInt()
            val key = "${chapter.id}|$shrinkingPage|0"
            val initialCount = list.layoutInfo.totalItemsCount
            compose.runOnIdle { list.requestScrollToItem(if (shrinkingPage == 3) initialCount - 2 else 3, offset) }
            compose.waitUntil(5000) { list.layoutInfo.visibleItemsInfo.firstOrNull()?.key == key }
            assertTrue("The delayed page must really use the HTTP transport", arrived.await(5, TimeUnit.SECONDS))
            if (shrinkingPage == 3 && withNext) {
                compose.waitUntil(5000) { list.layoutInfo.totalItemsCount > initialCount }
                compose.runOnIdle {
                    val index = list.layoutInfo.visibleItemsInfo.first { it.key == key }.index
                    list.requestScrollToItem(index, offset)
                }
                compose.waitUntil(5000) { list.firstVisibleItemScrollOffset == offset }
            }
            compose.waitUntil(5000) {
                list.layoutInfo.visibleItemsInfo.firstOrNull()?.let { it.key == key && it.offset == -offset } == true
            }
            assertEquals(offset, list.firstVisibleItemScrollOffset)
            val placeholderHeight = list.layoutInfo.visibleItemsInfo.first().size
            deliver.countDown()
            compose.waitUntil(10000) {
                list.layoutInfo.visibleItemsInfo.firstOrNull()?.let { it.key != key || it.size != placeholderHeight } == true
            }
            compose.waitForIdle()
            if (withNext) {
                assertEquals("A shrinking network placeholder must not advance to unread pages", key,
                    list.layoutInfo.visibleItemsInfo.firstOrNull()?.key)
                assertEquals("Short pages must start at the beginning; tall pages retain their offset",
                    if (deliveredHeight == 80) 0 else offset, list.firstVisibleItemScrollOffset)
                compose.waitUntil(5000) { vm.reader.value?.id == chapter.id && vm.readerPage.value == shrinkingPage }
                if (shrinkingPage == 1 && deliveredHeight == 80) {
                    compose.onNodeWithTag("streaming-viewport").performTouchInput { swipeUp(durationMillis = 600) }
                    compose.waitUntil(5000) { vm.reader.value?.id != chapter.id || vm.readerPage.value > shrinkingPage }
                }
            } else {
                val visible = list.layoutInfo.visibleItemsInfo.single { it.key == key }
                assertTrue("The final short image must remain entirely visible", visible.offset >= 0 &&
                    visible.offset + visible.size <= list.layoutInfo.viewportEndOffset)
                withTimeout(5000) { while (app.dao.progress(chapter.id)?.completed != true) delay(30) }
            }
            assertNull("Streaming must not create a downloaded chapter", app.dao.download(chapter.id))
        } catch (error: Throwable) {
            android.util.Log.e("KomoriStream", "first=${list.firstVisibleItemIndex} offset=${list.firstVisibleItemScrollOffset} reader=${vm.reader.value?.id}:${vm.readerPage.value} visible=${list.layoutInfo.visibleItemsInfo.map { Triple(it.key, it.offset, it.size) }}", error)
            throw error
        } finally {
            deliver.countDown()
            withContext(Dispatchers.Main) { vm.closeReader(); vm.viewModelScope.cancel() }
            vm.viewModelScope.coroutineContext[Job]?.join()
            compose.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            app.settings.number("preloadPages", previous.preloadPages)
            app.settings.set("continuousChapters", previous.continuousChapters)
            File(app.cacheDir, "reader").listFiles().orEmpty().filter { it.name.startsWith(chapter.id) || it.name.startsWith(next.id) }.forEach { it.delete() }
            for (table in listOf("progress", "shelf", "manga")) {
                val column = if (table == "manga") "id" else "mangaId"
                app.database.openHelper.writableDatabase.execSQL("DELETE FROM $table WHERE $column = ?", arrayOf(book.id))
            }
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
