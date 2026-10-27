package app.komori

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.komori.ui.AuthorFiltersDialog
import app.komori.ui.AuthorWorkFilter
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class AuthorFilterAccessibilityTest {
    @Test fun filterActionsRemainReachableInLandscapeWithLargeRtlText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val result = AtomicReference<AuthorWorkFilter>()
        var activity: MainActivity? = null
        try {
            device.setOrientationLeft()
            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            instrumentation.runOnMainSync {
                activity!!.setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                        MaterialTheme { AuthorFiltersDialog(AuthorWorkFilter(query = "active"), listOf("zap", "nhentai"), { result.set(it) }, {}) }
                    }
                }
            }
            assertTrue(device.wait(Until.hasObject(By.text("Apply")), 5000))
            for (label in listOf("Apply", "Cancel", "Reset filters")) {
                val button = device.findObject(By.text(label))
                assertNotNull("Missing $label in landscape", button)
                assertTrue("Hidden $label", button.visibleBounds.width() > 0 && button.visibleBounds.height() > 0)
            }
            device.findObject(By.text("Reset filters")).click()
            device.waitForIdle()
            device.findObject(By.text("Apply")).click()
            instrumentation.waitForIdleSync()
            assertEquals(AuthorWorkFilter(), result.get())
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "authors-filters-landscape-rtl.png"))
        } finally { instrumentation.runOnMainSync { activity?.finish() }; device.setOrientationNatural(); device.unfreezeRotation() }
    }

    @Test fun filterFieldsAndApplyRemainAccessibleWithLargeTextAndRtl() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val result = AtomicReference<AuthorWorkFilter>()
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                        MaterialTheme { AuthorFiltersDialog(AuthorWorkFilter(), listOf("zap", "nhentai"), { result.set(it) }, {}) }
                    }
                }
            }
            instrumentation.waitForIdleSync()
            device.waitForIdle()
            assertTrue(device.wait(Until.hasObject(By.text("Filter author works")), 10000))
            val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
            assertNotNull(field); field!!.text = "forest"
            device.waitForIdle()
            val apply = device.wait(Until.findObject(By.text("Apply")), 5000)
            assertNotNull(apply)
            assertTrue(apply!!.visibleBounds.width() > 0 && apply.visibleBounds.height() > 0)
            apply.click()
            val deadline = System.currentTimeMillis() + 5000
            while (result.get() == null && System.currentTimeMillis() < deadline) Thread.sleep(25)
            assertEquals("forest", result.get()!!.query)
        } catch (error: Throwable) {
            device.executeShellCommand("screencap -p /data/local/tmp/komori-author-filter-failure.png")
            device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null), "author-filter-failure.xml"))
            throw error
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
