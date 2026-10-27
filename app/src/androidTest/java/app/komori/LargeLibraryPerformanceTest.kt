package app.komori

import android.content.Intent
import android.os.Debug
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import androidx.compose.runtime.Recomposer
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import app.komori.data.*
import app.komori.ui.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class LargeLibraryPerformanceTest {
    @OptIn(androidx.compose.runtime.ExperimentalComposeApi::class)
    @Test fun thousandTitleLibraryAndFiftyThousandProgressRowsStayLazyAndScoped() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as KomoriApp
        val device = UiDevice.getInstance(instrumentation)
        val prefix = "perf-${UUID.randomUUID()}"
        val settings = app.settings.flow.first()
        var activity: MainActivity? = null
        var vm: ReaderViewModel? = null
        val before = Debug.getPss()
        val seedStart = System.nanoTime()
        try {
            app.settings.set("showContinue", false)
            app.settings.set("libraryAlphabetical", false)
            app.database.withTransaction {
                val sql = app.database.openHelper.writableDatabase
                val progress = sql.compileStatement("INSERT INTO progress(releaseId,mangaId,chapterKey,page,completed,scroll) VALUES(?,?,?,?,?,?)")
                try {
                    repeat(1000) { book ->
                        val id = "$prefix-$book"
                        app.dao.putManga(Manga(id, "Perf library $book", "Synthetic performance fixture", ""))
                        app.dao.putShelf(Shelf(id, saved = true, touched = System.currentTimeMillis() + book))
                        repeat(50) { chapter ->
                            progress.bindString(1, "$id-$chapter"); progress.bindString(2, id)
                            progress.bindString(3, "$id-key-$chapter"); progress.bindLong(4, 2)
                            progress.bindLong(5, 0); progress.bindDouble(6, 0.0); progress.executeInsert()
                        }
                    }
                } finally { progress.close() }
            }
            val seededMs = (System.nanoTime() - seedStart) / 1e6
            device.executeShellCommand("dumpsys gfxinfo ${app.packageName} reset")
            val start = SystemClock.elapsedRealtime()
            activity = instrumentation.startActivitySync(Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            instrumentation.runOnMainSync {
                vm = ViewModelProvider(activity!!)[ReaderViewModel::class.java]
                vm!!.currentTab.value = 0
                ViewModelProvider(activity!!)[UpdateViewModel::class.java].dismiss()
            }
            assertTrue(device.wait(Until.hasObject(By.descStartsWith("Perf library ")), 8000))
            val firstVisibleMs = SystemClock.elapsedRealtime() - start
            val visible = device.findObjects(By.descStartsWith("Perf library ")).size
            assertTrue("Library must stay lazy: visible=$visible", visible in 1..40)
            withTimeout(8000) { vm!!.library.first { titles -> titles.count { it.id.startsWith(prefix) } == 1000 } }
            assertTrue(vm!!.progress.value.isEmpty())
            val recompositionsBefore = Recomposer.runningRecomposers.value.sumOf { it.changeCount }
            val observationStart = System.nanoTime()
            repeat(50) { page -> app.dao.putProgress(Progress("$prefix-500-0", "$prefix-500", "$prefix-500-key-0", page)) }
            assertEquals(50, app.dao.observeProgressForManga("$prefix-500").first().size)
            instrumentation.waitForIdleSync()
            assertTrue("An unselected Library must not retain all progress", vm!!.progress.value.isEmpty())
            val applies = Recomposer.runningRecomposers.value.sumOf { it.changeCount } - recompositionsBefore
            val observationMs = (System.nanoTime() - observationStart) / 1e6
            repeat(6) { device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 3, 20) }
            device.waitForIdle()
            android.util.Log.i("KomoriBenchmark", "library titles=1000 progress=50000 seed_ms=$seededMs first_activity_visible_ms=$firstVisibleMs visible_cover_nodes=$visible progress_writes=50 observation_ms=$observationMs composition_apply_delta=$applies pss_before_kb=$before pss_after_kb=${Debug.getPss()}")
            java.io.File(app.getExternalFilesDir(null), "large-library-gfx.txt").writeText(device.executeShellCommand("dumpsys gfxinfo ${app.packageName}"))
        } finally {
            instrumentation.runOnMainSync { vm?.viewModelScope?.cancel(); activity?.finish() }
            app.database.withTransaction {
                val sql = app.database.openHelper.writableDatabase
                sql.execSQL("DELETE FROM progress WHERE mangaId LIKE ?", arrayOf("$prefix%"))
                sql.execSQL("DELETE FROM shelf WHERE mangaId LIKE ?", arrayOf("$prefix%"))
                sql.execSQL("DELETE FROM manga WHERE id LIKE ?", arrayOf("$prefix%"))
            }
            app.settings.set("showContinue", settings.showContinue)
            app.settings.set("libraryAlphabetical", settings.libraryAlphabetical)
        }
    }
}
