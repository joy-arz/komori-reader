package app.komori

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.komori.data.*
import app.komori.ui.KomoriScreen
import app.komori.ui.ReaderViewModel
import app.komori.ui.UpdateViewModel
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class AuthorsInteractionTest {
    @Test
    fun catalogWaitsForSourceStartsAtTopAndManageCanUnfollow() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as KomoriApp
        val device = UiDevice.getInstance(instrumentation)
        val creators =
            listOf(
                Creator("nhentai:author:authors-test-a", "Aki Mori", "author"),
                Creator("nhentai:author:authors-test-b", "Ren Sato", "artist"),
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
        val ready = CompletableDeferred<Unit>()
        instrumentation.runOnMainSync {
            ViewModelProvider(activity)[UpdateViewModel::class.java].dismiss()
            val factory =
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return ReaderViewModel(app) { creator, _ ->
                            ready.await()
                            val index = creators.indexOf(creator)
                            BrowsePage(
                                (0 until 8).map { order ->
                                    Manga(
                                        if (order == 7) "authors-test-title-$index"
                                        else "authors-test-$index-$order",
                                        if (order == 7)
                                            (if (index == 0) "Forest letters" else "The quiet city")
                                        else "Catalog $index page $order",
                                        "UI fixture",
                                        "",
                                    )
                                },
                                false,
                            )
                        }
                            as T
                    }
                }
            val vm =
                ViewModelProvider(activity, factory)["author-test", ReaderViewModel::class.java]
            activity.setContent { KomoriScreen(vm) }
        }
        try {
            assertTrue(device.wait(Until.hasObject(By.desc("Authors")), 8000))
            device.findObject(By.desc("Authors")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Following")), 5000))
            assertFalse(device.hasObject(By.text("Forest letters")))
            ready.complete(Unit)
            assertTrue(device.wait(Until.hasObject(By.text("Catalog 0 page 0")), 8000))
            assertFalse(device.hasObject(By.text("In your Library")))
            assertFalse(device.hasObject(By.text("Forest letters")))
            val creatorFilter = device.findObject(By.text("Aki Mori"))
            assertTrue(creatorFilter.visibleBounds.bottom < device.displayHeight / 4)
            device.takeScreenshot(File(app.getExternalFilesDir(null), "authors-subscriptions.png"))
            device
                .findObject(By.scrollable(true))
                .scrollUntil(Direction.DOWN, Until.hasObject(By.text("Forest letters")))
            assertTrue(device.hasObject(By.text("Forest letters")))
            assertFalse(device.hasObject(By.text("In your Library")))
            device.findObject(By.text("Ren Sato")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Catalog 1 page 0")), 5000))
            assertFalse(device.hasObject(By.text("Catalog 0 page 0")))
            device.findObject(By.text("All")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Catalog 0 page 0")), 5000))
            assertFalse(device.hasObject(By.text("Select")))
            device.findObject(By.desc("Catalog 0 page 0")).longClick()
            assertTrue(device.wait(Until.hasObject(By.text("1 selected")), 5000))
            device.findObject(By.text("Done")).click()
            assertFalse(device.hasObject(By.text("Select")))
            device.findObject(By.text("Manage")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Unfollow")), 5000))
            device.findObjects(By.text("Unfollow")).first().click()
            device.waitForIdle()
            withTimeout(5000) {
                while (
                    app.dao.observeCreators().first().count { it.id.contains("authors-test-") } != 1
                ) delay(30)
            }
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
