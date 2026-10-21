package app.komori

import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import app.komori.data.*
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ScheduledBackupTest {
    @Test fun documentProviderWorkerVerifiesRotatesAndRestoresMetadata() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as KomoriApp
        val tree = DocumentsContract.buildTreeDocumentUri("${app.packageName}.backup-fixtures", "root")
        app.grantUriPermission(app.packageName, tree,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        val folder = DocumentBackupFolder(app, tree)
        val old = app.settings.flow.first()
        val id = "scheduled-${UUID.randomUUID()}"
        val target = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).build()
        try {
            folder.list().forEach { folder.delete(it.id) }
            folder.create("manual.json", byteArrayOf(1, 2, 3))
            app.dao.putManga(Manga(id, "Scheduled backup fixture", "", ""))
            app.dao.putShelf(Shelf(id, saved = true, liked = true))
            app.settings.configureBackup(tree.toString(), 1, 3)
            repeat(4) {
                val worker = TestListenableWorkerBuilder<MetadataBackupWorker>(app).build()
                assertEquals(ListenableWorker.Result.success(), worker.doWork())
            }
            val documents = folder.list()
            assertEquals(4, documents.size)
            assertTrue(documents.any { it.name == "manual.json" })
            val bytes = folder.read(documents.first { it.name.startsWith("komori-auto-") }.id)
            val snapshot = BackupCodec.decode(bytes)
            BackupRepository(target).restore(snapshot, false)
            assertTrue(target.dao().shelf(id)!!.liked)
            assertTrue(target.dao().shelf(id)!!.saved)
            assertTrue(app.settings.flow.first().backupStatus.contains("verified"))
            assertTrue(app.settings.flow.first().backupLastSuccess > 0)
            app.settings.configureBackup(tree.toString(), 0, 3)
            val before = folder.list().map { it.id }.toSet()
            assertEquals(ListenableWorker.Result.success(), TestListenableWorkerBuilder<MetadataBackupWorker>(app).build().doWork())
            assertEquals(before, folder.list().map { it.id }.toSet())
            assertEquals(ListenableWorker.Result.success(), TestListenableWorkerBuilder<MetadataBackupWorker>(app)
                .setInputData(Data.Builder().putBoolean("manual", true).build()).build().doWork())
            assertNotEquals(before, folder.list().map { it.id }.toSet())
        } finally {
            target.close()
            folder.list().forEach { folder.delete(it.id) }
            app.revokeUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            app.settings.configureBackup(old.backupFolder, old.backupDays, old.backupRetention)
            app.settings.backupResult(old.backupStatus, old.backupLastSuccess)
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM shelf WHERE mangaId = ?", arrayOf(id))
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM manga WHERE id = ?", arrayOf(id))
        }
    }
}
