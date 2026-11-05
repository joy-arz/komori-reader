package app.komori

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.komori.data.*
import app.komori.ui.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class TitleLibraryAssignmentTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun libraryPickerPreservesDuplicateConfirmationAndDoesNotSaveOnCancel() = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        val token = UUID.randomUUID().toString()
        val old = Manga(sourceId("weeb", "/series/duplicate-$token"), "Duplicate $token", "", "")
        val book = old.copy(id = sourceId("demonic", "/manga/duplicate-$token"))
        app.dao.putManga(old); app.dao.putShelf(Shelf(old.id, saved = true))
        lateinit var vm: ReaderViewModel
        compose.runOnUiThread {
            vm = ReaderViewModel(app)
            compose.activity.setContent { MaterialTheme(colorScheme = Night, typography = ReaderTypography) { Surface(Modifier.fillMaxSize()) { Details(vm, book, {}, {}) } } }
        }
        try {
            compose.waitUntil(5000) { vm.library.value.any { it.id == old.id } && vm.shelf.value.any { it.mangaId == old.id && it.saved } }
            compose.onNodeWithContentDescription("Add to libraries").performClick()
            compose.onNodeWithText("Already in your library?").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            assertNull(app.dao.shelf(book.id))
            compose.onNodeWithContentDescription("Add to libraries").performClick()
            compose.onNodeWithText("Add anyway").performClick()
            compose.onNodeWithText("Libraries · 1 title").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            assertNull(app.dao.shelf(book.id))
            assertTrue(app.dao.shelf(old.id)!!.saved)
        } finally {
            compose.runOnUiThread { vm.viewModelScope.cancel(); compose.activity.setContent {} }
            for (id in listOf(old.id, book.id)) {
                app.database.openHelper.writableDatabase.execSQL("DELETE FROM shelf WHERE mangaId = ?", arrayOf(id))
                app.database.openHelper.writableDatabase.execSQL("DELETE FROM manga WHERE id = ?", arrayOf(id))
            }
        }
    }

    @Test fun titlePickerSavesIntoMultipleLibrariesAndCancelDoesNotMutate() = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        val token = UUID.randomUUID().toString()
        val book = Manga(sourceId("demonic", "/manga/library-$token"), "Picker $token", "", "")
        val a = LibraryCategory("picker-a-$token", "First $token", 0)
        val b = LibraryCategory("picker-b-$token", "Second $token", 1)
        app.dao.putCategory(a); app.dao.putCategory(b)
        lateinit var vm: ReaderViewModel
        compose.runOnUiThread {
            vm = ReaderViewModel(app)
            compose.activity.setContent { MaterialTheme(colorScheme = Night, typography = ReaderTypography) { Surface(Modifier.fillMaxSize()) { Details(vm, book, {}, {}) } } }
        }
        try {
            val button = compose.onNodeWithContentDescription("Add to libraries")
            button.assertIsDisplayed().assertHasClickAction().assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
            val device = androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
            device.waitForIdle()
            device.executeShellCommand("screencap -p /data/local/tmp/komori-title-libraries.png")
            button.performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText(a.name).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(a.name).performClick()
            compose.onNodeWithText("Cancel").performClick()
            assertNull(app.dao.shelf(book.id))
            assertTrue(app.dao.backupMemberships().none { it.mangaId == book.id })
            button.performClick()
            compose.onNodeWithText(a.name).performClick()
            compose.onNodeWithText(b.name).performClick()
            compose.onNodeWithText("Apply").performClick()
            compose.waitUntil(5000) { runBlocking { app.dao.backupMemberships().count { it.mangaId == book.id } == 2 } }
            assertTrue(app.dao.shelf(book.id)!!.saved)
            assertEquals(book.title, app.dao.manga(book.id)!!.title)
            button.performClick()
            compose.onNodeWithText(a.name).performClick()
            compose.onNodeWithText("Apply").performClick()
            compose.waitUntil(5000) { runBlocking { app.dao.backupMemberships().filter { it.mangaId == book.id }.map { it.categoryId } == listOf(b.id) } }
            assertTrue("Removing an assignment does not unsave the title", app.dao.shelf(book.id)!!.saved)
        } finally {
            compose.runOnUiThread { vm.viewModelScope.cancel(); compose.activity.setContent {} }
            app.dao.deleteCategory(a.id); app.dao.deleteCategory(b.id)
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM shelf WHERE mangaId = ?", arrayOf(book.id))
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM manga WHERE id = ?", arrayOf(book.id))
        }
    }
}
