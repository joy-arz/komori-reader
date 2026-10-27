package app.komori.data

import java.io.File

/** Transport/policy boundary; real APK verification remains owned by AppUpdates. */
interface UpdateBackend {
    val canDownload: Boolean
    fun channel(): String
    fun channel(value: String)
    fun cachedUpdate(): AppUpdate?
    fun snoozedUntil(): Long
    fun snooze()
    fun automaticCheckAllowed(): Boolean
    fun recordAttempt()
    fun recordSuccess()
    fun unmetered(): Boolean
    suspend fun check(): AppUpdate?
    suspend fun download(update: AppUpdate, unmeteredOnly: Boolean, progress: (Int) -> Unit): File
}

enum class UpdatePhase { IDLE, CHECKING, AVAILABLE, DOWNLOADING, READY, FAILED }

internal fun AppUpdate.sameArtifact(other: AppUpdate?) = other != null && copy(releaseNotes = "") == other.copy(releaseNotes = "")
