package app.komori.ui

import app.komori.data.*
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** One operation owns state and every callback; visibility/snooze are orthogonal to transfer state. */
internal class UpdateOperations(
    private val scope: CoroutineScope,
    private val backend: UpdateBackend,
    private val installedVersion: String,
    private val automaticDownloadEnabled: suspend () -> Boolean,
    private val monotonic: () -> Long,
    private val wallTime: () -> Long = System::currentTimeMillis,
) {
    val state = MutableStateFlow(backend.cachedUpdate().let {
        UpdateState(update = it, phase = if (it == null) UpdatePhase.IDLE else UpdatePhase.AVAILABLE,
            channel = backend.channel(), snoozedUntil = backend.snoozedUntil())
    })
    private val generation = AtomicLong()
    private var lastCheck = -60_000L
    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    fun check(manual: Boolean = false) {
        val old = state.value
        if (old.checking || old.downloading) return
        if (!manual && (old.file != null || !backend.automaticCheckAllowed() || monotonic() - lastCheck < 30_000)) return
        val token = generation.incrementAndGet()
        lastCheck = monotonic()
        backend.recordAttempt()
        state.value = old.copy(phase = UpdatePhase.CHECKING, message = "Checking GitHub…",
            snoozedUntil = old.snoozedUntil.takeIf { it > wallTime() } ?: 0)
        checkJob = scope.launch {
            try {
                val update = backend.check()
                if (generation.get() != token) return@launch
                backend.recordSuccess()
                val ready = old.file?.takeIf { update?.sameArtifact(old.update) == true }
                state.update { current -> current.copy(update = update, file = ready, percent = if (ready == null) 0 else 100,
                    phase = if (ready != null) UpdatePhase.READY else if (update != null) UpdatePhase.AVAILABLE else UpdatePhase.IDLE,
                    show = current.show || manual && update != null,
                    message = if (update == null) "You're up to date ($installedVersion)." else "Version ${update.version} is available.") }
                if (update != null && ready == null && (manual || backend.snoozedUntil() <= wallTime()) &&
                    backend.canDownload && backend.unmetered() && automaticDownloadEnabled() && generation.get() == token)
                    download(true)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (generation.get() == token) state.update { current -> current.copy(
                    phase = if (old.file != null) UpdatePhase.READY else UpdatePhase.FAILED,
                    message = "Couldn't check for updates. ${error.message.orEmpty()}") }
            }
        }
    }

    fun download(unmeteredOnly: Boolean = false) {
        val current = state.value
        val update = current.update ?: return
        if (current.checking || current.downloading || current.file != null) return
        if (!backend.canDownload) { message("In-app installation is available in signed release builds."); return }
        val token = generation.incrementAndGet()
        state.value = current.copy(phase = UpdatePhase.DOWNLOADING, file = null, percent = 0, message = "Downloading update…")
        downloadJob = scope.launch {
            try {
                val file = backend.download(update, unmeteredOnly) { percent ->
                    state.update { value -> if (generation.get() == token && value.downloading && update.sameArtifact(value.update))
                        value.copy(percent = percent.coerceIn(0, 100)) else value }
                }
                if (generation.get() == token && update.sameArtifact(state.value.update))
                    state.update { it.copy(file = file, phase = UpdatePhase.READY, percent = 100,
                        message = "Verified update ready. Android will ask you to confirm installation.") }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (generation.get() == token && update.sameArtifact(state.value.update))
                    state.update { it.copy(phase = UpdatePhase.FAILED, file = null, message = "Update failed: ${error.message.orEmpty()}") }
            }
        }
    }

    fun channel(value: String) {
        if (state.value.downloading || value == state.value.channel) return
        generation.incrementAndGet()
        checkJob?.cancel()
        backend.channel(value)
        state.value = UpdateState(channel = value, snoozedUntil = backend.snoozedUntil())
        check(true)
    }
    fun snooze() { backend.snooze(); state.update { it.copy(show = false, snoozedUntil = backend.snoozedUntil()) } }
    fun dismiss() { state.update { it.copy(show = false) } }
    fun show() { state.update { it.copy(show = true) } }
    fun message(text: String) { state.update { it.copy(message = text) } }
    fun close() { generation.incrementAndGet(); checkJob?.cancel(); downloadJob?.cancel() }
}
