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
    val showUnavailable: Boolean = true,
    val newestFirst: Boolean = false,
    val libraryAlphabetical: Boolean = false,
    val autoUpdateDownload: Boolean = true,
    val showContinue: Boolean = true,
    val continuousChapters: Boolean = true,
    val retrySeconds: Int = 5,
    val sources: String = "demonic,weeb,comix,mangadex,zap,imhentai,nhentai",
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
                p[booleanPreferencesKey("showUnavailable")] ?: true,
                p[booleanPreferencesKey("newestFirst")] ?: false,
                p[booleanPreferencesKey("libraryAlphabetical")] ?: false,
                p[booleanPreferencesKey("autoUpdateDownload")] ?: true,
                p[booleanPreferencesKey("showContinue")] ?: true,
                p[booleanPreferencesKey("continuousChapters")] ?: true,
                (p[intPreferencesKey("retrySeconds")] ?: 5).let {
                    if (it in listOf(0, 5, 10, 30)) it else 5
                },
                p[stringPreferencesKey("sources")]
                    ?: "demonic,weeb,comix,mangadex,zap,imhentai,nhentai",
            )
        }

    suspend fun set(key: String, value: Boolean) {
        context.settingsStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    suspend fun retrySeconds(value: Int) {
        require(value in listOf(0, 5, 10, 30))
        context.settingsStore.edit { it[intPreferencesKey("retrySeconds")] = value }
    }

    suspend fun sources(value: String) {
        context.settingsStore.edit { it[stringPreferencesKey("sources")] = value }
    }

    suspend fun language(value: String) {
        context.settingsStore.edit { it[stringPreferencesKey("language")] = value }
    }
}
