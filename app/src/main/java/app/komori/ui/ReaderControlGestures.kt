package app.komori.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import app.komori.domain.ReaderTapZones

@Composable
internal fun Modifier.readerCenterTap(viewportBounds: () -> Rect? = { null }, onTap: () -> Unit): Modifier {
    val currentTap by rememberUpdatedState(onTap)
    val currentViewport by rememberUpdatedState(viewportBounds)
    var contentBounds by remember { mutableStateOf<Rect?>(null) }
    return onGloballyPositioned { contentBounds = it.boundsInWindow() }.pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = {},
            onTap = { point ->
                val viewport = currentViewport()
                val content = contentBounds
                val x = if (viewport != null && content != null && viewport.width > 0f)
                    (content.left + point.x - viewport.left) / viewport.width else point.x / size.width.coerceAtLeast(1)
                val y = if (viewport != null && content != null && viewport.height > 0f)
                    (content.top + point.y - viewport.top) / viewport.height else point.y / size.height.coerceAtLeast(1)
                if (ReaderTapZones.isControlTap(x, y)) {
                    currentTap()
                }
            },
        )
    }
}
