package app.komori

import androidx.test.runner.AndroidJUnitRunner
import app.komori.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File

/** Existing integration fixtures exercise the explicitly enabled full catalog. */
class KomoriTestRunner : AndroidJUnitRunner() {
    override fun onStart() {
        val app = targetContext.applicationContext as KomoriApp
        runBlocking {
            val initial = app.settings.flow.first()
            File(app.getExternalFilesDir(null), "source-install-state.json").writeText(JSONObject()
                .put("legacyInstall", app.legacySourceInstall)
                .put("enabledSources", initial.enabledSources)
                .put("adultConfirmed", initial.adultSourcesConfirmed).toString())
            if (!app.legacySourceInstall) {
                check(initial.enabledSources == SourceConsentPolicy.defaultEnabled)
                check(!initial.adultSourcesConfirmed)
            }
            for (key in sourceNames.keys) {
                if (key in SourceConsentPolicy.adultSources) app.settings.confirmAdultSource(key)
                else app.settings.enableSource(key, true)
            }
        }
        super.onStart()
    }
}
