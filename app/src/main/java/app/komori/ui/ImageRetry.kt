package app.komori.ui

import androidx.compose.runtime.*
import kotlinx.coroutines.delay

/** Visible retries are cancellable; the ViewModel owns the session's network attempt budget. */
@Composable
fun imageRetryCountdown(
    failed: Boolean,
    seconds: Int,
    notBeforeMillis: Long = 0L,
    retry: () -> Unit,
): Int {
    var attempts by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    val latestRetry by rememberUpdatedState(retry)
    LaunchedEffect(failed, seconds, notBeforeMillis) {
        countdown = 0
        if (!failed || seconds <= 0 || attempts >= 3) return@LaunchedEffect
        val deadline = maxOf(System.currentTimeMillis() + seconds * 1000L, notBeforeMillis)
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            countdown = ((remaining / 1000) + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            delay(minOf(1000, remaining))
        }
        countdown = 0
        attempts++
        latestRetry()
    }
    return countdown
}
