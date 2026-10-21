package app.komori

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.komori.ui.ReaderViewModel
import app.komori.ui.UpdateViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MaintenanceSettingsUiTest {
    @Test fun storageReviewCancellationAndAutomaticBackupControlsAreAccessible() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as KomoriApp
        val device = UiDevice.getInstance(instrumentation)
        val before = app.dao.backupProgress()
        val activity = instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        lateinit var vm: ReaderViewModel
        instrumentation.runOnMainSync {
            ViewModelProvider(activity)[UpdateViewModel::class.java].dismiss()
            vm = ViewModelProvider(activity)[ReaderViewModel::class.java]
            vm.reader.value = null; vm.selected.value = null; vm.currentTab.value = 3
        }
        fun clickText(text: String) = (device.wait(Until.findObject(By.text(text)), 8000)
            ?: throw AssertionError("Missing $text")).click()
        try {
            (device.wait(Until.findObject(By.desc("Queue options")), 8000) ?: throw AssertionError("Missing queue menu")).click()
            clickText("Storage & integrity")
            assertTrue(device.wait(Until.hasObject(By.text("Download storage")), 8000))
            assertTrue(device.hasObject(By.text("Scan downloaded files")))
            clickText("Review delete-read chapters")
            assertTrue(device.wait(Until.hasObject(By.text("Delete read downloads?")), 8000))
            clickText("Cancel")
            assertTrue(device.wait(Until.gone(By.text("Delete read downloads?")), 8000))
            assertEquals(before, app.dao.backupProgress())
            device.executeShellCommand("screencap -p /data/local/tmp/komori-download-maintenance.png")
            clickText("Close")
            instrumentation.runOnMainSync { vm.currentTab.value = 4 }
            clickText("App")
            assertTrue(device.wait(Until.hasObject(By.text("Export backup")), 8000))
            repeat(5) {
                if (!device.hasObject(By.text("Keep recent backups"))) {
                    device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, .6f)
                    device.waitForIdle()
                }
            }
            assertTrue("Automatic retention controls should be reachable", device.hasObject(By.text("Keep recent backups")))
            assertTrue(device.hasObject(By.text("Daily")))
            assertTrue(device.hasObject(By.text("Weekly")))
            device.executeShellCommand("screencap -p /data/local/tmp/komori-scheduled-backup-settings.png")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
