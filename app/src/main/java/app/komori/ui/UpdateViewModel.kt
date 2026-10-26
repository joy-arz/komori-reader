package app.komori.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.komori.BuildConfig
import app.komori.KomoriApp
import app.komori.data.AppUpdates
import app.komori.data.UpdateState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UpdateViewModel @JvmOverloads constructor(application: Application, val updater: AppUpdates = AppUpdates(application)) : AndroidViewModel(application) {
    val state = MutableStateFlow(UpdateState(update = updater.cachedUpdate(), channel = updater.channel(), snoozedUntil = updater.snoozedUntil()))
    private var lastCheck = -60_000L
    private var checkJob: kotlinx.coroutines.Job? = null
    private var checkGeneration = 0L

    fun check(manual: Boolean = false) {
        if (state.value.snoozedUntil != 0L && state.value.snoozedUntil <= System.currentTimeMillis())
            state.update { it.copy(snoozedUntil = 0) }
        val old = state.value
        if (old.checking || old.downloading) return
        // Rotation, permission screens and rapid foreground changes are not new checks.
        if (!manual && (old.file != null || !updater.automaticCheckAllowed() || SystemClock.elapsedRealtime() - lastCheck < 30_000))
            return
        lastCheck = SystemClock.elapsedRealtime()
        updater.recordAttempt()
        state.value = old.copy(checking = true, message = "Checking GitHub…")
        val generation = ++checkGeneration
        checkJob = viewModelScope.launch {
            try {
                val update = updater.check()
                if (generation != checkGeneration) return@launch
                updater.recordSuccess()
                state.value =
                    UpdateState(
                        update = update,
                        channel = updater.channel(),
                        snoozedUntil = updater.snoozedUntil(),
                        show = manual && update != null,
                        message =
                            if (update == null) "You're up to date (${BuildConfig.VERSION_NAME})."
                            else "Version ${update.version} is available.",
                    )
                if (
                    update != null &&
                        (manual || updater.snoozedUntil() <= System.currentTimeMillis()) &&
                        !BuildConfig.DEBUG &&
                        updater.unmetered() &&
                        (getApplication<KomoriApp>()).settings.flow.first().autoUpdateDownload
                )
                    download(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != checkGeneration) return@launch
                state.value =
                    old.copy(checking = false, message = "Couldn't check for updates. ${e.message.orEmpty()}")
            }
        }
    }

    fun download(unmeteredOnly: Boolean = false) {
        val update = state.value.update ?: return
        if (state.value.downloading) return
        if (BuildConfig.DEBUG) {
            state.value =
                state.value.copy(
                    message = "In-app installation is available in signed release builds."
                )
            return
        }
        state.value =
            state.value.copy(
                downloading = true,
                percent = 0,
                message = "Downloading update…",
                show = state.value.show,
            )
        viewModelScope.launch {
            try {
                val file =
                    updater.download(update, unmeteredOnly) { percent ->
                        state.update { it.copy(percent = percent) }
                    }
                state.value =
                    state.value.copy(
                        file = file,
                        downloading = false,
                        message =
                            "Verified update ready. Android will ask you to confirm installation.",
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.value =
                    state.value.copy(
                        downloading = false,
                        message = "Update failed: ${e.message.orEmpty()}",
                    )
            }
        }
    }

    fun channel(value: String) {
        if (state.value.downloading) return
        checkGeneration++
        checkJob?.cancel()
        updater.channel(value)
        state.value = UpdateState(channel = value, snoozedUntil = updater.snoozedUntil())
        check(true)
    }

    fun snooze() {
        updater.snooze()
        state.update { it.copy(show = false, snoozedUntil = updater.snoozedUntil()) }
    }

    fun message(text: String) {
        state.value = state.value.copy(message = text)
    }

    fun dismiss() {
        state.value = state.value.copy(show = false)
    }

    fun show() {
        state.value = state.value.copy(show = true)
    }
}
