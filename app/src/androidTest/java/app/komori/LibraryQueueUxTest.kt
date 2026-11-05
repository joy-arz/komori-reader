package app.komori

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.komori.data.*
import app.komori.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class LibraryQueueUxTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun libraryTabsPersistLastAndPinnedViewsWithoutLeakingIntoCollections() = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        val previous = app.settings.flow.first()
        val token = UUID.randomUUID().toString().take(8)
        val first = LibraryCategory("ux-a-$token", "First $token", 1000)
        val second = LibraryCategory("ux-b-$token", "Second $token", 1001)
        val book = Manga("ux-book-$token", "Book $token", "", "")
        app.dao.putCategory(first); app.dao.putCategory(second)
        app.dao.putManga(book); app.dao.putShelf(Shelf(book.id, saved = true))
        app.dao.putMembership(CategoryMembership(book.id, second.id))
        app.settings.libraryView("last", true); app.settings.libraryView("collection:Saved")
        lateinit var vm: ReaderViewModel
        var screen by mutableIntStateOf(0)
        compose.runOnUiThread {
            vm = ReaderViewModel(app)
            compose.activity.setContent { MaterialTheme(colorScheme = Night, typography = ReaderTypography) {
                Surface(Modifier.fillMaxSize().background(Night.background).systemBarsPadding()) { key(screen) { Library(vm) {} } }
            } }
        }
        fun waitView(value: String) { compose.waitUntil(5000) { runBlocking { app.settings.flow.first().lastLibraryView == value } } }
        try {
            compose.waitUntil(5000) { compose.onAllNodes(hasText("Collection") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Collection").assertIsSelected()
            compose.onNodeWithText("Libraries", useUnmergedTree = true).performClick()
            compose.onNodeWithText("All", useUnmergedTree = true).assertExists()
            compose.onNodeWithContentDescription("Manage libraries").assertIsDisplayed().assertHasClickAction().assertTouchWidthIsEqualTo(48.dp)
            compose.onNodeWithText(first.name).performClick()
            compose.onAllNodesWithText(book.title).assertCountEquals(0)
            compose.onNodeWithText(second.name).performClick()
            compose.onNodeWithText(book.title).assertExists()
            waitView("library:${second.id}")
            compose.runOnUiThread { screen++ }
            compose.waitUntil(5000) { compose.onAllNodes(hasText(second.name) and isSelected()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText(second.name) and isSelected()).assertIsSelected()
            compose.onNodeWithText("Collection").performClick()
            compose.onNodeWithText(book.title).assertExists()
            waitView("collection:Saved")
            app.settings.libraryView("library:${first.id}", true)
            compose.runOnUiThread { screen++ }
            compose.waitUntil(5000) { compose.onAllNodes(hasText(first.name) and isSelected()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText(first.name) and isSelected()).assertIsSelected()
            app.dao.deleteCategory(first.id)
            compose.waitUntil(5000) { runBlocking { app.settings.flow.first().lastLibraryView == "library:" } }
            compose.onNodeWithText("All").assertIsSelected()
            screenshot("komori-library-tabs")
        } finally {
            compose.runOnUiThread { vm.viewModelScope.cancel(); compose.activity.setContent {} }
            app.settings.libraryView(previous.libraryStart, true); app.settings.libraryView(previous.lastLibraryView)
            app.dao.deleteCategory(first.id); app.dao.deleteCategory(second.id)
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM shelf WHERE mangaId = ?", arrayOf(book.id))
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM manga WHERE id = ?", arrayOf(book.id))
        }
    }

    @Test fun sourceHandleDragPersistsOrderAndAccessibleActionsMoveBack() = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        val previous = app.settings.flow.first()
        val original = sourceOrder(previous.sources)
        lateinit var vm: ReaderViewModel
        compose.runOnUiThread {
            vm = ReaderViewModel(app)
            compose.activity.setContent { MaterialTheme(colorScheme = Night, typography = ReaderTypography) {
                val settings by vm.settings.collectAsStateWithLifecycle()
                Surface(Modifier.fillMaxSize().background(Night.background).systemBarsPadding()) { SettingsScreen(vm, settings) }
            } }
        }
        try {
            compose.onNodeWithText("Sources").performClick()
            val first = sourceNames.getValue(original.first())
            val handle = compose.onNodeWithContentDescription("Reorder $first")
            val rowDistance = compose.onNodeWithContentDescription("Reorder ${sourceNames.getValue(original[1])}").fetchSemanticsNode().boundsInRoot.center.y - handle.fetchSemanticsNode().boundsInRoot.center.y
            handle.performTouchInput {
                down(center); advanceEventTime(650)
                moveBy(androidx.compose.ui.geometry.Offset(0f, rowDistance + 8f), 500)
                up()
            }
            compose.waitUntil(5000) { runBlocking { sourceOrder(app.settings.flow.first().sources).first() != original.first() } }
            assertEquals(previous.enabledSources, app.settings.flow.first().enabledSources)
            val earlier = compose.onNodeWithContentDescription("Reorder $first").fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Move earlier" }
            compose.runOnUiThread { assertTrue(earlier.action()) }
            compose.waitUntil(5000) { runBlocking { sourceOrder(app.settings.flow.first().sources) == original } }
            screenshot("komori-source-reorder")
        } finally {
            compose.runOnUiThread { vm.viewModelScope.cancel(); compose.activity.setContent {} }
            app.settings.sources(previous.sources)
        }
    }

    @Test fun queueScopesAndSelectionRemainExplicit() = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        val token = UUID.randomUUID().toString()
        val title = Manga("queue-$token", "Fixture series", "", "")
        val pending = Download("queue-ch-$token", title.id, "Chapter 2", 0, state = "Paused")
        val ready = Download("ready-ch-$token", title.id, "Chapter 1", 1, 1, "Complete")
        app.dao.putManga(title); app.dao.putDownload(pending); app.dao.putDownload(ready)
        lateinit var vm: ReaderViewModel
        compose.runOnUiThread { vm = ReaderViewModel(app); compose.activity.setContent {
            MaterialTheme(colorScheme = Night, typography = ReaderTypography) { Surface(Modifier.fillMaxSize().background(Night.background).systemBarsPadding()) { Downloads(vm) } }
        } }
        try {
            compose.waitUntil(5000) { compose.onAllNodesWithText("Chapter 2").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Queue", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Chapter 2").assertExists()
            compose.onAllNodesWithText("Chapter 1").assertCountEquals(0)
            compose.onNodeWithText("Chapter 2").performTouchInput { longClick() }
            compose.onNodeWithContentDescription("Select download Chapter 2").assertIsOn()
            compose.onNodeWithContentDescription("End selection").performClick()
            compose.onNodeWithText("Offline", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Chapter 1").assertExists()
            compose.onAllNodesWithText("Chapter 2").assertCountEquals(0)
            screenshot("komori-queue-offline")
        } finally {
            compose.runOnUiThread { vm.viewModelScope.cancel(); compose.activity.setContent {} }
            app.dao.removeDownload(pending.releaseId); app.dao.removeDownload(ready.releaseId)
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM manga WHERE id = ?", arrayOf(title.id))
        }
    }
    @Test fun selectionRemainsUsableAtLargeFontInNarrowRtlLayout() {
        val selection = MultiSelection(true, listOf("a"))
        var applied = emptyList<String>()
        compose.runOnUiThread { compose.activity.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f, 1.6f),
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl,
            ) {
                MaterialTheme(colorScheme = Night, typography = ReaderTypography) {
                    androidx.compose.foundation.layout.Box(Modifier.width(320.dp).testTag("selection-width")) {
                        SelectionBar(selection, listOf("a", "b"), listOf(BatchAction("Apply") { applied = it }))
                    }
                }
            }
        } }
        val limits = compose.onNodeWithTag("selection-width").fetchSemanticsNode().boundsInRoot
        for (node in listOf(compose.onNodeWithText("1 selected"), compose.onNodeWithText("Actions"), compose.onNodeWithContentDescription("End selection"))) {
            val bounds = node.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= limits.left && bounds.right <= limits.right)
        }
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithText("Actions").performClick()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(listOf("a", "b"), applied)
        compose.onAllNodesWithContentDescription("End selection").assertCountEquals(0)
    }

    private fun screenshot(name: String) {
        androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("screencap -p /data/local/tmp/$name.png")
    }
}
