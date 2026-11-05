package app.komori

import android.graphics.drawable.Animatable
import android.widget.ImageView
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
            if (!bitmap.isRecycled) bitmap.recycle()
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
            file.delete()
        }
    }
}
