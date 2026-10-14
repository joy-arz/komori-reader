package app.komori.ui

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlin.math.abs

/** Leaves pinch/double-tap zoom and zoomed panning with the tiled decoder. */
class ReaderImageView(context: Context) : SubsamplingScaleImageView(context) {
    var onReaderTap: (Float) -> Unit = {}
    var onReaderSwipe: ((Float) -> Unit)? = null
    private var multiplePointers = false
    private var startedAtMinimum = false
    private val gestures =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (!multiplePointers) onReaderTap(e.x / width.coerceAtLeast(1))
                    return true
                }

                override fun onFling(
                    first: MotionEvent?,
                    last: MotionEvent,
                    vx: Float,
                    vy: Float,
                ): Boolean {
                    if (first == null || multiplePointers || !startedAtMinimum) return false
                    val dx = last.x - first.x
                    val dy = last.y - first.y
                    if (abs(dx) > width * 0.15f && abs(dx) > abs(dy) * 1.5f) {
                        onReaderSwipe?.invoke(dx)
                        return onReaderSwipe != null
                    }
                    return false
                }
            },
        )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            multiplePointers = false
            startedAtMinimum = isReady && scale <= minScale * 1.02f
        }
        if (event.pointerCount > 1) multiplePointers = true
        gestures.onTouchEvent(event)
        return super.onTouchEvent(event)
    }
}
