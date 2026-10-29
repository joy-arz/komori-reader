package app.komori

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
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
                    Box(Modifier.width(300.dp).height(400.dp)) { WebtoonReader(vm, chapter, state = list) }
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
