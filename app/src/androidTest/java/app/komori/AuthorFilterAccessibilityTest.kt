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
import androidx.compose.ui.unit.dp
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
            // Finish the owned Activity before ActivityScenario launches its empty teardown Activity.
            val activity = compose.activity
            compose.runOnUiThread { activity.finish() }
        } finally {
            try {
                val value = previousKeyboardSetting
                device.executeShellCommand(if (value == null) "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $value")
            } finally {
                Configurator.getInstance().setWaitForIdleTimeout(previousIdleTimeout)
            }
        }
    }

    private fun hasKeyboardOnboarding(node: android.view.accessibility.AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 30) return false
        if (node.text?.toString() == "Tap to Google Search and more") return true
        return (0 until node.childCount.coerceAtMost(100)).any { hasKeyboardOnboarding(node.getChild(it), depth + 1) }
    }

    private fun requireKeyboardAndActions() {
        val labels = listOf("Apply", "Cancel", "Reset filters")
        var previousBounds: List<android.graphics.Rect>? = null
        var stableSince = 0L
        var onboardingHandled = false
        compose.waitUntil(5000) {
            // Gboard reports its painted first-run bubble as invisible on API 26, so By.text omits it.
            val keyboardWindows = instrumentation.uiAutomation.windows.filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            val onboarding = !onboardingHandled && keyboardWindows.any { hasKeyboardOnboarding(it.root) }
            if (onboarding) {
                onboardingHandled = true
                android.util.Log.i("KomoriAuthorFilter", "Preparing first-run Gboard window before native action checks")
                device.pressBack()
                compose.waitUntil(5000) {
                    instrumentation.uiAutomation.windows.none { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                }
                compose.onNodeWithText("Title or creator").performClick()
                previousBounds = null
                return@waitUntil false
            }
            val keyboardShown = instrumentation.uiAutomation.windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            val bounds = try {
                labels.map { label -> device.findObject(By.pkg(instrumentation.targetContext.packageName).text(label))?.visibleBounds }
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                emptyList()
            }
            if (!keyboardShown || bounds.size != labels.size || bounds.any { it == null || it.isEmpty }) {
                previousBounds = null
                return@waitUntil false
            }
            val current = bounds.map { android.graphics.Rect(it!!) }
            val now = android.os.SystemClock.uptimeMillis()
            if (current != previousBounds) {
                previousBounds = current
                stableSince = now
                false
            } else now - stableSince >= 100L
        }
        compose.onNodeWithText("Title or creator").assertHeightIsAtLeast(48.dp)
        for (label in labels) compose.onNodeWithText(label).assertIsDisplayed().assertHasClickAction()
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
                        if (visible.value) {
                            AuthorFiltersDialog(initial, listOf("zap", "nhentai"), {
                                result.set(it)
                                visible.value = false
                            }, { visible.value = false })
                        }
                    }
                }
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Apply").fetchSemanticsNodes().size == 1 }
        compose.waitForIdle()
    }

    private fun exportCapture(file: java.io.File, extension: String) {
        val space = "\${IFS}"
        val command = "run-as${space}${instrumentation.targetContext.packageName}${space}cat${space}${file.absolutePath}>/data/local/tmp/komori-author-filter-failure.$extension"
        val output = device.executeShellCommand("sh -c $command")
        android.util.Log.i("KomoriAuthorFilter", "Capture $extension: $output")
    }

    private fun captureFailure(error: Throwable) {
        android.util.Log.e("KomoriAuthorFilter", "Filter acceptance failed", error)
        runCatching {
            val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true)
            repeat(roots.fetchSemanticsNodes().size) { roots[it].printToLog("KomoriAuthorFilter") }
        }
        for (label in listOf("Apply", "Cancel", "Reset filters")) {
            runCatching { compose.onNodeWithText(label).printToLog("KomoriAuthorAction") }
        }
        val windows = org.json.JSONArray()
        fun node(info: android.view.accessibility.AccessibilityNodeInfo, depth: Int): org.json.JSONObject {
            val bounds = android.graphics.Rect()
            info.getBoundsInScreen(bounds)
            val value = org.json.JSONObject().put("text", info.text?.toString()).put("description", info.contentDescription?.toString())
                .put("bounds", bounds.toShortString()).put("visible", info.isVisibleToUser).put("class", info.className?.toString())
            val children = org.json.JSONArray()
            if (depth < 30) for (index in 0 until info.childCount.coerceAtMost(100)) info.getChild(index)?.let { children.put(node(it, depth + 1)) }
            return value.put("children", children)
        }
        for (window in instrumentation.uiAutomation.windows) {
            val root = window.root
            windows.put(org.json.JSONObject().put("type", window.type).put("root", root?.let { node(it, 0) }))
        }
        val hierarchy = java.io.File(instrumentation.targetContext.cacheDir, "author-filter-failure.json")
        hierarchy.writeText(windows.toString(2))
        exportCapture(hierarchy, "json")
        // Capture directly; UiDevice.dumpWindowHierarchy waits for accessibility idle and can stall with an animated IME.
        val screenshot = java.io.File(instrumentation.targetContext.cacheDir, "author-filter-failure.png")
        if (device.takeScreenshot(screenshot)) {
            exportCapture(screenshot, "png")
        }
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
