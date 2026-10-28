package app.komori

import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.komori.ui.BackgroundDownloadContent
import app.komori.ui.BackgroundDownloadStatus
import app.komori.ui.backgroundDownloadIntent
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackgroundDownloadsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun backgroundPermissionRequiresExplicitActionAndReportsRestrictions() {
        val opened = AtomicInteger()
        val states = listOf(
            BackgroundDownloadStatus(true, false) to "Allow background downloads",
            BackgroundDownloadStatus(true, true) to "Open app battery settings",
            BackgroundDownloadStatus(false, false) to "Open app battery settings",
        )
        for ((index, entry) in states.withIndex()) {
            compose.runOnUiThread {
                compose.activity.setContent { MaterialTheme {
                    BackgroundDownloadContent(entry.first) { opened.incrementAndGet() }
                } }
            }
            compose.onNodeWithText(entry.second).assertIsDisplayed()
            assertEquals("Permission must never open automatically", index, opened.get())
            compose.onNodeWithText(entry.second).performClick()
            assertEquals(index + 1, opened.get())
            val intent = backgroundDownloadIntent(compose.activity, entry.first)
            assertEquals(if (index == 0) Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS else Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
            assertEquals("package:${compose.activity.packageName}", intent.data.toString())
        }
    }
}
