package app.komori

import android.graphics.drawable.Animatable
import android.widget.ImageView
import app.komori.data.*
import coil.imageLoader
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.ui.AnimatedReaderView
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AnimatedReaderGestureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun pagedAnimationDecodeFailureOffersManualRetryWithoutAutomaticLoop() = kotlinx.coroutines.runBlocking<Unit> {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val application = instrument.targetContext.applicationContext as KomoriApp
        val previous = application.settings.flow.first()
        application.settings.set("webtoon", false)
        val file = File.createTempFile("animation-failure-", ".img", application.cacheDir)
        instrument.context.assets.open("images/animated.gif").use { input -> file.outputStream().use { input.copyTo(it) } }
        val originalLoader = application.imageLoader
        val failingLoader = coil.ImageLoader.Builder(application).components {
            add(object : coil.intercept.Interceptor {
                override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult =
                    coil.request.ErrorResult(null, chain.request, java.io.IOException("Fixture decoder failure"))
            })
        }.build()
        var vm: app.komori.ui.ReaderViewModel? = null
        try {
            coil.Coil.setImageLoader(failingLoader)
            compose.runOnUiThread {
                val model = app.komori.ui.ReaderViewModel(application) { _, _, _ -> app.komori.data.BrowsePage(emptyList(), false) }
                vm = model
                model.readerFile.value = file; model.readerReady.value = true; model.pageCount.value = 1
                val chapter = app.komori.domain.Release("animation-failure", "animation-title", "1", null, "", "en", emptyList(), "", 1)
                compose.activity.setContent { MaterialTheme { app.komori.ui.ReaderScreen(model, chapter) } }
            }
            compose.waitUntil(5000) { vm?.readerError?.value?.contains("animation") == true }
            assertFalse(requireNotNull(vm).readerRetryAllowed.value)
            assertFalse(requireNotNull(vm).readerSourceAction.value)
            assertEquals(0L, requireNotNull(vm).readerRetryAt.value)
            compose.onNodeWithText("Retry").assertExists()
            compose.onAllNodesWithText("Retrying this page", substring = true).assertCountEquals(0)
        } finally {
            compose.runOnUiThread { compose.activity.setContent {}; vm?.viewModelScope?.cancel() }
            compose.waitForIdle()
            coil.Coil.setImageLoader(originalLoader); failingLoader.shutdown()
            application.settings.set("webtoon", previous.webtoon)
            file.delete()
        }
    }

    @Test fun tiledPageTapFiresOnceAndDoubleTapDoesNotOpenControls() {
        val taps = AtomicInteger()
        val bitmap = android.graphics.Bitmap.createBitmap(400, 800, android.graphics.Bitmap.Config.RGB_565)
        var view: app.komori.ui.ReaderImageView? = null
        try {
            compose.runOnUiThread { compose.activity.setContent { MaterialTheme {
                AndroidView(factory = { context -> app.komori.ui.ReaderImageView(context).also {
                    view = it
                    it.onReaderTap = { _, _ -> taps.incrementAndGet() }
                    it.setMaxScale(5f)
                    it.setImage(com.davemorrissey.labs.subscaleview.ImageSource.bitmap(bitmap))
                } }, modifier = Modifier.fillMaxSize().testTag("tiled-reader"), onReset = null, onRelease = { it.recycle() })
            } } }
            compose.waitUntil(5000) {
                var ready = false
                compose.runOnUiThread { ready = view?.isReady == true }
                ready
            }
            val image = compose.onNodeWithTag("tiled-reader")
            image.performTouchInput { click(center) }
            compose.waitUntil(3000) { taps.get() > 0 }
            compose.runOnIdle { assertEquals(1, taps.get()) }
            image.performTouchInput { doubleClick(center) }
            compose.runOnIdle { assertEquals("Zoom must not open controls", 1, taps.get()) }
        } finally {
            compose.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            // The view owns this bitmap and recycles it from onRelease after detachment.
        }
    }

    @Test fun offlineAnimationRunsAndPreservesTapSwipeZoomAndAccessibleClick() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val file = File.createTempFile("reader-animation-", ".img", instrument.targetContext.cacheDir)
        instrument.context.assets.open("images/animated.gif").use { input -> file.outputStream().use { input.copyTo(it) } }
        val turns = AtomicInteger(); val taps = AtomicInteger()
        var view: AnimatedReaderView? = null
        try {
            compose.runOnUiThread { compose.activity.setContent { MaterialTheme {
                AndroidView(factory = { context -> AnimatedReaderView(context).also {
                    view = it
                    it.onReaderSwipe = { turns.incrementAndGet() }
                    it.onReaderTap = { _, _ -> taps.incrementAndGet() }
                    it.load(file) { fail("Fixture GIF must decode") }
                } }, modifier = Modifier.fillMaxSize().testTag("animated-reader"), onReset = null, onRelease = { it.release() })
            } } }
            compose.waitUntil(5000) {
                var running = false
                compose.runOnUiThread { running = (((view?.getChildAt(0) as? ImageView)?.drawable) as? Animatable)?.isRunning == true }
                running
            }
            val image = compose.onNodeWithTag("animated-reader")
            image.performTouchInput { swipeLeft() }
            assertEquals(1, turns.get()); assertEquals(0, taps.get())
            image.performTouchInput { click(center) }
            compose.waitUntil(3000) { taps.get() == 1 }
            image.performTouchInput { doubleClick(center) }
            image.performTouchInput { swipeLeft() }
            assertEquals("A zoomed pan must not turn the page", 1, turns.get())
            assertEquals("Double tap must not open controls", 1, taps.get())
            compose.runOnUiThread { assertTrue(requireNotNull(view).performClick()) }
            assertEquals(2, taps.get())
        } finally {
            compose.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            file.delete()
        }
    }
}
