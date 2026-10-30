package app.komori

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
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
import org.junit.Rule

class AuthorsInteractionTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test
    fun catalogWaitsForSourceStartsAtTopAndManageCanUnfollow() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as KomoriApp
        val device = UiDevice.getInstance(instrumentation)
        fun refreshNative() {
            compose.waitForIdle()
            if (android.os.Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
        }
        fun hasNative(selector: androidx.test.uiautomator.BySelector): Boolean {
            refreshNative()
            return device.hasObject(selector)
        }
        fun waitForNative(condition: androidx.test.uiautomator.SearchCondition<Boolean>, timeout: Long): Boolean =
            device.wait(object : androidx.test.uiautomator.Condition<UiDevice, Boolean> {
                override fun apply(device: UiDevice): Boolean {
                    refreshNative()
                    return condition.apply(device)
                }
            }, timeout)
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
                        return ReaderViewModel(app) { creator, _, filter ->
                            ready.await()
                            assertTrue(filter.sort in listOf("Latest", "Popular all time"))
                            val index = creators.indexOf(creator)
                            BrowsePage(
                                (0 until 8)
                                    .map { order ->
                                        Manga(
                                            if (order == 7) "authors-test-title-$index"
                                            else "authors-test-$index-$order",
                                            if (order == 7)
                                                (if (index == 0) "Forest letters"
                                                else "The quiet city")
                                            else "Catalog $index page $order",
                                            "UI fixture",
                                            "",
                                        )
                                    }
                                    .let {
                                        if (filter.sort == "Popular all time") it.reversed() else it
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
            assertTrue(waitForNative(Until.hasObject(By.desc("Authors")), 8000))
            device.findObject(By.desc("Authors")).click()
            assertTrue(waitForNative(Until.hasObject(By.text("Following")), 5000))
            assertFalse(hasNative(By.text("Forest letters")))
            ready.complete(Unit)
            assertTrue(waitForNative(Until.hasObject(By.text("Catalog 0 page 0")), 8000))
            assertFalse(hasNative(By.text("In your Library")))
            assertFalse(hasNative(By.text("Forest letters")))
            val creatorFilter = device.findObject(By.text("Aki Mori"))
            assertTrue(creatorFilter.visibleBounds.bottom < device.displayHeight / 4)
            device.takeScreenshot(File(app.getExternalFilesDir(null), "authors-subscriptions.png"))
            repeat(10) {
                if (!hasNative(By.text("Forest letters"))) {
                    device.swipe(
                        device.displayWidth / 2,
                        device.displayHeight * 3 / 4,
                        device.displayWidth / 2,
                        device.displayHeight / 3,
                        24,
                    )
                    device.waitForIdle()
                }
            }
            assertTrue(hasNative(By.text("Forest letters")))
            assertFalse(hasNative(By.text("In your Library")))
            device.findObject(By.text("Ren Sato")).click()
            assertTrue(waitForNative(Until.hasObject(By.text("Catalog 1 page 0")), 5000))
            assertFalse(hasNative(By.text("Catalog 0 page 0")))
            device.findObject(By.text("All")).click()
            assertTrue(waitForNative(Until.hasObject(By.text("Catalog 0 page 0")), 5000))
            fun sortBy(label: String) {
                device.findObject(By.desc("Sort author works")).click()
                assertTrue(waitForNative(Until.hasObject(By.text(label)), 5000))
                device.findObject(By.text(label)).click()
            }
            sortBy("Popularity / views")
            assertTrue(waitForNative(Until.hasObject(By.text("Forest letters")), 5000))
            sortBy("Alphabetical")
            assertTrue(waitForNative(Until.hasObject(By.text("Catalog 0 page 0")), 5000))
            sortBy("Author")
            assertTrue(waitForNative(Until.hasObject(By.text("Catalog 0 page 0")), 5000))
            assertFalse(hasNative(By.text("Catalog 1 page 0")))
            sortBy("Latest")
            assertTrue(waitForNative(Until.hasObject(By.text("Catalog 1 page 0")), 5000))
            assertFalse(hasNative(By.text("Select")))
            device.findObject(By.desc("Catalog 0 page 0")).longClick()
            assertTrue(waitForNative(Until.hasObject(By.text("1 selected")), 5000))
            device.findObject(By.text("Done")).click()
            assertFalse(hasNative(By.text("Select")))
            device.findObject(By.text("Manage")).click()
            assertTrue(waitForNative(Until.hasObject(By.text("Unfollow")), 5000))
            device.findObjects(By.text("Unfollow")).first().click()
            device.waitForIdle()
            withTimeout(5000) {
                while (
                    app.dao.observeCreators().first().count { it.id.contains("authors-test-") } != 1
                ) delay(30)
            }
        } catch (error: Throwable) {
            runCatching { device.takeScreenshot(File(app.getExternalFilesDir(null), "authors-interaction-failure.png")) }
            runCatching { device.dumpWindowHierarchy(File(app.getExternalFilesDir(null), "authors-interaction-failure.xml")) }
            throw error
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
