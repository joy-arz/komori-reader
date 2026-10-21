package app.komori

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.komori.data.Manga
import app.komori.data.sourceId
import app.komori.ui.ReaderViewModel
import app.komori.ui.UpdateViewModel
import org.junit.Assert.*
import org.junit.Test

class CoverLanguageInteractionTest {
    @Test
    fun coverBadgesRemainVisibleAndLongPressSelectionStillWorks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val activity =
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ) as MainActivity
        lateinit var vm: ReaderViewModel
        val fixtures =
            listOf("English", "Japanese", "Spanish", "Chinese").mapIndexed { i, name ->
                Manga(
                    sourceId("zap", "/gallery/${900 + i}/"),
                    "$name fixture",
                    "",
                    "",
                    tagsJson =
                        """[{"id":"/language/${name.lowercase()}/","name":"$name","type":"language"}]""",
                )
            }
        instrumentation.runOnMainSync {
            vm = ViewModelProvider(activity)[ReaderViewModel::class.java]
            ViewModelProvider(activity)[UpdateViewModel::class.java].dismiss()
            vm.activeSource.value = "zap"
            vm.loading.value = true
            vm.results.value = fixtures
            vm.currentTab.value = 1
        }
        try {
            assertTrue(device.wait(Until.hasObject(By.desc("Language: English")), 8000))
            assertTrue(device.hasObject(By.text("en")))
            assertTrue(device.hasObject(By.text("jp")))
            assertTrue(device.hasObject(By.text("sp")))
            assertTrue(device.hasObject(By.text("ch")))
            device.executeShellCommand("screencap -p /data/local/tmp/komori-language-grid.png")
            device.findObject(By.text("English fixture")).longClick()
            assertTrue(device.wait(Until.hasObject(By.desc("Select English fixture")), 3000))
            assertTrue(device.hasObject(By.desc("Language: English")))
            device.executeShellCommand("screencap -p /data/local/tmp/komori-language-selection.png")
            instrumentation.runOnMainSync { vm.selected.value = fixtures.first() }
            assertTrue(device.wait(Until.hasObject(By.text("Title details")), 3000))
            assertTrue(device.hasObject(By.desc("Language: English")))
            device.executeShellCommand("screencap -p /data/local/tmp/komori-language-details.png")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
