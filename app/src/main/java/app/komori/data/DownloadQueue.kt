package app.komori.data

import android.content.Context
import androidx.work.*
import app.komori.KomoriApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** Queue order belongs to Room. Only the head needs a new WorkManager request. */
suspend fun kickDownloadQueue(context: Context) {
    val app = context.applicationContext as KomoriApp
    val manager = WorkManager.getInstance(app)
    val next = app.dao.queueHead()
    if (next == null) {
        manager.cancelUniqueWork("download-queue-recovery")
        return
    }
    val settings = app.settings.flow.first()
    manager.enqueueUniqueWork("download-${next.releaseId}", ExistingWorkPolicy.KEEP, downloadRequest(next.releaseId, settings))
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
