package app.komori

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import app.komori.data.*
import app.komori.ui.*
import coil.imageLoader
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OfflineLibraryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    @Test fun downloadedTitleOpensWithoutNetworkAndCoverSurvivesCacheEviction() = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        val token = UUID.randomUUID().toString()
        val book = Manga(sourceId("demonic", "/manga/offline-$token"), "Offline $token", "Saved complete synopsis", "https://cover.example/$token.png")
        val chapterId = sourceId("demonic", "/reader/offline-$token")
        val row = Chapter(chapterId, book.id, JSONObject().put("id", chapterId).put("attributes", JSONObject().put("chapter", "1").put("pages", -1).put("translatedLanguage", "en"))
            .put("relationships", JSONArray().put(JSONObject().put("type", "manga").put("id", book.id))).toString())
        val calls = AtomicInteger()
        var connected = false
        val catalogEntered = CountDownLatch(1)
        val releaseCatalog = CountDownLatch(1)
        val image = Bitmap.createBitmap(12, 24, Bitmap.Config.ARGB_8888)
        val bytes = java.io.ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        image.recycle()
        val source = Sources(OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            if (chain.request().url.toString() != book.cover) {
                catalogEntered.countDown()
                check(releaseCatalog.await(10, TimeUnit.SECONDS))
                throw java.io.IOException("Offline fixture: catalog unavailable")
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(bytes.toResponseBody()).build()
        }.build())
        val vm = withContext(Dispatchers.Main) { ReaderViewModel(app, source, online = { connected }) }
        try {
            app.dao.putManga(book)
            app.dao.putChapters(listOf(row))
            app.dao.putDownload(Download(chapterId, book.id, "Chapter 1", 1, 1, "Complete"))
            chapterDirectory(app, chapterId).mkdirs()
            pageFile(app, chapterId, 0).writeBytes(bytes)
            OfflineTitleAssets.retain(app, book, source)
            assertEquals(1, calls.get())
            assertTrue(OfflineTitleAssets.cover(app, book.id).isFile)
            withContext(Dispatchers.IO) { app.imageLoader.diskCache?.clear() }
            app.imageLoader.memoryCache?.clear()
            assertEquals(OfflineTitleAssets.cover(app, book.id), coverImageRequest(app, book).data)
            withContext(Dispatchers.Main) { vm.open(book, immediately = true) }
            withTimeout(5000) { combine(vm.reader, vm.readerReady) { chapter, ready -> chapter?.id == chapterId && ready }.first { it } }
            assertEquals(chapterId, vm.reader.value?.id)
            assertEquals("Saved complete synopsis", vm.selected.value?.description)
            assertEquals(1, calls.get())
            assertTrue(pageFile(app, chapterId, 0).isFile)
            val previousSession = vm.readerSession.value
            withContext(Dispatchers.Main) { vm.closeReader(); connected = true; vm.open(book, immediately = true) }
            assertTrue(withContext(Dispatchers.IO) { catalogEntered.await(5, TimeUnit.SECONDS) })
            withTimeout(5000) { combine(vm.reader, vm.readerReady, vm.readerSession) { chapter, ready, session ->
                chapter?.id == chapterId && ready && session > previousSession
            }.first { it } }
            assertEquals("Downloaded reading does not wait for the catalog", chapterId, vm.reader.value?.id)
            releaseCatalog.countDown()
            withTimeout(5000) { vm.chapterStatus.first { it.contains("Refresh failed") } }
            assertEquals("Saved complete synopsis", vm.selected.value?.description)
            OfflineTitleAssets.removeIfUnused(app, book.id)
            assertTrue("Other saved chapters still own the cover", OfflineTitleAssets.cover(app, book.id).isFile)
            app.dao.removeDownload(chapterId)
            OfflineTitleAssets.removeIfUnused(app, book.id)
            assertFalse(OfflineTitleAssets.cover(app, book.id).exists())
            assertNotNull("Removing downloads retains reading metadata", app.dao.manga(book.id))
        } finally {
            releaseCatalog.countDown()
            withContext(Dispatchers.Main) { vm.closeReader(); vm.viewModelScope.cancel() }
            app.dao.removeDownload(chapterId)
            OfflineTitleAssets.removeIfUnused(app, book.id)
            chapterDirectory(app, chapterId).deleteRecursively()
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM chapter_index WHERE mangaId = ?", arrayOf(book.id))
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM chapters WHERE mangaId = ?", arrayOf(book.id))
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM shelf WHERE mangaId = ?", arrayOf(book.id))
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM progress WHERE mangaId = ?", arrayOf(book.id))
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM manga WHERE id = ?", arrayOf(book.id))
        }
    }

    @Test fun librariesCanBeCreatedAssignedAndReorderedWithoutLosingTitles() = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        val token = UUID.randomUUID().toString().take(8)
        val vm = withContext(Dispatchers.Main) { ReaderViewModel(app) }
        val book = Manga("library-$token", "Library title $token", "", "")
        val original = app.dao.backupCategories()
        val previousView = app.settings.flow.first()
        var created = emptyList<LibraryCategory>()
        try {
            withContext(Dispatchers.Main) { vm.saveCategory("First $token") }.join()
            withContext(Dispatchers.Main) { vm.saveCategory("Second $token") }.join()
            created = app.dao.backupCategories().filter { it.name.endsWith(token) }
            assertEquals(2, created.size)
            withContext(Dispatchers.Main) { vm.changeCategories(listOf(book.id), mapOf(created[1].id to true), listOf(book)) }.join()
            compose.activity.runOnUiThread { compose.activity.setContent { MaterialTheme { CategoryManagerDialog(vm) { } } } }
            compose.waitUntil(5000) { vm.categories.value.any { it.id == created[1].id } }
            compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.ScrollToIndex))
                .performScrollToNode(hasText("Second $token"))
            compose.onNodeWithText("Second $token").assertIsDisplayed()
            val earlier = compose.onNodeWithContentDescription("Reorder Second $token").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].single { it.label == "Move earlier" }
            compose.runOnUiThread { assertTrue(earlier.action()) }
            withTimeout(5000) { vm.categories.first { rows -> rows.indexOfFirst { it.id == created[1].id } < rows.indexOfFirst { it.id == created[0].id } } }
            assertEquals(created[1].id, app.dao.backupMemberships().single { it.mangaId == book.id }.categoryId)
            app.settings.libraryView("collection:Saved", true)
            compose.activity.runOnUiThread { compose.activity.setContent { MaterialTheme { Library(vm) { } } } }
            compose.waitUntil(5000) { compose.onAllNodesWithText(book.title).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Libraries", useUnmergedTree = true).performClick()
            compose.onNodeWithText("First $token").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText(book.title).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("Second $token").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText(book.title).fetchSemanticsNodes().isNotEmpty() }
            withContext(Dispatchers.Main) { vm.deleteCategory(created[1].id) }.join()
            assertTrue(app.dao.backupMemberships().none { it.mangaId == book.id })
            assertTrue(app.dao.shelf(book.id)!!.saved)
            assertNotNull(app.dao.manga(book.id))
        } finally {
            compose.activity.runOnUiThread { compose.activity.setContent { } }
            withContext(Dispatchers.Main) { vm.viewModelScope.cancel() }
            created.forEach { app.dao.deleteCategory(it.id) }
            original.forEach { app.dao.putCategory(it) }
            app.settings.libraryView(previousView.libraryStart, true)
            app.settings.libraryView(previousView.lastLibraryView)
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM shelf WHERE mangaId = ?", arrayOf(book.id))
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM manga WHERE id = ?", arrayOf(book.id))
        }
    }
}
