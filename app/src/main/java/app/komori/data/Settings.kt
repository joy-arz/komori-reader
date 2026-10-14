package app.komori.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")

data class Settings(
    val language: String = "en",
    val smart: Boolean = true,
    val resume: Boolean = true,
    val reread: Boolean = false,
    val direct: Boolean = false,
    val saver: Boolean = true,
    val wifi: Boolean = true,
    val dark: Boolean = false,
    val webtoon: Boolean = false,
    val rtl: Boolean = false,
    val keepScreenOn: Boolean = false,
)

class SettingsStore(private val context: Context) {
    val flow =
        context.settingsStore.data.map { p ->
            Settings(
                p[stringPreferencesKey("language")] ?: "en",
                p[booleanPreferencesKey("smart")] ?: true,
                p[booleanPreferencesKey("resume")] ?: true,
                p[booleanPreferencesKey("reread")] ?: false,
                p[booleanPreferencesKey("direct")] ?: false,
                p[booleanPreferencesKey("saver")] ?: true,
                p[booleanPreferencesKey("wifi")] ?: true,
                p[booleanPreferencesKey("dark")] ?: false,
                p[booleanPreferencesKey("webtoon")] ?: false,
                p[booleanPreferencesKey("rtl")] ?: false,
                p[booleanPreferencesKey("keepScreenOn")] ?: false,
            )
        }

    suspend fun set(key: String, value: Boolean) {
        context.settingsStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    suspend fun language(value: String) {
        context.settingsStore.edit { it[stringPreferencesKey("language")] = value }
    }
}
