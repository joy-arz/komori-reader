package app.komori

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.komori.data.*
import app.komori.ui.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RegularGenreFiltersTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun selectedGenresCanBeRemovedAndClearedAndLoadFailuresExposeRetry() = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        lateinit var vm: ReaderViewModel
        val genres = listOf("Psychological", "Shounen", "Harem").map { SourceTag(it, it) }
        compose.runOnUiThread {
            vm = ReaderViewModel(app)
            vm.activeSource.value = "weeb"
            compose.activity.setContent { MaterialTheme(colorScheme = Night, typography = ReaderTypography) {
                SourceFilters(vm, "weeb", BrowseFilter(tags = setOf("Psychological", "Shounen")), genres) {}
            } }
        }
        try {
            compose.onNodeWithText("2 genres selected").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Remove Psychological").performScrollTo().assertIsDisplayed()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.waitForIdle()
            device.executeShellCommand("screencap -p /data/local/tmp/komori-source-genre-filters.png")
            compose.onNodeWithContentDescription("Remove Psychological").assertHasClickAction().performClick()
            compose.onNodeWithText("1 genre selected").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Clear genres").performScrollTo().performClick()
            compose.onAllNodesWithContentDescription("Remove Shounen").assertCountEquals(0)
            compose.runOnUiThread { vm.tagsError.value = "Source genres temporarily unavailable." }
            compose.onNodeWithText("Retry genres and tags").performScrollTo().assertIsDisplayed().assertHasClickAction()
            assertEquals("Canceling a draft must not leak filtering into browsing", BrowseFilter(), vm.browseFilter.value)
        } finally { compose.runOnUiThread { vm.viewModelScope.cancel(); compose.activity.setContent {} } }
    }
}
