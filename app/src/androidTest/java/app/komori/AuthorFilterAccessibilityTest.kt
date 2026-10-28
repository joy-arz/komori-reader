package app.komori

import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import app.komori.ui.AuthorFiltersDialog
import app.komori.ui.AuthorWorkFilter
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AuthorFilterAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)

    private fun showFilter(initial: AuthorWorkFilter, result: AtomicReference<AuthorWorkFilter>) {
        val activity = compose.activity
        compose.runOnUiThread {
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, 1.5f),
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                ) {
                    MaterialTheme {
                        AuthorFiltersDialog(initial, listOf("zap", "nhentai"), { result.set(it) }, {})
                    }
                }
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Apply").fetchSemanticsNodes().size == 1 }
        compose.waitForIdle()
    }

    private fun captureFailure() {
        runCatching {
            val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true)
            repeat(roots.fetchSemanticsNodes().size) { roots[it].printToLog("KomoriAuthorFilter") }
        }
        device.executeShellCommand("screencap -p /data/local/tmp/komori-author-filter-failure.png")
        device.dumpWindowHierarchy(java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "author-filter-failure.xml"))
    }

    @Test fun filterActionsRemainReachableInLandscapeWithLargeRtlText() {
        val result = AtomicReference<AuthorWorkFilter>()
        try {
            device.setOrientationLeft()
            compose.waitUntil(5000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                    device.displayWidth > device.displayHeight
            }
            showFilter(AuthorWorkFilter(query = "active"), result)
            for (label in listOf("Apply", "Cancel", "Reset filters")) {
                compose.onNodeWithText(label).assertIsDisplayed().assertHasClickAction()
                val button = device.findObject(By.text(label))
                assertNotNull("Missing $label in landscape", button)
                assertTrue("Hidden $label", button.visibleBounds.width() > 0 && button.visibleBounds.height() > 0)
            }
            compose.onNodeWithText("Reset filters").performClick()
            compose.onNodeWithText("Apply").performClick()
            compose.waitUntil(5000) { result.get() != null }
            assertEquals(AuthorWorkFilter(), result.get())
            device.takeScreenshot(java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "authors-filters-landscape-rtl.png"))
        } catch (error: Throwable) {
            captureFailure()
            throw error
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    @Test fun filterFieldsAndApplyRemainAccessibleWithLargeTextAndRtl() {
        val result = AtomicReference<AuthorWorkFilter>()
        try {
            device.setOrientationNatural()
            compose.waitUntil(5000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
                    device.displayHeight > device.displayWidth
            }
            showFilter(AuthorWorkFilter(), result)
            compose.onNodeWithText("Filter author works").assertIsDisplayed()
            val field = device.findObject(By.clazz("android.widget.EditText"))
            assertNotNull(field)
            compose.onNodeWithText("Title or creator").performTextInput("forest")
            compose.onNodeWithText("Apply").assertIsDisplayed().assertHasClickAction()
            val apply = device.findObject(By.text("Apply"))
            assertNotNull(apply)
            assertTrue(apply!!.visibleBounds.width() > 0 && apply.visibleBounds.height() > 0)
            compose.onNodeWithText("Apply").performClick()
            compose.waitUntil(5000) { result.get() != null }
            assertEquals("forest", result.get()!!.query)
        } catch (error: Throwable) {
            captureFailure()
            throw error
        } finally {
            device.unfreezeRotation()
        }
    }
}
