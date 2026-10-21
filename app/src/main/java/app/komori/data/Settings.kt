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
    val dark: Boolean = true,
    val webtoon: Boolean = false,
    val rtl: Boolean = false,
    val keepScreenOn: Boolean = false,
    val showUnavailable: Boolean = true,
    val newestFirst: Boolean = false,
    val libraryAlphabetical: Boolean = false,
    val autoUpdateDownload: Boolean = true,
    val showContinue: Boolean = true,
    val continuousChapters: Boolean = true,
    val retrySeconds: Int = 3,
    val sources: String = "demonic,weeb,mangadex,zap,imhentai,nhentai",
    val theme: String = "legacy",
    val compact: Boolean = true,
    val brightness: Int = -1,
    val dim: Int = 0,
    val orientation: String = "auto",
    val volumeKeys: Boolean = false,
    val detectWebtoon: Boolean = false,
    val preloadPages: Int = 1,
    val readerCacheMb: Int = 64,
    val gapWarnings: Boolean = true,
    val libraryCheckHours: Int = 0,
    val checkWifi: Boolean = true,
    val checkAuthors: Boolean = false,
    val notifyChapterUpdates: Boolean = false,
    val libraryCheckStatus: String = "Automatic checks are off. The first check establishes a baseline.",
    val enabledSources: String = sourceNames.keys.joinToString(","),
    val savedSearches: String = "[]",
    val exportFolder: String = "",
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
                p[booleanPreferencesKey("dark")] ?: true,
                p[booleanPreferencesKey("webtoon")] ?: false,
                p[booleanPreferencesKey("rtl")] ?: false,
                p[booleanPreferencesKey("keepScreenOn")] ?: false,
                p[booleanPreferencesKey("showUnavailable")] ?: true,
                p[booleanPreferencesKey("newestFirst")] ?: false,
                p[booleanPreferencesKey("libraryAlphabetical")] ?: false,
                p[booleanPreferencesKey("autoUpdateDownload")] ?: true,
                p[booleanPreferencesKey("showContinue")] ?: true,
                p[booleanPreferencesKey("continuousChapters")] ?: true,
                (p[intPreferencesKey("retrySeconds")] ?: 3).let {
                    if (it in listOf(0, 3, 5, 10, 30)) it else 3
                },
                p[stringPreferencesKey("sources")] ?: "demonic,weeb,mangadex,zap,imhentai,nhentai",
                theme = p[stringPreferencesKey("theme")]?.takeIf { it in listOf("legacy", "system", "light", "dark") } ?: "legacy",
                compact = p[booleanPreferencesKey("compact")] ?: true,
                brightness = (p[intPreferencesKey("brightness")] ?: -1).coerceIn(-1, 100),
                dim = (p[intPreferencesKey("dim")] ?: 0).coerceIn(0, 80),
                orientation = p[stringPreferencesKey("orientation")]?.takeIf { it in listOf("auto", "portrait", "landscape", "locked") } ?: "auto",
                volumeKeys = p[booleanPreferencesKey("volumeKeys")] ?: false,
                detectWebtoon = p[booleanPreferencesKey("detectWebtoon")] ?: false,
                preloadPages = (p[intPreferencesKey("preloadPages")] ?: 1).coerceIn(0, 2),
                readerCacheMb = (p[intPreferencesKey("readerCacheMb")] ?: 64).coerceIn(32, 256),
                gapWarnings = p[booleanPreferencesKey("gapWarnings")] ?: true,
                libraryCheckHours = (p[intPreferencesKey("libraryCheckHours")] ?: 0).takeIf { it in listOf(0, 12, 24) } ?: 0,
                checkWifi = p[booleanPreferencesKey("checkWifi")] ?: true,
                checkAuthors = p[booleanPreferencesKey("checkAuthors")] ?: false,
                notifyChapterUpdates = p[booleanPreferencesKey("notifyChapterUpdates")] ?: false,
                libraryCheckStatus = p[stringPreferencesKey("libraryCheckStatus")] ?: "Automatic checks are off. The first check establishes a baseline.",
                enabledSources = p[stringPreferencesKey("enabledSources")] ?: sourceNames.keys.joinToString(","),
                savedSearches = p[stringPreferencesKey("savedSearches")] ?: "[]",
                exportFolder = p[stringPreferencesKey("exportFolder")] ?: "",
            )
        }

    suspend fun set(key: String, value: Boolean) {
        context.settingsStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    suspend fun number(key: String, value: Int) {
        val range = when (key) {
            "brightness" -> -1..100
            "dim" -> 0..80
            "preloadPages" -> 0..2
            "readerCacheMb" -> 32..256
            "libraryCheckHours" -> 0..24
            else -> error("Unknown numeric preference")
        }
        require(value in range && (key != "libraryCheckHours" || value in listOf(0, 12, 24)))
        context.settingsStore.edit { it[intPreferencesKey(key)] = value }
    }

    suspend fun choice(key: String, value: String) {
        val choices = when (key) {
            "theme" -> setOf("legacy", "system", "light", "dark")
            "orientation" -> setOf("auto", "portrait", "landscape", "locked")
            else -> error("Unknown choice preference")
        }
        require(value in choices)
        context.settingsStore.edit { it[stringPreferencesKey(key)] = value }
    }

    suspend fun exportFolder(value: String) {
        require(value.isEmpty() || value.startsWith("content://") && value.length <= 4096)
        context.settingsStore.edit { it[stringPreferencesKey("exportFolder")] = value }
    }

    suspend fun saveSearch(row: SavedSearch) {
        context.settingsStore.edit { p ->
            val rows = savedSearches(p[stringPreferencesKey("savedSearches")] ?: "[]")
            p[stringPreferencesKey("savedSearches")] = savedSearchJson(rows.filter { it.id != row.id } + row)
        }
    }

    suspend fun removeSearch(id: String) {
        context.settingsStore.edit { p -> p[stringPreferencesKey("savedSearches")] = savedSearchJson(savedSearches(p[stringPreferencesKey("savedSearches")] ?: "[]").filter { it.id != id }) }
    }

    suspend fun checkStatus(value: String) {
        context.settingsStore.edit { it[stringPreferencesKey("libraryCheckStatus")] = value.take(2000) }
    }

    suspend fun enableSource(key: String, enabled: Boolean) {
        require(key in sourceNames)
        context.settingsStore.edit { p ->
            val current = (p[stringPreferencesKey("enabledSources")] ?: sourceNames.keys.joinToString(",")).split(',').filter { it in sourceNames }.toMutableSet()
            if (enabled) current.add(key) else current.remove(key)
            p[stringPreferencesKey("enabledSources")] = sourceNames.keys.filter { it in current }.joinToString(",")
        }
    }

    suspend fun retrySeconds(value: Int) {
        require(value in listOf(0, 3, 5, 10, 30))
        context.settingsStore.edit { it[intPreferencesKey("retrySeconds")] = value }
    }

    suspend fun sources(value: String) {
        context.settingsStore.edit { it[stringPreferencesKey("sources")] = value }
    }

    suspend fun language(value: String) {
        context.settingsStore.edit { it[stringPreferencesKey("language")] = value }
    }
}

fun enabledSourceOrder(settings: Settings): List<String> = sourceOrder(settings.sources).filter { it in settings.enabledSources.split(',') }
