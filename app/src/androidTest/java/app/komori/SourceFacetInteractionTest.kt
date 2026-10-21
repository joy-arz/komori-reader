package app.komori

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.komori.data.*
import app.komori.ui.ReaderViewModel
import app.komori.ui.UpdateViewModel
import org.junit.Assert.*
import org.junit.Test

class SourceFacetInteractionTest {
    @Test fun metadataGroupsOpenCanonicalParodyAndBackRestoresTheTitle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as KomoriApp
        val device = UiDevice.getInstance(instrumentation)
        val activity = instrumentation.startActivitySync(
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        lateinit var vm: ReaderViewModel
        val book = Manga(sourceId("zap", "/gallery/1/"), "Metadata fixture", "", "",
            tagsJson = """[{"id":"/parody/space-saga/","name":"Space Saga","type":"parody"},{"id":"/language/english/","name":"English","type":"language"},{"id":"/category/manga/","name":"Manga","type":"category"}]""")
        instrumentation.runOnMainSync {
            vm = ViewModelProvider(activity)[ReaderViewModel::class.java]
            ViewModelProvider(activity)[UpdateViewModel::class.java].dismiss()
            vm.activeSource.value = "zap"
            vm.selected.value = book
        }
        try {
            assertTrue(device.wait(Until.hasObject(By.text("Parodies")), 8000))
            assertTrue(device.hasObject(By.text("Languages")))
            assertTrue(device.hasObject(By.text("Categories")))
            device.findObject(By.text("Space Saga")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Open search")), 5000))
            assertEquals("/parody/space-saga/", vm.browseFilter.value.facetPath)
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("Title details")), 5000))
            assertEquals(book.id, vm.selected.value?.id)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun languageAndCategoryChoicesApplyResetAndRestoreThroughHistory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        lateinit var vm: ReaderViewModel
        instrumentation.runOnMainSync {
            vm = ViewModelProvider(activity)[ReaderViewModel::class.java]
            ViewModelProvider(activity)[UpdateViewModel::class.java].dismiss()
            vm.currentTab.value = 1
            vm.activeSource.value = "nhentai"
            vm.results.value = listOf(Manga(sourceId("nhentai", "/g/1/"), "Filter fixture", "", ""))
        }
        try {
            assertTrue(device.wait(Until.hasObject(By.desc("Filters and sorting")), 8000))
            device.findObject(By.desc("Filters and sorting")).click()
            assertTrue(device.wait(Until.hasObject(By.text("All languages")), 5000))
            device.findObject(By.text("English")).click()
            device.findObject(By.text("Manga")).click()
            device.findObject(By.text("Apply")).click()
            assertTrue(device.wait(Until.gone(By.text("Browse filters")), 5000))
            device.waitForIdle()
            instrumentation.waitForIdleSync()
            assertEquals("english", vm.browseFilter.value.language)
            assertEquals("manga", vm.browseFilter.value.category)
            device.findObject(By.desc("Filters and sorting")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Reset")), 5000))
            device.findObject(By.text("Reset")).click()
            assertTrue(device.wait(Until.gone(By.text("Browse filters")), 5000))
            device.waitForIdle()
            instrumentation.waitForIdleSync()
            assertTrue(vm.browseFilter.value.language.isEmpty())
            assertTrue(vm.browseFilter.value.category.isEmpty())
            instrumentation.runOnMainSync {
                vm.back()
                assertEquals("english", vm.browseFilter.value.language)
                assertEquals("manga", vm.browseFilter.value.category)
                vm.submitSearch("forest")
                assertEquals("english", vm.browseFilter.value.language)
                assertEquals("manga", vm.browseFilter.value.category)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
