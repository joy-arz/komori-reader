package app.komori

import android.graphics.BitmapFactory
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.komori.data.sourceNames
import app.komori.ui.*
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SourceIconTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun everySourceHasOfflineArtworkAndAccessibleSourceText() = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        for (key in sourceNames.keys) {
            val resource = sourceIconResource(key)
            val bitmap = BitmapFactory.decodeResource(app.resources, resource)
            assertNotNull("Missing website artwork for $key", bitmap)
            assertTrue(bitmap.width > 0 && bitmap.height > 0)
            bitmap.recycle()
            val result = app.imageLoader.execute(ImageRequest.Builder(app).data(resource).size(96).build())
            assertTrue("Bundled artwork must decode without network: $key", result is SuccessResult)
        }
        var alternate by mutableStateOf(false)
        compose.runOnUiThread { compose.activity.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFF0B100D)).padding(20.dp)) {
                    Text("Sources", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    sourceNames.entries.chunked(2).forEach { sources ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            for ((key, name) in sources) Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                SourceIcon(key, 32.dp)
                                Text(name, Modifier.padding(start = 8.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    if (alternate) Text("Ready", color = Color.White)
                }
            }
        } }
        sourceNames.forEach { (key, name) ->
            compose.onNodeWithTag("source-icon-$key").assertIsDisplayed()
            compose.onNodeWithText(name).assertIsDisplayed()
        }
        compose.runOnUiThread { alternate = true }
        compose.onNodeWithText("Ready").assertIsDisplayed()
        val screenshot = File(app.getExternalFilesDir(null), "source-icons.png")
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(screenshot))
        compose.runOnUiThread { compose.activity.setContent { } }
    }
}
