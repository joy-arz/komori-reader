package app.komori

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.komori.data.Manga
import app.komori.ui.ReaderViewModel
import app.komori.ui.UpdateViewModel
import org.junit.Assert.*
import org.junit.Test

/** Real injected touches: catches an invisible overlay or a drag handler swallowing taps. */
class BrowseInteractionTest {
    @Test
    fun navigationSearchCoverTapsAndSourceDragsRemainInteractive() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val activity =
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ) as MainActivity
        lateinit var vm: ReaderViewModel
        instrumentation.runOnMainSync {
            vm = ViewModelProvider(activity)[ReaderViewModel::class.java]
            ViewModelProvider(activity)[UpdateViewModel::class.java].dismiss()
            vm.activeSource.value = "demonic"
            vm.results.value =
                (1..40).map {
                    Manga("demonic:touch-$it", "Touch title $it", "Interaction fixture", "")
                }
        }
        fun tap(label: String) {
            assertTrue(
                "Missing control: $label",
                device.wait(Until.hasObject(By.pkg(instrumentation.targetContext.packageName).desc(label)), 8_000),
            )
            device.findObject(By.pkg(instrumentation.targetContext.packageName).desc(label)).click()
            device.waitForIdle()
        }
        try {
            tap("Downloads")
            assertTrue(device.wait(Until.hasObject(By.text("Downloads")), 5_000))
            tap("Authors")
            assertTrue(device.wait(Until.hasObject(By.text("Following")), 5_000))
            tap("Settings")
            assertTrue(device.wait(Until.hasObject(By.text("Continuous chapters")), 5_000))
            tap("Library")
            tap("Search library")
            assertTrue(device.wait(Until.hasObject(By.desc("Close library search")), 5_000))
            tap("Close library search")
            tap("Discover")
            assertFalse(device.hasObject(By.text("Select")))
            device.takeScreenshot(
                java.io.File(activity.getExternalFilesDir(null), "discover-compact.png")
            )
            device.findObject(By.desc("Touch title 1")).longClick()
            assertTrue(device.wait(Until.hasObject(By.text("1 selected")), 5000))
            device.findObject(By.text("Done")).click()
            assertFalse(device.hasObject(By.text("Select")))
            tap("Open search")
            assertTrue(device.wait(Until.hasObject(By.desc("Close search")), 5_000))
            tap("Close search")
            // A cover click must reach its child, not the parent's horizontal drag recognizer.
            tap("Touch title 1")
            assertTrue(device.wait(Until.hasObject(By.text("Title details")), 5_000))
            assertEquals("demonic:touch-1", vm.selected.value?.id)
            tap("Back")
            val width = device.displayWidth
            val height = device.displayHeight
            device.swipe(width / 2, height * 3 / 4, width / 2, height / 2, 18)
            device.waitForIdle()
            assertEquals(
                "Vertical scrolling must not change the source",
                "demonic",
                vm.activeSource.value,
            )
            device.swipe(width * 4 / 5, height / 2, width / 5, height / 2, 18)
            device.waitForIdle()
            val deadline = android.os.SystemClock.uptimeMillis() + 5000
            while (vm.activeSource.value != "weeb" && android.os.SystemClock.uptimeMillis() < deadline) {
                android.os.SystemClock.sleep(50)
            }
            assertEquals("weeb", vm.activeSource.value)
            // Navigation must remain usable while a source request is in flight.
            tap("Library")
            assertTrue(device.wait(Until.hasObject(By.text("Your library")), 5_000))
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
