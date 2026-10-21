package app.komori

import android.content.Intent
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.komori.data.*
import app.komori.domain.Release
import app.komori.ui.ReaderViewModel
import app.komori.ui.UpdateViewModel
import org.junit.Assert.*
import org.junit.Test

class NavigationHistoryTest {
    @Test
    fun leavingReaderRestoresChapterListPixelOffset() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as KomoriApp
        val device = UiDevice.getInstance(instrumentation)
        val id = "scroll-test-${java.util.UUID.randomUUID()}"
        val releases =
            (1..30).map {
                Release("$id-$it", id, "$it", null, "", "en", emptyList(), "Test", 1, local = true)
            }
        val bitmap =
            android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.RGB_565)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        for (release in releases) {
            chapterDirectory(app, release.id).mkdirs()
            pageFile(app, release.id, 0).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        bitmap.recycle()
        val activity =
            instrumentation.startActivitySync(
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ) as MainActivity
        lateinit var vm: ReaderViewModel
        instrumentation.runOnMainSync {
            vm = ViewModelProvider(activity)[ReaderViewModel::class.java]
            ViewModelProvider(activity)[UpdateViewModel::class.java].dismiss()
            vm.selected.value = Manga(id, "Scroll restoration", "Offline fixture", "")
            vm.chapters.value = releases
        }
        try {
            assertTrue(device.wait(Until.hasObject(By.text("Title details")), 8000))
            repeat(3) {
                device.swipe(
                    device.displayWidth / 2,
                    device.displayHeight * 4 / 5,
                    device.displayWidth / 2,
                    device.displayHeight / 3,
                    30,
                )
            }
            device.waitForIdle()
            val row =
                device.findObjects(By.textStartsWith("Ch. ")).first {
                    it.visibleBounds.height() > 20
                }
            val label = row.text
            val bounds = row.visibleBounds
            row.click()
            val deadline = android.os.SystemClock.uptimeMillis() + 8000
            while (
                !vm.readerReady.value && android.os.SystemClock.uptimeMillis() < deadline
            ) android.os.SystemClock.sleep(50)
            assertTrue(vm.readerReady.value)
            instrumentation.runOnMainSync { vm.closeReader() }
            assertTrue(device.wait(Until.hasObject(By.text(label)), 5000))
            device.waitForIdle()
            assertEquals(bounds, device.findObject(By.text(label)).visibleBounds)
        } finally {
            instrumentation.runOnMainSync { vm.closeReader(); vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancel(); activity.finish() }
            kotlinx.coroutines.runBlocking { vm.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.join() }
            instrumentation.waitForIdleSync()
            releases.forEach { chapterDirectory(app, it.id).deleteRecursively() }
            kotlinx.coroutines.runBlocking {
                app.dao.resetProgress(id)
                app.database.openHelper.writableDatabase.execSQL("DELETE FROM shelf WHERE mangaId = ?", arrayOf(id))
            }
        }
    }

    @Test
    fun backRestoresExactGridOffsetAndSearchFilterHistory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val activity =
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ) as MainActivity
        lateinit var vm: ReaderViewModel
        instrumentation.runOnMainSync {
            vm = ViewModelProvider(activity)[ReaderViewModel::class.java]
            ViewModelProvider(activity)[UpdateViewModel::class.java].dismiss()
            vm.currentTab.value = 1
            vm.activeSource.value = "demonic"
            vm.results.value =
                (1..80).map { Manga("demonic:history-$it", "History title $it", "Fixture", "") }
        }
        try {
            assertTrue(device.wait(Until.hasObject(By.desc("History title 1")), 8000))
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 3,
                30,
            )
            device.waitForIdle()
            val cover =
                device.findObjects(By.descStartsWith("History title ")).first {
                    it.visibleBounds.height() > 200
                }
            val description = cover.contentDescription
            val bounds = cover.visibleBounds
            cover.click()
            assertTrue(device.wait(Until.hasObject(By.text("Title details")), 5000))
            device.findObject(By.desc("Back")).click()
            assertTrue(device.wait(Until.hasObject(By.desc(description)), 5000))
            assertEquals(
                "Returning from a title must retain the exact pixel offset",
                bounds,
                device.findObject(By.desc(description)).visibleBounds,
            )

            // Search history restores full result snapshots, query, source, filters and page entry.
            instrumentation.runOnMainSync {
                vm.submitSearch("forest")
                vm.applyFilter(BrowseFilter(tags = setOf("action")))
                vm.submitSearch("city")
                assertTrue(vm.browseFilter.value.tags.isEmpty())
                vm.back()
                assertEquals("forest", vm.searchQuery.value)
                assertEquals(setOf("action"), vm.browseFilter.value.tags)
                vm.back()
                assertEquals("forest", vm.searchQuery.value)
                assertTrue(vm.browseFilter.value.tags.isEmpty())
                vm.back()
                assertEquals("", vm.searchQuery.value)
            }
            assertTrue(device.wait(Until.hasObject(By.desc(description)), 5000))
            assertEquals(bounds, device.findObject(By.desc(description)).visibleBounds)
            device.findObject(By.desc(description)).click()
            assertTrue(device.wait(Until.hasObject(By.text("Title details")), 5000))
            val title = vm.selected.value!!
            instrumentation.runOnMainSync { vm.browseTag(title, SourceTag("action", "Action")) }
            assertTrue(device.wait(Until.hasObject(By.desc("Open search")), 5000))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("Title details")), 5000))
            assertEquals(title.id, vm.selected.value?.id)
            device.findObject(By.desc("Back")).click()
            assertTrue(device.wait(Until.hasObject(By.desc(description)), 5000))
            assertEquals(bounds, device.findObject(By.desc(description)).visibleBounds)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
