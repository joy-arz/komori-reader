package app.komori

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.komori.data.*
import app.komori.ui.ReaderViewModel
import app.komori.ui.SettingsScreen
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SourceConsentTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realStoreMigratesDefaultsPreservesChoicesAndRequiresAtomicConsent() = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        suspend fun withStore(legacy: Boolean, values: Map<String, String>, block: suspend (SettingsStore) -> Unit) {
            val file = File(app.cacheDir, "source-consent-${UUID.randomUUID()}.preferences_pb")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            try {
                data.edit { p -> values.forEach { (key, value) -> p[stringPreferencesKey(key)] = value } }
                block(SettingsStore(app, legacy, data))
            } finally { scope.cancel(); scope.coroutineContext[Job]?.join(); file.delete() }
        }
        withStore(false, emptyMap()) { store ->
            val fresh = store.flow.first()
            assertEquals("demonic,weeb", fresh.enabledSources)
            assertFalse(fresh.adultSourcesConfirmed)
            assertFalse("MangaDex is non-adult and disabled initially", "mangadex" in fresh.enabledSources.split(','))
            assertTrue(runCatching { store.enableSource("nhentai", true) }.isFailure)
            assertEquals("demonic,weeb", store.flow.first().enabledSources)
            store.confirmAdultSource("nhentai")
            store.enableSource("zap", true)
            store.enableSource("nhentai", false)
            assertTrue(store.flow.first().adultSourcesConfirmed)
            store.enableSource("nhentai", true)
            assertTrue("nhentai" in store.flow.first().enabledSources.split(','))
            store.enableSource("mangadex", true)
            assertTrue("mangadex" in store.flow.first().enabledSources.split(','))
        }
        withStore(true, emptyMap()) { store ->
            assertEquals(sourceNames.keys.joinToString(","), store.flow.first().enabledSources)
            assertTrue(store.flow.first().adultSourcesConfirmed)
        }
        for (choices in listOf("", "weeb", "mangadex,zap")) withStore(true, mapOf("enabledSources" to choices)) { store ->
            val value = store.flow.first()
            assertEquals(choices, value.enabledSources)
            assertEquals("zap" in choices, value.adultSourcesConfirmed)
        }
    }

    @Test fun firstAdultToggleCanBeDeclinedThenConfirmedOnceAcrossSourcesAndViewModels() = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        val previous = app.settings.flow.first()
        var vm: ReaderViewModel? = null
        try {
            sourceNames.keys.forEach { app.settings.enableSource(it, it in setOf("demonic", "weeb")) }
            app.settings.set("adultSourcesConfirmed", false)
            fun show() {
                compose.runOnUiThread {
                    val model = ReaderViewModel(app); vm = model
                    compose.activity.setContent { MaterialTheme {
                        val settings by model.settings.collectAsStateWithLifecycle()
                        androidx.compose.runtime.key(model) { SettingsScreen(model, settings) }
                    } }
                }
                compose.onNodeWithText("Sources").performClick()
            }
            show()
            compose.onNodeWithContentDescription("Enable Nhentai").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Adults only · 18+").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Adults only · 18+").assertIsDisplayed()
            assertFalse("nhentai" in app.settings.flow.first().enabledSources.split(','))
            compose.onNodeWithText("No, keep disabled").performClick()
            assertFalse(app.settings.flow.first().adultSourcesConfirmed)
            compose.onNodeWithContentDescription("Enable Nhentai").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Adults only · 18+").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("I am 18 or older").performClick()
            withTimeout(5000) { app.settings.flow.first { it.adultSourcesConfirmed && "nhentai" in it.enabledSources.split(',') } }
            compose.waitUntil(5000) { vm!!.pendingAdultSource.value == null }
            compose.onNodeWithContentDescription("Enable HentaiZap").performScrollTo().performClick()
            withTimeout(5000) { app.settings.flow.first { "zap" in it.enabledSources.split(',') } }
            compose.onNodeWithText("Adults only · 18+").assertDoesNotExist()
            withContext(Dispatchers.Main) { vm!!.viewModelScope.cancel() }
            show()
            compose.onNodeWithContentDescription("Enable ImHentai").performScrollTo().performClick()
            withTimeout(5000) { app.settings.flow.first { "imhentai" in it.enabledSources.split(',') } }
            compose.onNodeWithText("Adults only · 18+").assertDoesNotExist()
        } finally {
            withContext(Dispatchers.Main) { vm?.viewModelScope?.cancel() }
            compose.runOnUiThread { compose.activity.setContent {} }
            for (key in sourceNames.keys) {
                if (key in previous.enabledSources.split(',') && key in SourceConsentPolicy.adultSources) app.settings.confirmAdultSource(key)
                else app.settings.enableSource(key, key in previous.enabledSources.split(','))
            }
            app.settings.set("adultSourcesConfirmed", previous.adultSourcesConfirmed)
        }
    }
}
