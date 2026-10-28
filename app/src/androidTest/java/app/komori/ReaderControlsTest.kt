package app.komori

import androidx.activity.compose.setContent
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import app.komori.ui.readerCenterTap
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReaderControlsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun settleTapDetection() {
        compose.mainClock.advanceTimeBy(android.view.ViewConfiguration.getDoubleTapTimeout().toLong() + 1)
        compose.waitForIdle()
    }

    @Test fun viewportCenterTogglesButScrollingOutsideTapsPinchingAndDoubleTapsDoNot() {
        val toggles = AtomicInteger()
        val firstVisible = AtomicInteger()
        val activity = compose.activity
        compose.runOnUiThread {
            activity.setContent {
                val list = rememberLazyListState()
                val visibleIndex = list.firstVisibleItemIndex
                SideEffect { firstVisible.set(visibleIndex) }
                Box(Modifier.fillMaxSize().testTag("viewport").readerCenterTap { toggles.incrementAndGet() }) {
                    LazyColumn(state = list) {
                        items(50) { Text("Page $it", Modifier.fillMaxWidth().height(150.dp)) }
                    }
                }
            }
        }
        val viewport = compose.onNodeWithTag("viewport")
        viewport.performTouchInput { click(Offset(width * .5f, height * .15f)) }
        settleTapDetection()
        assertEquals(0, toggles.get())
        viewport.performTouchInput { click(Offset(width * .15f, height * .5f)) }
        settleTapDetection()
        assertEquals(0, toggles.get())
        viewport.performTouchInput { click(center) }
        settleTapDetection()
        compose.waitUntil(3000) { toggles.get() == 1 }
        viewport.performTouchInput { swipe(Offset(center.x, height * .8f), center) }
        settleTapDetection()
        compose.waitUntil(3000) { firstVisible.get() > 0 }
        assertEquals("Scrolling must not toggle controls", 1, toggles.get())
        viewport.performTouchInput { doubleClick(center) }
        settleTapDetection()
        assertEquals("Double taps must not toggle controls", 1, toggles.get())
        viewport.performTouchInput {
            pinch(Offset(width * .45f, height * .45f), Offset(width * .55f, height * .55f),
                Offset(width * .25f, height * .25f), Offset(width * .75f, height * .75f))
        }
        settleTapDetection()
        assertEquals("Pinching must not toggle controls", 1, toggles.get())
        viewport.performTouchInput { click(center) }
        settleTapDetection()
        compose.waitUntil(3000) { toggles.get() == 2 }
    }

    @Test fun readerStartsHiddenAndControlsRemainReachableWhileLoading() = kotlinx.coroutines.runBlocking<Unit> {
        val application = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KomoriApp
        val previousWebtoon = application.settings.flow.first().webtoon
        application.settings.set("webtoon", false)
        val vm = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            app.komori.ui.ReaderViewModel(application) { _, _, _ -> app.komori.data.BrowsePage(emptyList(), false) }
        }
        try {
            val activity = compose.activity
            val chapter = app.komori.domain.Release("controls-loading", "controls-title", "1", null, "", "en", emptyList(), "", 3)
            compose.runOnUiThread { activity.setContent { MaterialTheme { app.komori.ui.ReaderScreen(vm, chapter) } } }
            compose.onNodeWithContentDescription("Close reader").assertDoesNotExist()
            val area = compose.onNodeWithContentDescription("Reading area")
            area.performTouchInput { click(Offset(width * .5f, height * .15f)) }
            settleTapDetection()
            compose.onNodeWithContentDescription("Close reader").assertDoesNotExist()
            area.performTouchInput { click(center) }
            settleTapDetection()
            compose.waitUntil(3000) { compose.onAllNodesWithContentDescription("Close reader").fetchSemanticsNodes().size == 1 }
            area.performTouchInput { click(center) }
            settleTapDetection()
            compose.waitUntil(3000) { compose.onAllNodesWithContentDescription("Close reader").fetchSemanticsNodes().isEmpty() }
            val showControls = area.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
                .single { it.label == "Show reader controls" }
            compose.runOnIdle { assertTrue(showControls.action()) }
            compose.onNodeWithContentDescription("Close reader").assertIsDisplayed()
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
            vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.join()
            application.settings.set("webtoon", previousWebtoon)
        }
    }

    @Test fun retryButtonsConsumeTheirOwnTapsInsideTheCenterRegion() {
        val retries = AtomicInteger()
        val toggles = AtomicInteger()
        val activity = compose.activity
        compose.runOnUiThread {
            activity.setContent {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().readerCenterTap { toggles.incrementAndGet() }, contentAlignment = Alignment.Center) {
                        Button(onClick = { retries.incrementAndGet() }) { Text("Retry page") }
                    }
                }
            }
        }
        compose.onNodeWithText("Retry page").performTouchInput { click() }
        settleTapDetection()
        assertEquals(1, retries.get())
        assertEquals(0, toggles.get())
    }
}
