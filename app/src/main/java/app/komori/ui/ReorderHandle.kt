package app.komori.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Handle-only dragging leaves row navigation, switches and list scrolling independent. */
@Composable
internal fun ReorderHandle(
    label: String,
    rowHeight: Int,
    earlier: Boolean,
    later: Boolean,
    move: (Int) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val bring = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    fun moved(step: Int) {
        move(step)
        scope.launch { withFrameNanos { }; bring.bringIntoView() }
    }
    val currentMove by rememberUpdatedState<(Int) -> Unit>(::moved)
    val canEarlier by rememberUpdatedState(earlier)
    val canLater by rememberUpdatedState(later)
    val height by rememberUpdatedState(rowHeight.coerceAtLeast(1))
    Box(
        Modifier.size(48.dp).bringIntoViewRequester(bring).background(if (dragging) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent, CircleShape).semantics {
            contentDescription = "Reorder $label"
            stateDescription = if (dragging) "Reordering" else "Hold and drag to reorder"
            customActions = buildList {
                if (earlier) add(CustomAccessibilityAction("Move earlier") { currentMove(-1); true })
                if (later) add(CustomAccessibilityAction("Move later") { currentMove(1); true })
            }
        }.pointerInput(label) {
            var distance = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = { distance = 0f; dragging = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                onDragEnd = { distance = 0f; dragging = false },
                onDragCancel = { distance = 0f; dragging = false },
            ) { change, delta ->
                change.consume()
                distance += delta.y
                if (distance >= height && canLater) {
                    distance -= height
                    currentMove(1)
                } else if (distance <= -height && canEarlier) {
                    distance += height
                    currentMove(-1)
                }
            }
        }, contentAlignment = Alignment.Center) {
        Icon(KomoriIcons.DragHandle, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
