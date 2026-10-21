package app.komori

import android.content.Intent
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.komori.data.*
import app.komori.ui.BackupViewModel
import app.komori.ui.ReaderViewModel
import app.komori.ui.UpdateViewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BackupInteractionTest {
    @Test
    fun documentPickerCancellationAndReviewedMergeKeepTheirPromises() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as KomoriApp
        val device = UiDevice.getInstance(instrumentation)
        val id = "backup-ui-${UUID.randomUUID()}"
        val file = File(File(app.cacheDir, "updates").apply { mkdirs() }, "$id.json")
        app.dao.putManga(Manga(id, "Backup UI fixture", "Synthetic metadata", ""))
        app.dao.putShelf(Shelf(id, true, true, "Reading", null, 123))
        val activity =
            instrumentation.startActivitySync(
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ) as MainActivity
        lateinit var vm: BackupViewModel
        instrumentation.runOnMainSync {
            ViewModelProvider(activity)[UpdateViewModel::class.java].dismiss()
            ViewModelProvider(activity)[ReaderViewModel::class.java].apply {
                reader.value = null
                selected.value = null
                currentTab.value = 4
            }
            vm = ViewModelProvider(activity)[BackupViewModel::class.java]
        }
        fun settled() {
            val deadline = SystemClock.uptimeMillis() + 10_000
            while (vm.busy.value && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            assertFalse("Backup operation did not finish", vm.busy.value)
        }
        fun cancelPicker() {
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
            // Create-document can focus its filename editor and consume Back in the IME.
            if (!device.wait(Until.hasObject(By.pkg(app.packageName).text("Restore backup")), 1500)) {
                device.pressBack()
            }
            assertTrue(device.wait(Until.hasObject(By.pkg(app.packageName).text("Restore backup")), 8000))
        }
        try {
            assertTrue(device.wait(Until.hasObject(By.text("App")), 8000))
            device.findObject(By.text("App")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Export backup")), 5000))
            device.findObject(By.text("Export backup")).click()
            assertTrue(device.wait(Until.hasObject(By.pkg(java.util.regex.Pattern.compile("com\\.(google\\.android|android)\\.documentsui"))), 5000))
            cancelPicker()
            device.findObject(By.text("Restore backup")).click()
            assertTrue(device.wait(Until.hasObject(By.pkg(java.util.regex.Pattern.compile("com\\.(google\\.android|android)\\.documentsui"))), 5000))
            cancelPicker()
            assertTrue(device.wait(Until.hasObject(By.text("Export backup")), 5000))
            assertNull(vm.preview.value)
            assertEquals("Reading", app.dao.shelf(id)!!.status)

            val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
            instrumentation.runOnMainSync { vm.export(uri) }
            settled()
            assertTrue(vm.message.value.orEmpty(), vm.message.value.orEmpty().contains("saved"))
            assertTrue(
                BackupCodec.decode(file.readBytes()).shelf.any { it.mangaId == id && it.liked }
            )
            app.dao.putShelf(Shelf(id, true, false, "Completed", null, 999))
            instrumentation.runOnMainSync { vm.inspect(uri) }
            settled()
            assertTrue(device.wait(Until.hasObject(By.text("Review Library restore")), 5000))
            assertFalse(device.findObject(By.checkable(true)).isChecked)
            device.findObject(By.text("Cancel")).click()
            assertTrue(device.wait(Until.gone(By.text("Review Library restore")), 5000))
            assertEquals("Completed", app.dao.shelf(id)!!.status)
            instrumentation.runOnMainSync { vm.inspect(uri) }
            settled()
            device.wait(Until.hasObject(By.text("Restore Library")), 5000)
            device.findObject(By.text("Restore Library")).click()
            settled()
            assertEquals("Completed", app.dao.shelf(id)!!.status)
            assertFalse(app.dao.shelf(id)!!.liked)

            instrumentation.runOnMainSync { vm.inspect(uri) }
            settled()
            device.wait(Until.hasObject(By.text("Use backup values for matching entries")), 5000)
            device.findObject(By.text("Use backup values for matching entries")).click()
            assertTrue(device.findObject(By.checkable(true)).isChecked)
            device.findObject(By.text("Restore Library")).click()
            settled()
            assertEquals("Reading", app.dao.shelf(id)!!.status)
            assertTrue(app.dao.shelf(id)!!.liked)

            file.writeText("{\"format\":\"komori.metadata.backup\",\"version\":999}")
            instrumentation.runOnMainSync { vm.inspect(uri) }
            settled()
            assertNull(vm.preview.value)
            assertTrue(vm.message.value.orEmpty().contains("version"))
            assertTrue(app.dao.shelf(id)!!.liked)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            file.delete()
            app.database.openHelper.writableDatabase.execSQL(
                "DELETE FROM shelf WHERE mangaId = ?",
                arrayOf(id),
            )
            app.database.openHelper.writableDatabase.execSQL(
                "DELETE FROM manga WHERE id = ?",
                arrayOf(id),
            )
        }
    }
}
