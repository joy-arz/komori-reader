package app.komori.data

import android.content.Context
import androidx.work.*
import app.komori.KomoriApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val queueScheduling = Mutex()

/** Queue order belongs to Room. Only the head needs a new WorkManager request. */
suspend fun kickDownloadQueue(context: Context, recoverInterrupted: Boolean = false) = queueScheduling.withLock {
    val app = context.applicationContext as KomoriApp
    val manager = WorkManager.getInstance(app)
    val next = app.dao.queueHead()
    if (next == null) {
        manager.cancelUniqueWork("download-queue-recovery")
        return@withLock
    }
    val settings = app.settings.flow.first()
    val name = "download-${next.releaseId}"
    val work = withContext(Dispatchers.IO) { manager.getWorkInfosForUniqueWork(name).get() }
    val interrupted = recoverInterrupted && next.state in setOf("Queued", "Downloading") &&
        work.none { it.state == WorkInfo.State.RUNNING } &&
        work.any { it.state == WorkInfo.State.ENQUEUED }
    // Retrying rows own their server delay/backoff; foreground recovery must preserve it.
    withContext(Dispatchers.IO) {
        manager.enqueueUniqueWork(name, if (interrupted) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            downloadRequest(next.releaseId, settings)).result.get()
    }
    val recovery = PeriodicWorkRequestBuilder<DownloadQueueRecoveryWorker>(15, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(if (settings.wifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true).build()).build()
    manager.enqueueUniquePeriodicWork("download-queue-recovery", ExistingPeriodicWorkPolicy.KEEP, recovery)
}

class DownloadQueueRecoveryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        kickDownloadQueue(applicationContext)
        return Result.success()
    }
}
