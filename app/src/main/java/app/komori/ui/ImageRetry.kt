package app.komori.ui

import androidx.compose.runtime.*
import kotlinx.coroutines.delay

/**
 * Visible-image retry only. Three attempts per visible item, canceled when the image leaves
 * composition.
 */
@Composable
fun imageRetryCountdown(failed: Boolean, seconds: Int, retry: () -> Unit): Int {
    var attempts by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    val latestRetry by rememberUpdatedState(retry)
    LaunchedEffect(failed, seconds) {
        countdown = 0
        if (!failed || seconds <= 0 || attempts >= 3) return@LaunchedEffect
        for (remaining in seconds downTo 1) {
            countdown = remaining
            delay(1000)
        }
        countdown = 0
        attempts++
        latestRetry()
    }
    return countdown
}
