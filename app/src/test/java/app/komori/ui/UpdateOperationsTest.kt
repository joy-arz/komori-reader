package app.komori.ui

import app.komori.data.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class UpdateOperationsTest {
    private fun update(code: Long) = AppUpdate("99.$code.0", code, "https://example.invalid/$code.apk", "$code".padStart(64, '0'), 20)
    private class Backend(var cached: AppUpdate?) : UpdateBackend {
        override var canDownload = true
        var selected = "stable"; var snoozed = 0L; var allowed = true; var wifi = true
        var checks = 0; var downloads = 0
        var fetch: suspend () -> AppUpdate? = { cached }
        var transfer: suspend (AppUpdate, Boolean, (Int) -> Unit) -> File = { value, _, _ -> File("${value.code}.apk") }
        override fun channel() = selected
        override fun channel(value: String) { selected = value; cached = null }
        override fun cachedUpdate() = cached
        override fun snoozedUntil() = snoozed
        override fun snooze() { snoozed = 1000000L }
        override fun automaticCheckAllowed() = allowed
        override fun recordAttempt() {}
        override fun recordSuccess() {}
        override fun unmetered() = wifi
        override suspend fun check(): AppUpdate? { checks++; return fetch() }
        override suspend fun download(update: AppUpdate, unmeteredOnly: Boolean, progress: (Int) -> Unit): File {
            downloads++; return transfer(update, unmeteredOnly, progress)
        }
    }
    private fun owner(scope: CoroutineScope, backend: Backend, automatic: Boolean = false) =
        UpdateOperations(scope, backend, "0.8.5", { automatic }, { 100000 }, { 10000 })

    @Test fun cachedDownloadCannotOverlapCheckOrPublishUnderAnotherRelease() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val old = update(10); val newer = update(11); val check = CompletableDeferred<AppUpdate?>(); val download = CompletableDeferred<File>()
        val backend = Backend(old).apply { fetch = { check.await() }; transfer = { _, _, _ -> download.await() } }
        val operations = owner(scope, backend)
        try {
            operations.check(true); operations.download()
            assertEquals(0, backend.downloads); assertEquals(UpdatePhase.CHECKING, operations.state.value.phase)
            check.complete(newer); yield()
            operations.download(); operations.download(); operations.check(true)
            assertEquals(1, backend.downloads); assertEquals(1, backend.checks)
            download.complete(File("11.apk")); yield()
            assertEquals(newer, operations.state.value.update)
            assertEquals(File("11.apk"), operations.state.value.file)
            assertEquals(UpdatePhase.READY, operations.state.value.phase)
        } finally { scope.cancel() }
    }

    @Test fun channelGenerationRejectsOutOfOrderSuccessAndLateProgress() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val first = CompletableDeferred<AppUpdate?>(); val second = update(12)
        val backend = Backend(update(10)).apply { fetch = { if (checks == 1) withContext(NonCancellable) { first.await() } else second } }
        val operations = owner(scope, backend)
        try {
            operations.check(true); operations.channel("preview"); yield()
            first.complete(update(11)); yield()
            assertEquals(second, operations.state.value.update); assertEquals("preview", operations.state.value.channel)
            var callback: ((Int) -> Unit)? = null
            backend.transfer = { _, _, progress -> callback = progress; File("12.apk") }
            operations.download(); yield()
            callback!!(2)
            assertEquals(100, operations.state.value.percent)
            backend.fetch = { update(13) }; operations.check(true); yield(); callback!!(99)
            assertNull(operations.state.value.file); assertEquals(update(13), operations.state.value.update)
            assertEquals(0, operations.state.value.percent)
        } finally { first.complete(null); scope.cancel() }
    }

    @Test fun obsoleteCheckFailureDoesNotReplaceNewChannelState() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val failOld = CompletableDeferred<Unit>()
        val backend = Backend(null).apply { fetch = { if (checks == 1) withContext(NonCancellable) { failOld.await(); throw IOException("old") } else update(12) } }
        val operations = owner(scope, backend)
        try {
            operations.check(true); operations.channel("preview"); failOld.complete(Unit); yield()
            assertEquals(update(12), operations.state.value.update)
            assertEquals(UpdatePhase.AVAILABLE, operations.state.value.phase)
            assertFalse(operations.state.value.message.contains("old"))
        } finally { failOld.complete(Unit); scope.cancel() }
    }

    @Test fun failedCheckRetainsCachedArtifactAndAllowsOneManualRetryDownload() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val cached = update(20); val backend = Backend(cached).apply { fetch = { throw IOException("offline") } }
        val operations = owner(scope, backend)
        try {
            operations.check(true); yield()
            assertEquals(UpdatePhase.FAILED, operations.state.value.phase); assertEquals(cached, operations.state.value.update)
            backend.transfer = { _, _, _ -> throw IOException("transfer failed") }
            operations.download(); yield(); assertNull(operations.state.value.file)
            assertEquals(UpdatePhase.FAILED, operations.state.value.phase)
            backend.transfer = { value, _, _ -> File("${value.code}.apk") }
            operations.download(); yield(); assertEquals(UpdatePhase.READY, operations.state.value.phase)
        } finally { scope.cancel() }
    }

    @Test fun automaticDownloadsHonorUnmeteredSettingSnoozeAndBuildPolicy() = runBlocking {
        for (wifi in listOf(false, true)) for (enabled in listOf(false, true)) for (allowed in listOf(false, true)) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val backend = Backend(update(30)).apply { this.wifi = wifi; canDownload = allowed }
            val operations = owner(scope, backend, enabled)
            try {
                var unmetered: Boolean? = null
                backend.transfer = { _, value, _ -> unmetered = value; File("30.apk") }
                operations.check(true); yield()
                assertEquals(if (wifi && enabled && allowed) 1 else 0, backend.downloads)
                if (backend.downloads > 0) assertEquals(true, unmetered)
            } finally { scope.cancel() }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val backend = Backend(null).apply { fetch = { update(30) }; snoozed = 100000 }
        val operations = owner(scope, backend, true)
        try { operations.check(); yield(); assertEquals(0, backend.downloads) } finally { scope.cancel() }
    }

    @Test fun snoozingActiveTransferDoesNotCancelOrChangeItsArtifact() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val complete = CompletableDeferred<File>(); val backend = Backend(update(40)).apply { transfer = { _, _, _ -> complete.await() } }
        val operations = owner(scope, backend)
        try {
            operations.show(); operations.download(); operations.snooze(); operations.channel("preview")
            assertEquals("stable", backend.channel()); assertTrue(operations.state.value.downloading); assertFalse(operations.state.value.show)
            complete.complete(File("40.apk")); yield()
            assertEquals(File("40.apk"), operations.state.value.file); assertFalse(operations.state.value.show)
        } finally { scope.cancel() }
    }

    @Test fun cancelingCheckRestoresCachedArtifactAndIgnoresItsLateResult() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val pending = CompletableDeferred<AppUpdate?>()
        val cached = update(60)
        val backend = Backend(cached).apply { fetch = { withContext(NonCancellable) { pending.await() } } }
        val operations = owner(scope, backend)
        try {
            operations.check(true); operations.cancelCheck(); operations.download()
            assertEquals(UpdatePhase.READY, operations.state.value.phase)
            pending.complete(update(61)); yield()
            assertEquals(cached, operations.state.value.update); assertEquals(File("60.apk"), operations.state.value.file)
        } finally { pending.complete(null); scope.cancel() }
    }

    @Test fun closingOwnerIgnoresEvenNonCooperativeTransferCallbacks() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val complete = CompletableDeferred<File>(); var progress: ((Int) -> Unit)? = null
        val backend = Backend(update(50)).apply { transfer = { _, _, callback -> progress = callback; withContext(NonCancellable) { complete.await() } } }
        val operations = owner(scope, backend)
        try {
            operations.download(); operations.close(); progress!!(80); complete.complete(File("50.apk")); yield()
            assertNull(operations.state.value.file); assertEquals(0, operations.state.value.percent)
        } finally { complete.complete(File("50.apk")); scope.cancel() }
    }
}
