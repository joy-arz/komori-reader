package app.komori

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import app.komori.data.*
import app.komori.ui.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UpdateUiCoordinationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun cachedDialogCannotDownloadDuringCheckAndReadyResultKeepsExactRelease() {
        val app = compose.activity.application as KomoriApp
        val pending = CompletableDeferred<AppUpdate?>()
        val old = AppUpdate("99.1.0", 991, "https://example.invalid/old.apk", "0".repeat(64), 20)
        val latest = old.copy(version = "99.2.0", code = 992, url = "https://example.invalid/new.apk")
        var transfers = 0
        val backend = object : AppUpdates(app) {
            override val canDownload = true
            override fun cachedUpdate() = old
            override fun unmetered() = false
            override suspend fun check(): AppUpdate? = pending.await()
            override suspend fun download(update: AppUpdate, unmeteredOnly: Boolean, progress: (Int) -> Unit): File {
                transfers++; assertEquals(latest, update); progress(100); return File(app.cacheDir, "fixture-not-an-apk")
            }
        }
        lateinit var vm: UpdateViewModel
        compose.runOnUiThread {
            vm = UpdateViewModel(app, backend); vm.check(true); vm.show()
            compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) { MaterialTheme { UpdateDialog(vm) } }
            }
        }
        try {
            compose.onNodeWithText("Download update").assertIsNotEnabled()
            assertEquals(0, transfers)
            pending.complete(latest)
            compose.waitUntil(5000) { !vm.state.value.checking }
            compose.onNodeWithText("Komori 99.2.0").assertIsDisplayed()
            compose.onNodeWithText("Download update").assertIsEnabled().performClick()
            compose.waitUntil(5000) { vm.state.value.file != null }
            compose.onNodeWithText("Install update").assertIsEnabled()
            assertEquals(1, transfers); assertEquals(latest, vm.state.value.update)
            compose.onNodeWithText("Snooze 24h").performClick()
            compose.onNodeWithText("Install update").assertDoesNotExist()
        } finally { pending.complete(null); compose.runOnUiThread { vm.viewModelScope.cancel() } }
    }
}
