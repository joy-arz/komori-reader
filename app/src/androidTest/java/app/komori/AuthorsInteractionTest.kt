package app.komori

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.komori.data.*
import app.komori.ui.UpdateViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class AuthorsInteractionTest {
    @Test
    fun creatorStripFiltersSavedWorksAndManageCanUnfollow() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as KomoriApp
        val device = UiDevice.getInstance(instrumentation)
        val creators =
            listOf(
                Creator("authors-test-a", "Aki Mori", "author"),
                Creator("authors-test-b", "Ren Sato", "artist"),
            )
        creators.forEachIndexed { index, creator ->
            app.dao.putCreator(creator)
            val id = "authors-test-title-$index"
            app.dao.putManga(
                Manga(
                    id,
                    if (index == 0) "Forest letters" else "The quiet city",
                    "UI fixture",
                    "",
                    """[{"id":"${creator.id}","name":"${creator.name}","role":"${creator.role}"}]""",
                )
            )
            app.dao.putShelf(Shelf(id, saved = true))
        }
        val activity =
            instrumentation.startActivitySync(
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ) as MainActivity
        instrumentation.runOnMainSync {
            ViewModelProvider(activity)[UpdateViewModel::class.java].dismiss()
        }
        try {
            assertTrue(device.wait(Until.hasObject(By.desc("Authors")), 8000))
            device.findObject(By.desc("Authors")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Forest letters")), 8000))
            assertTrue(device.hasObject(By.text("The quiet city")))
            device.takeScreenshot(File(app.getExternalFilesDir(null), "authors-subscriptions.png"))
            device.findObject(By.text("Aki Mori")).click()
            assertTrue(device.wait(Until.hasObject(By.text("All works")), 5000))
            assertTrue(device.hasObject(By.text("Forest letters")))
            assertFalse(device.hasObject(By.text("The quiet city")))
            device.findObject(By.text("Manage")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Unfollow")), 5000))
            device.findObjects(By.text("Unfollow")).first().click()
            device.waitForIdle()
            assertEquals(
                1,
                app.dao.observeCreators().first().count { it.id.startsWith("authors-test-") },
            )
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            creators.forEach { app.dao.removeCreator(it.id) }
            withContext(Dispatchers.IO) {
                app.database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM shelf WHERE mangaId LIKE 'authors-test-%'"
                )
                app.database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM manga WHERE id LIKE 'authors-test-%'"
                )
            }
        }
    }
}
