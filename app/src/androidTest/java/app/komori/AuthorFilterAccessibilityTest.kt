package app.komori

import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import app.komori.ui.AuthorFiltersDialog
import app.komori.ui.AuthorWorkFilter
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Test

class AuthorFilterAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private var dismissFilter: () -> Unit = {}
    private var previousKeyboardSetting: Int? = null
    private var previousIdleTimeout = 10000L
    private val keyboardPackage get() = device.executeShellCommand("settings get secure default_input_method").trim().substringBefore('/')

    @Before fun showOnScreenKeyboard() {
        previousIdleTimeout = Configurator.getInstance().getWaitForIdleTimeout()
        Configurator.getInstance().setWaitForIdleTimeout(0)
        previousKeyboardSetting = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim().toIntOrNull()
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
    }

    @After fun restoreKeyboardSetting() {
        try {
            compose.runOnUiThread { dismissFilter() }
            compose.waitForIdle()
        } finally {
            try {
                val value = previousKeyboardSetting
                device.executeShellCommand(if (value == null) "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $value")
            } finally {
                Configurator.getInstance().setWaitForIdleTimeout(previousIdleTimeout)
            }
        }
    }

    private fun requireKeyboardAndActions() {
        val keyboard = keyboardPackage
        compose.waitUntil(5000) {
            device.hasObject(By.pkg(keyboard)) && listOf("Apply", "Cancel", "Reset filters").all { label ->
                device.findObject(By.text(label))?.visibleBounds?.let { it.width() > 0 && it.height() > 0 } == true
            }
        }
    }

    private fun showFilter(initial: AuthorWorkFilter, result: AtomicReference<AuthorWorkFilter>) {
        val activity = compose.activity
        val visible = mutableStateOf(true)
        dismissFilter = { visible.value = false }
        compose.runOnUiThread {
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, 1.5f),
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                ) {
                    MaterialTheme {
                        if (visible.value) AuthorFiltersDialog(initial, listOf("zap", "nhentai"), {
                            result.set(it)
                            visible.value = false
                        }, { visible.value = false })
                    }
                }
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Apply").fetchSemanticsNodes().size == 1 }
        compose.waitForIdle()
    }

    private fun captureFailure(error: Throwable) {
        android.util.Log.e("KomoriAuthorFilter", "Filter acceptance failed", error)
        runCatching {
            val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true)
            repeat(roots.fetchSemanticsNodes().size) { roots[it].printToLog("KomoriAuthorFilter") }
        }
        // A failed IME/accessibility query must not block the test runner's teardown.
        device.executeShellCommand("sh -c 'screencap -p /data/local/tmp/komori-author-filter-failure.png >/dev/null 2>&1 </dev/null &'")
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
            compose.onNodeWithText("Title or creator").performClick()
            requireKeyboardAndActions()
            for (label in listOf("Apply", "Cancel", "Reset filters")) {
                compose.onNodeWithText(label).assertIsDisplayed().assertHasClickAction()
                val button = device.findObject(By.text(label))
                assertNotNull("Missing $label in landscape", button)
                assertTrue("Hidden $label", button.visibleBounds.width() > 0 && button.visibleBounds.height() > 0)
            }
            device.takeScreenshot(java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "authors-filters-landscape-rtl.png"))
            device.findObject(By.text("Reset filters")).click()
            compose.waitForIdle()
            device.findObject(By.text("Apply")).click()
            compose.waitUntil(5000) { result.get() != null }
            assertEquals(AuthorWorkFilter(), result.get())
        } catch (error: Throwable) {
            captureFailure(error)
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
            compose.onNodeWithText("Title or creator").performClick()
            compose.onNodeWithText("Title or creator").performTextInput("forest")
            requireKeyboardAndActions()
            compose.onNodeWithText("Apply").assertIsDisplayed().assertHasClickAction()
            val apply = device.findObject(By.text("Apply"))
            assertNotNull(apply)
            assertTrue(apply!!.visibleBounds.width() > 0 && apply.visibleBounds.height() > 0)
            apply.click()
            compose.waitUntil(5000) { result.get() != null }
            assertEquals("forest", result.get()!!.query)
        } catch (error: Throwable) {
            captureFailure(error)
            throw error
        } finally {
            device.unfreezeRotation()
        }
    }
}
