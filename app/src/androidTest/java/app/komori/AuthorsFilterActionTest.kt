package app.komori

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.data.*
import app.komori.ui.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AuthorsFilterActionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun onePrimaryFilterPreservesSortStateManagementAndNavigationOnNarrowLargeRtlLayout() = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        val creator = Creator("nhentai:author:filter-action", "Filter fixture", "artist")
        app.dao.putCreator(creator)
        lateinit var vm: ReaderViewModel
        compose.runOnUiThread {
            vm = ReaderViewModel(app) { _, _, _ -> BrowsePage(emptyList(), false) }
            vm.currentTab.value = 2
            compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MaterialTheme { Box(Modifier.width(320.dp).fillMaxHeight()) { KomoriScreen(vm) } }
                }
            }
        }
        val primary = hasContentDescription("Filter author works") and hasClickAction()
        try {
            compose.waitUntil(5000) { compose.onAllNodes(primary).fetchSemanticsNodes().size == 1 }
            compose.onAllNodes(primary).assertCountEquals(1)
            compose.onNode(primary).assertIsDisplayed().assertIsNotSelected().performClick()
            compose.onNodeWithText("Title or creator").performTextInput("forest")
            compose.onNodeWithText("Apply").performClick()
            compose.onNode(primary).assertIsSelected()
            compose.onNodeWithText("Reset filters").assertHasClickAction()
            compose.onNodeWithContentDescription("Sort author works").performClick()
            compose.onNodeWithText("Alphabetical").performClick()
            compose.onAllNodes(primary).assertCountEquals(1)
            compose.onNodeWithText("Manage").performClick()
            compose.onAllNodes(primary).assertCountEquals(0)
            compose.onNodeWithText("Done").performClick()
            compose.onAllNodes(primary).assertCountEquals(1)
            compose.runOnUiThread { vm.currentTab.value = 0 }
            compose.waitForIdle()
            compose.runOnUiThread { vm.currentTab.value = 2 }
            compose.onNode(primary).assertIsSelected()
            compose.onNodeWithText("Reset filters").performClick()
            compose.onNode(primary).assertIsNotSelected()
            assertEquals(BrowseFilter(), vm.browseFilter.value)
            androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .takeScreenshot(java.io.File(app.getExternalFilesDir(null), "authors-filter-after-large-rtl.png"))
        } finally { compose.runOnUiThread { vm.viewModelScope.cancel() }; app.dao.removeCreator(creator.id) }
    }
}
