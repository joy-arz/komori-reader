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

    private fun delayedShortPage(shrinkingPage: Int) = runBlocking<Unit> {
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
        val short = image(80)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.host == "demonicscans.org")
                (0..3).joinToString("") { "<img class='imgholder' alt='page' src='https://images.example/$token/$it.png'>" }.toResponseBody()
            else {
                val page = request.url.pathSegments.last().substringBefore('.').toInt()
                if (page == shrinkingPage) { arrived.countDown(); check(deliver.await(30, TimeUnit.SECONDS)) }
                (if (page == shrinkingPage) short else tall).toResponseBody()
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        }.build()
        val vm = withContext(Dispatchers.Main) { ReaderViewModel(app, Sources(client, client)) }
        val list = LazyListState()
        try {
            withTimeout(5000) { vm.settings.first { it.preloadPages == 0 && it.continuousChapters } }
            compose.runOnUiThread {
                vm.selected.value = book
                vm.chapters.value = listOf(chapter, next)
                vm.read(chapter, requestedPage = 0)
                compose.activity.setContent { MaterialTheme {
                    Box(Modifier.width(300.dp).height(400.dp)) { WebtoonReader(vm, chapter, state = list) }
                } }
            }
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Ch. 1, page 1, part 1 of 2").fetchSemanticsNodes().isNotEmpty() }
            val offset = (300 * compose.activity.resources.displayMetrics.density).toInt()
            val key = "${chapter.id}|$shrinkingPage|0"
            val initialCount = list.layoutInfo.totalItemsCount
            compose.runOnIdle { list.requestScrollToItem(if (shrinkingPage == 3) initialCount - 2 else 3, offset) }
            compose.waitUntil(5000) { list.layoutInfo.visibleItemsInfo.firstOrNull()?.key == key }
            assertTrue("The delayed page must really use the HTTP transport", arrived.await(5, TimeUnit.SECONDS))
            assertEquals(offset, list.firstVisibleItemScrollOffset)
            if (shrinkingPage == 3) compose.waitUntil(5000) { list.layoutInfo.totalItemsCount > initialCount }
            deliver.countDown()
            compose.waitUntil(10000) {
                list.layoutInfo.visibleItemsInfo.firstOrNull()?.let { it.key != key || it.size < offset } == true
            }
            compose.waitForIdle()
            assertEquals("A shrinking network placeholder must not advance to unread pages", key,
                list.layoutInfo.visibleItemsInfo.firstOrNull()?.key)
            assertEquals("A page shorter than the placeholder offset must open at its beginning", 0, list.firstVisibleItemScrollOffset)
            assertNull("Streaming must not create a downloaded chapter", app.dao.download(chapter.id))
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
