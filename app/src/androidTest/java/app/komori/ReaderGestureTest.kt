package app.komori

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.ui.ReaderImageView
import com.davemorrissey.labs.subscaleview.ImageSource
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class ReaderGestureTest {
    @Test
    fun pagedSwipesDoNotTurnWhileZoomed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity =
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ) as Activity
        instrumentation.runOnMainSync {
            val image = ReaderImageView(activity).apply { setMaxScale(5f) }
            val bitmap = Bitmap.createBitmap(800, 1600, Bitmap.Config.RGB_565)
            val output = Bitmap.createBitmap(1080, 1800, Bitmap.Config.RGB_565)
            val canvas = Canvas(output)
            var turns = 0
            image.onReaderSwipe = { turns++ }
            image.setImage(ImageSource.bitmap(bitmap))
            image.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY),
            )
            image.layout(0, 0, 1080, 1800)
            image.draw(canvas)
            assertTrue(image.isReady)
            fun swipe() {
                val start = SystemClock.uptimeMillis()
                listOf(
                        MotionEvent.ACTION_DOWN to 200f,
                        MotionEvent.ACTION_MOVE to 550f,
                        MotionEvent.ACTION_UP to 900f,
                    )
                    .forEachIndexed { index, (action, x) ->
                        val event =
                            MotionEvent.obtain(start, start + index * 100L, action, x, 900f, 0)
                        image.onTouchEvent(event)
                        event.recycle()
                    }
            }
            swipe()
            assertEquals(1, turns)
            image.setScaleAndCenter(image.minScale * 2, PointF(400f, 800f))
            image.draw(canvas)
            assertTrue(image.scale > image.minScale * 1.5f)
            swipe()
            assertEquals("A zoomed pan must not navigate", 1, turns)
            image.recycle()
            output.recycle()
            activity.finish()
        }
    }

    @Test
    fun pagedLongPressRequestsSaveOnlyAtReadingScale() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity =
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ) as Activity
        lateinit var image: ReaderImageView
        val saves = AtomicInteger()
        val taps = AtomicInteger()
        var downAt = 0L
        instrumentation.runOnMainSync {
            image = ReaderImageView(activity).apply { setMaxScale(5f) }
            val bitmap = Bitmap.createBitmap(800, 1600, Bitmap.Config.RGB_565)
            val output = Bitmap.createBitmap(1080, 1800, Bitmap.Config.RGB_565)
            val canvas = Canvas(output)
            image.onReaderLongPress = { x, y ->
                saves.incrementAndGet()
                assertTrue("Long press must map inside the image", x in 0f..1f && y in 0f..1f)
            }
            image.onReaderTap = { _, _ -> taps.incrementAndGet() }
            image.setImage(ImageSource.bitmap(bitmap))
            image.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY),
            )
            image.layout(0, 0, 1080, 1800)
            image.draw(canvas)
            assertTrue(image.isReady)
            downAt = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(downAt, downAt, MotionEvent.ACTION_DOWN, 540f, 900f, 0)
            image.onTouchEvent(down)
            down.recycle()
            output.recycle()
        }
        val longPressDeadline = SystemClock.uptimeMillis() + 3000
        while (saves.get() == 0 && SystemClock.uptimeMillis() < longPressDeadline) Thread.sleep(50)
        instrumentation.runOnMainSync {
            val upAt = SystemClock.uptimeMillis()
            val up = MotionEvent.obtain(downAt, upAt, MotionEvent.ACTION_UP, 540f, 900f, 0)
            image.onTouchEvent(up)
            up.recycle()
        }
        instrumentation.waitForIdleSync()
        assertEquals("Long press must offer one save", 1, saves.get())
        assertEquals("Long press must not open controls", 0, taps.get())
        instrumentation.runOnMainSync {
            image.setScaleAndCenter(image.minScale * 2f, PointF(540f, 900f))
            downAt = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(downAt, downAt, MotionEvent.ACTION_DOWN, 540f, 900f, 0)
            image.onTouchEvent(down)
            down.recycle()
        }
        Thread.sleep(700)
        instrumentation.runOnMainSync {
            val upAt = SystemClock.uptimeMillis()
            val up = MotionEvent.obtain(downAt, upAt, MotionEvent.ACTION_UP, 540f, 900f, 0)
            image.onTouchEvent(up)
            up.recycle()
            image.recycle()
            activity.finish()
        }
        instrumentation.waitForIdleSync()
        assertEquals("Zoomed inspection must not trigger save", 1, saves.get())
    }
}
