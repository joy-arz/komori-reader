package app.komori.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll

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
    val preloadPages: Int = 10,
    val readerCacheMb: Int = 64,
    val gapWarnings: Boolean = true,
    val libraryCheckHours: Int = 0,
    val checkWifi: Boolean = true,
    val checkAuthors: Boolean = false,
    val notifyChapterUpdates: Boolean = false,
    val libraryCheckStatus: String = "Automatic checks are off. The first check establishes a baseline.",
    val enabledSources: String = SourceConsentPolicy.defaultEnabled,
    val savedSearches: String = "[]",
    val exportFolder: String = "",
    val backupFolder: String = "",
    val backupDays: Int = 0,
    val backupRetention: Int = 7,
    val backupStatus: String = "No automatic backup has run.",
    val backupLastSuccess: Long = 0,
    val adultSourcesConfirmed: Boolean = false,
)

class SettingsStore(context: Context, private val legacyInstall: Boolean = false,
    private val preferences: androidx.datastore.core.DataStore<Preferences> = context.settingsStore) {
    private suspend fun initializeSources() {
        preferences.edit { p ->
            if (p[intPreferencesKey("sourceConsentVersion")] == 1) return@edit
            val initial = SourceConsentPolicy.initial(p[stringPreferencesKey("enabledSources")], legacyInstall,
                p[booleanPreferencesKey("adultSourcesConfirmed")])
            p[stringPreferencesKey("enabledSources")] = initial.enabled
            p[booleanPreferencesKey("adultSourcesConfirmed")] = initial.confirmed
            p[intPreferencesKey("sourceConsentVersion")] = 1
        }
    }
    val flow = flow {
        initializeSources()
        emitAll(preferences.data.map { p ->
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
                preloadPages = (p[intPreferencesKey("preloadPages")] ?: 10).coerceIn(0, 10),
                readerCacheMb = (p[intPreferencesKey("readerCacheMb")] ?: 64).coerceIn(32, 256),
                gapWarnings = p[booleanPreferencesKey("gapWarnings")] ?: true,
                libraryCheckHours = (p[intPreferencesKey("libraryCheckHours")] ?: 0).takeIf { it in listOf(0, 12, 24) } ?: 0,
                checkWifi = p[booleanPreferencesKey("checkWifi")] ?: true,
                checkAuthors = p[booleanPreferencesKey("checkAuthors")] ?: false,
                notifyChapterUpdates = p[booleanPreferencesKey("notifyChapterUpdates")] ?: false,
                libraryCheckStatus = p[stringPreferencesKey("libraryCheckStatus")] ?: "Automatic checks are off. The first check establishes a baseline.",
                enabledSources = p[stringPreferencesKey("enabledSources")] ?: SourceConsentPolicy.defaultEnabled,
                savedSearches = p[stringPreferencesKey("savedSearches")] ?: "[]",
                exportFolder = p[stringPreferencesKey("exportFolder")] ?: "",
                backupFolder = p[stringPreferencesKey("backupFolder")] ?: "",
                backupDays = (p[intPreferencesKey("backupDays")] ?: 0).takeIf { it in setOf(0, 1, 7) } ?: 0,
                backupRetention = (p[intPreferencesKey("backupRetention")] ?: 7).takeIf { it in setOf(3, 7, 14) } ?: 7,
                backupStatus = p[stringPreferencesKey("backupStatus")] ?: "No automatic backup has run.",
                backupLastSuccess = p[longPreferencesKey("backupLastSuccess")] ?: 0,
                adultSourcesConfirmed = p[booleanPreferencesKey("adultSourcesConfirmed")] ?: false,
            )
        })
    }

    suspend fun set(key: String, value: Boolean) {
        preferences.edit { it[booleanPreferencesKey(key)] = value }
    }

    suspend fun number(key: String, value: Int) {
        val range = when (key) {
            "brightness" -> -1..100
            "dim" -> 0..80
            "preloadPages" -> 0..10
            "readerCacheMb" -> 32..256
            "libraryCheckHours" -> 0..24
            else -> error("Unknown numeric preference")
        }
        require(value in range && (key != "libraryCheckHours" || value in listOf(0, 12, 24)))
        preferences.edit { it[intPreferencesKey(key)] = value }
    }

    suspend fun choice(key: String, value: String) {
        val choices = when (key) {
            "theme" -> setOf("legacy", "system", "light", "dark")
            "orientation" -> setOf("auto", "portrait", "landscape", "locked")
            else -> error("Unknown choice preference")
        }
        require(value in choices)
        preferences.edit { it[stringPreferencesKey(key)] = value }
    }

    suspend fun configureBackup(folder: String, days: Int, retention: Int) {
        require(folder.isEmpty() || folder.startsWith("content://") && folder.length <= 4096)
        require(days in setOf(0, 1, 7) && retention in setOf(3, 7, 14))
        preferences.edit {
            it[stringPreferencesKey("backupFolder")] = folder
            it[intPreferencesKey("backupDays")] = if (folder.isEmpty()) 0 else days
            it[intPreferencesKey("backupRetention")] = retention
        }
    }

    suspend fun backupResult(status: String, success: Long? = null) {
        preferences.edit {
            it[stringPreferencesKey("backupStatus")] = status.take(2000)
            if (success != null) it[longPreferencesKey("backupLastSuccess")] = success
        }
    }

    suspend fun exportFolder(value: String) {
        require(value.isEmpty() || value.startsWith("content://") && value.length <= 4096)
        preferences.edit { it[stringPreferencesKey("exportFolder")] = value }
    }

    suspend fun saveSearch(row: SavedSearch) {
        preferences.edit { p ->
            val rows = savedSearches(p[stringPreferencesKey("savedSearches")] ?: "[]")
            p[stringPreferencesKey("savedSearches")] = savedSearchJson(rows.filter { it.id != row.id } + row)
        }
    }

    suspend fun removeSearch(id: String) {
        preferences.edit { p -> p[stringPreferencesKey("savedSearches")] = savedSearchJson(savedSearches(p[stringPreferencesKey("savedSearches")] ?: "[]").filter { it.id != id }) }
    }

    suspend fun checkStatus(value: String) {
        preferences.edit { it[stringPreferencesKey("libraryCheckStatus")] = value.take(2000) }
    }

    suspend fun enableSource(key: String, enabled: Boolean) {
        require(key in sourceNames)
        initializeSources()
        preferences.edit { p ->
            check(!SourceConsentPolicy.needsConfirmation(key, enabled,
                p[booleanPreferencesKey("adultSourcesConfirmed")] == true)) { "Confirm you are 18 or older before enabling adult sources." }
            val current = p[stringPreferencesKey("enabledSources")].orEmpty().split(',').filter { it in sourceNames }.toMutableSet()
            if (enabled) current.add(key) else current.remove(key)
            p[stringPreferencesKey("enabledSources")] = sourceNames.keys.filter { it in current }.joinToString(",")
        }
    }

    suspend fun confirmAdultSource(key: String) {
        require(key in SourceConsentPolicy.adultSources)
        initializeSources()
        preferences.edit { p ->
            val current = p[stringPreferencesKey("enabledSources")].orEmpty().split(',').toSet() + key
            p[booleanPreferencesKey("adultSourcesConfirmed")] = true
            p[stringPreferencesKey("enabledSources")] = sourceNames.keys.filter { it in current }.joinToString(",")
        }
    }

    suspend fun retrySeconds(value: Int) {
        require(value in listOf(0, 3, 5, 10, 30))
        preferences.edit { it[intPreferencesKey("retrySeconds")] = value }
    }

    suspend fun sources(value: String) {
        preferences.edit { it[stringPreferencesKey("sources")] = value }
    }

    suspend fun language(value: String) {
        preferences.edit { it[stringPreferencesKey("language")] = value }
    }
}

fun enabledSourceOrder(settings: Settings): List<String> = sourceOrder(settings.sources).filter { it in settings.enabledSources.split(',') }
