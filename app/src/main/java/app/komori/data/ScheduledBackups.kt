package app.komori.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.*
import app.komori.BuildConfig
import app.komori.KomoriApp
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class BackupDocument(val id: String, val name: String)

internal interface BackupFolder {
    fun create(name: String, bytes: ByteArray): String
    fun read(id: String): ByteArray
    fun list(): List<BackupDocument>
    fun delete(id: String): Boolean
}

internal class DocumentBackupFolder(private val context: Context, private val tree: Uri) : BackupFolder {
    private val resolver get() = context.contentResolver
    private val root get() = DocumentsContract.getTreeDocumentId(tree)
    private fun document(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)
    override fun create(name: String, bytes: ByteArray): String {
        val uri = DocumentsContract.createDocument(resolver, document(root), "application/json", name)
            ?: throw IOException("The backup folder could not create a document.")
        try {
            (resolver.openOutputStream(uri, "wt") ?: throw IOException("The backup document cannot be written.")).use { it.write(bytes); it.flush() }
            return DocumentsContract.getDocumentId(uri)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw e
        }
    }
    override fun read(id: String): ByteArray =
        (resolver.openInputStream(document(id)) ?: throw IOException("The saved backup cannot be verified.")).use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size().toLong() + count > MAX_BACKUP_BYTES) throw InvalidBackup("The saved backup exceeds 16 MiB.")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    override fun list(): List<BackupDocument> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, root)
        val rows = resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?: throw IOException("The backup folder cannot be listed.")
        return rows.use { cursor -> buildList { while (cursor.moveToNext()) add(BackupDocument(cursor.getString(0), cursor.getString(1))) } }
    }
    override fun delete(id: String) = DocumentsContract.deleteDocument(resolver, document(id))
}

internal object BackupRetention {
    private val ownedName = Regex("komori-auto-[0-9]{13}-[a-f0-9-]{36}\\.json")
    fun save(folder: BackupFolder, bytes: ByteArray, retain: Int, now: Long = System.currentTimeMillis()): Int {
        require(retain in setOf(3, 7, 14))
        BackupCodec.decode(bytes)
        val id = folder.create("komori-auto-${now.toString().padStart(13, '0')}-${UUID.randomUUID()}.json", bytes)
        try {
            val saved = folder.read(id)
            if (!saved.contentEquals(bytes)) throw IOException("Backup read-back verification failed. Previous backups were retained.")
            BackupCodec.decode(saved)
        } catch (e: Exception) {
            runCatching { folder.delete(id) }
            throw e
        }
        var failed = 0
        val rows = try { folder.list() } catch (_: IOException) { return 1 } catch (_: SecurityException) { return 1 }
        var kept = 1
        for (row in rows.filter { it.id != id && ownedName.matches(it.name) }.sortedByDescending { it.name }) {
            if (kept < retain) {
                try { BackupCodec.decode(folder.read(row.id)); kept++ }
                catch (_: IOException) { failed++ }
                catch (_: SecurityException) { failed++ }
            } else {
                try { if (!folder.delete(row.id)) failed++ }
                catch (_: IOException) { failed++ }
                catch (_: SecurityException) { failed++ }
            }
        }
        return failed
    }
}

object BackupScheduler {
    fun schedule(context: Context, settings: Settings) {
        val manager = WorkManager.getInstance(context)
        if (settings.backupDays == 0 || settings.backupFolder.isEmpty()) {
            manager.cancelUniqueWork("metadata-backup-periodic")
            return
        }
        val request = PeriodicWorkRequestBuilder<MetadataBackupWorker>(settings.backupDays.toLong(), TimeUnit.DAYS)
            .setInitialDelay(settings.backupDays.toLong(), TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).setRequiresBatteryNotLow(true).build()).build()
        manager.enqueueUniquePeriodicWork("metadata-backup-periodic", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
    fun now(context: Context) = WorkManager.getInstance(context).enqueueUniqueWork("metadata-backup-now", ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<MetadataBackupWorker>().setInputData(workDataOf("manual" to true))
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build()).build())
}

class MetadataBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = gate.withLock {
        val app = applicationContext as KomoriApp
        val settings = app.settings.flow.first()
        if (settings.backupFolder.isEmpty() || settings.backupDays == 0 && !inputData.getBoolean("manual", false)) return@withLock Result.success()
        try {
            val bytes = BackupRepository(app.database).export(BuildConfig.VERSION_NAME)
            val failed = withContext(Dispatchers.IO) { BackupRetention.save(DocumentBackupFolder(app, Uri.parse(settings.backupFolder)), bytes, settings.backupRetention) }
            app.settings.backupResult("Backup saved and verified." + if (failed > 0) " $failed older files could not be verified or pruned." else "", System.currentTimeMillis())
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val message = when (e) {
                is SecurityException -> "Backup folder access expired. Choose the folder again."
                is InvalidBackup -> e.message ?: "Backup validation failed."
                else -> "Backup failed. Check folder access and free storage. Previous backups were retained."
            }
            app.settings.backupResult(message)
            if (e is IOException && runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
    companion object { private val gate = Mutex() }
}
