package app.komori

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import app.komori.data.*
import app.komori.ui.*
import kotlinx.coroutines.*
import okhttp3.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DiscoverMetadataRetryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun partialResultsRetryOnlyMissingTitleAndObsoleteRetryCannotReplaceNewSearch() = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        val failedRequests = AtomicInteger()
        val retryStarted = CountDownLatch(1)
        val releaseRetry = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            var status = 200
            val html = when {
                request.url.encodedPath == "/advanced.php" -> "<label><input name='genres[]' value='1'>Action</label>"
                request.url.encodedPath == "/search.php" -> if (request.url.queryParameter("manga") == "new")
                    "<a href='/manga/new'><img alt='New result'></a>" else
                    "<a href='/manga/good'><img alt='Good result'></a><a href='/manga/bad'><img alt='Recovered result'></a>"
                request.url.encodedPath == "/manga/bad" -> {
                    when (failedRequests.incrementAndGet()) {
                        1, 3 -> { status = 404; "missing" }
                        4 -> { retryStarted.countDown(); check(releaseRetry.await(5, TimeUnit.SECONDS)); "<ul class='genres-list'><li>Action</li></ul>" }
                        else -> "<ul class='genres-list'><li>Action</li></ul>"
                    }
                }
                else -> "<ul class='genres-list'><li>Action</li></ul>"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Fixture")
                .body(ResponseBody.create(null, html)).build()
        }.build()
        lateinit var vm: ReaderViewModel
        compose.runOnUiThread {
            vm = ReaderViewModel(app, Sources(client))
            vm.activeSource.value = "demonic"
            vm.browseFilter.value = BrowseFilter(tags = setOf("1"))
            compose.activity.setContent { MaterialTheme(colorScheme = Night, typography = ReaderTypography) { Discover(vm) } }
            vm.search("fixture")
        }
        try {
            compose.waitUntil(5000) { vm.incompleteBrowseMetadata.value.size == 1 && !vm.loading.value }
            compose.onNodeWithText("Partial results · metadata unavailable for 1 title").assertIsDisplayed()
            compose.onNodeWithText("Good result").assertExists()
            compose.onNodeWithText("Retry metadata").assertHasClickAction().performClick()
            compose.waitUntil(5000) { vm.incompleteBrowseMetadata.value.isEmpty() && vm.results.value.size == 2 }
            assertEquals(2, failedRequests.get())
            assertEquals(2, vm.results.value.map { it.id }.distinct().size)
            compose.runOnUiThread { vm.search("fixture") }
            compose.waitUntil(5000) { vm.incompleteBrowseMetadata.value.size == 1 && !vm.loading.value }
            compose.onNodeWithText("Retry metadata").performClick()
            assertTrue(retryStarted.await(5, TimeUnit.SECONDS))
            compose.runOnUiThread { vm.search("new") }
            compose.waitUntil(5000) { vm.results.value.singleOrNull()?.title == "New result" && !vm.loading.value }
            releaseRetry.countDown()
            compose.waitUntil(5000) { client.dispatcher.runningCallsCount() == 0 }
            compose.waitForIdle()
            assertEquals(listOf("New result"), vm.results.value.map { it.title })
            assertTrue(vm.incompleteBrowseMetadata.value.isEmpty())
        } finally {
            releaseRetry.countDown()
            compose.runOnUiThread { vm.viewModelScope.cancel(); compose.activity.setContent {} }
            client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
        }
    }
}
