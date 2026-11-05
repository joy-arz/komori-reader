package app.komori.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import android.widget.ImageView
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import java.io.File
import kotlin.math.abs

/** A bounded animated drawable with the same reader tap/zoom/swipe contract as tiled pages. */
internal class AnimatedReaderView(context: Context) : FrameLayout(context) {
    private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private var request: Disposable? = null
    private var zoom = 1f
    private var multi = false
    private var startX = 0f
    private var startY = 0f
    private var startedAtMinimum = true
    private var confirmedTap: Pair<Float, Float>? = null
    var onReaderTap: (Float, Float) -> Unit = { _, _ -> }
    var onReaderSwipe: ((Float) -> Unit)? = null
    init { addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = (zoom * detector.scaleFactor).coerceIn(1f, 5f)
            image.scaleX = zoom; image.scaleY = zoom
            if (zoom == 1f) { image.translationX = 0f; image.translationY = 0f }
            return true
        }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!multi) {
                confirmedTap = e.x / width.coerceAtLeast(1) to e.y / height.coerceAtLeast(1)
                performClick()
            }
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            zoom = if (zoom > 1f) 1f else 2f
            image.scaleX = zoom; image.scaleY = zoom
            image.translationX = 0f; image.translationY = 0f
            return true
        }
        override fun onScroll(a: MotionEvent?, b: MotionEvent, dx: Float, dy: Float): Boolean {
            if (zoom <= 1f) return false
            image.translationX = (image.translationX - dx).coerceIn(-width * (zoom - 1) / 2, width * (zoom - 1) / 2)
            image.translationY = (image.translationY - dy).coerceIn(-height * (zoom - 1) / 2, height * (zoom - 1) / 2)
            return true
        }

    })
    fun load(file: File, error: () -> Unit) {
        request?.dispose()
        request = context.imageLoader.enqueue(ImageRequest.Builder(context).data(file)
            .size(1024, 2048).allowHardware(false)
            .target(onSuccess = { drawable: Drawable ->
                image.setImageDrawable(drawable)
                if (windowVisibility == VISIBLE) (drawable as? android.graphics.drawable.Animatable)?.start()
            }, onError = { error() }).build())
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // The framework can call this during View construction, before child initialization.
        val drawable = (getChildAt(0) as? ImageView)?.drawable as? android.graphics.drawable.Animatable
        if (visibility == VISIBLE) drawable?.start() else drawable?.stop()
    }
    fun release() {
        request?.dispose(); request = null
        (image.drawable as? android.graphics.drawable.Animatable)?.stop()
        image.setImageDrawable(null)
    }
    override fun performClick(): Boolean {
        super.performClick()
        val tap = confirmedTap ?: (.5f to .5f)
        confirmedTap = null
        onReaderTap(tap.first, tap.second)
        return true
    }

    // Confirmed single taps invoke performClick from GestureDetector, after double-tap arbitration.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            multi = false; startX = event.x; startY = event.y; startedAtMinimum = zoom <= 1f
        }
        if (event.pointerCount > 1) multi = true
        scale.onTouchEvent(event); gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && startedAtMinimum && zoom <= 1f && !multi) {
            val dx = event.x - startX; val dy = event.y - startY
            if (abs(dx) > width * .15f && abs(dx) > abs(dy) * 1.5f) onReaderSwipe?.invoke(dx)
        }
        parent?.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)
        return true
    }
}
