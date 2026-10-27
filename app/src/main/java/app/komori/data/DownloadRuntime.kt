package app.komori.data

import java.io.File

/** Test fixtures can replace delivery/storage/time without replacing WorkManager scheduling. */
internal interface DownloadRuntime {
    suspend fun beforeFinish(id: String) {}
    fun source(id: String): Sources? = null
    fun availableBytes(id: String, directory: File): Long? = null
    fun elapsedMillis(id: String): Long? = null
}
