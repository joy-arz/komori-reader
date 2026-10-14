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
    private var startX = 0f
    private var startY = 0f
    private val gestures =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (!multiplePointers) onReaderTap(e.x / width.coerceAtLeast(1))
                    return true
                }
            },
        )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            multiplePointers = false
            startedAtMinimum = isReady && scale <= minScale * 1.02f
            startX = event.x
            startY = event.y
        }
        if (event.pointerCount > 1) multiplePointers = true
        gestures.onTouchEvent(event)
        val handled = super.onTouchEvent(event)
        // The image view normally releases its parent at a pan boundary. A paged
        // reader must retain this gesture until UP, even when the image cannot pan.
        if (onReaderSwipe != null && startedAtMinimum && !multiplePointers) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val dx = event.x - startX
                val dy = event.y - startY
                if (abs(dx) > width * 0.15f && abs(dx) > abs(dy) * 1.5f) onReaderSwipe?.invoke(dx)
            }
            if (
                event.actionMasked != MotionEvent.ACTION_UP &&
                    event.actionMasked != MotionEvent.ACTION_CANCEL
            ) {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
        return handled
    }
}
