package app.komori

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupRepositoryTest {
    private fun database() =
        Room.inMemoryDatabaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext,
                LibraryDatabase::class.java,
            )
            .build()

    private suspend fun seed(db: LibraryDatabase) {
        val chapter =
            Chapter(
                "backup-chapter",
                "backup-title",
                JSONObject()
                    .put("id", "backup-chapter")
                    .put(
                        "attributes",
                        JSONObject()
                            .put("chapter", "1")
                            .put("pages", 8)
                            .put("translatedLanguage", "en"),
                    )
                    .put(
                        "relationships",
                        JSONArray().put(JSONObject().put("id", "backup-title").put("type", "manga")),
                    )
                    .toString(),
            )
        db.dao().apply {
            putManga(Manga("backup-title", "Backup fixture", "Metadata only", ""))
            putShelf(Shelf("backup-title", true, true, "Reading", chapter.id, 1234))
            putChapters(listOf(chapter))
            putProgress(Progress(chapter.id, "backup-title", chapter.release().key, 3, false, .5f))
            putCreator(Creator("backup-author", "Backup author", "author"))
            putTitlePreferences(TitlePreferences("backup-title", "group-1", true, "webtoon"))
            putDownload(Download(chapter.id, "backup-title", "Ch. 1", 8, 8, "Complete"))
        }
    }

    @Test
    fun exportRestoreRoundTripExcludesFilesAndDownloadState() = runBlocking {
        val source = database()
        val target = database()
        try {
            seed(source)
            val bytes = BackupRepository(source).export("0.7.4")
            val snapshot = BackupCodec.decode(bytes)
            val preview = BackupRepository(target).preview(snapshot)
            assertEquals(0, preview.matchingTitles)
            assertEquals(1, preview.saved)
            BackupRepository(target).restore(snapshot, false)
            assertEquals(source.dao().backupManga(), target.dao().backupManga())
            assertEquals(source.dao().backupShelf(), target.dao().backupShelf())
            assertEquals(source.dao().backupProgress(), target.dao().backupProgress())
            assertEquals(source.dao().backupPreferences(), target.dao().backupPreferences())
            assertEquals(source.dao().backupCreators(), target.dao().backupCreators())
            assertEquals(
                source.dao().backupChapters().single().release(),
                target.dao().backupChapters().single().release(),
            )
            assertNull(target.dao().download("backup-chapter"))
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun mergeDefaultsKeepExistingAndOverwriteIsExplicit() = runBlocking {
        val source = database()
        val target = database()
        try {
            seed(source)
            seed(target)
            target
                .dao()
                .putShelf(Shelf("backup-title", true, false, "Completed", "backup-chapter", 9999))
            val existing =
                target.dao().progress("backup-chapter")!!.copy(page = 7, completed = true)
            target.dao().putProgress(existing)
            target
                .dao()
                .putTitlePreferences(TitlePreferences("backup-title", "group-2", false, "rtl"))
            target.dao().putCreator(Creator("backup-author", "Current name", "artist"))
            target.dao().putManga(Manga("unrelated", "Retained", "", ""))
            val repository = BackupRepository(target)
            val snapshot = BackupCodec.decode(BackupRepository(source).export("0.7.4"))
            assertEquals(1, repository.preview(snapshot).matchingProgress)
            repository.restore(snapshot, false)
            assertEquals(existing, target.dao().progress("backup-chapter"))
            assertEquals("Completed", target.dao().shelf("backup-title")!!.status)
            assertEquals("rtl", target.dao().titlePreferences("backup-title")!!.readerMode)
            assertEquals("Current name", target.dao().backupCreators().single().name)
            repository.restore(snapshot, true)
            assertEquals(3, target.dao().progress("backup-chapter")!!.page)
            assertEquals("Reading", target.dao().shelf("backup-title")!!.status)
            assertEquals("webtoon", target.dao().titlePreferences("backup-title")!!.readerMode)
            assertNotNull(target.dao().manga("unrelated"))
            assertEquals("Complete", target.dao().download("backup-chapter")!!.state)
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun databaseWriteFailureRollsBackTheWholeRestore() = runBlocking {
        val source = database()
        val target = database()
        try {
            seed(source)
            target.dao().putManga(Manga("canary", "Untouched", "", ""))
            target.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_restore BEFORE INSERT ON progress BEGIN SELECT RAISE(ABORT, 'forced write failure'); END"
            )
            val snapshot = BackupCodec.decode(BackupRepository(source).export("0.7.4"))
            var failed = false
            try {
                BackupRepository(target).restore(snapshot, true)
            } catch (e: Exception) {
                failed = true
            }
            assertTrue(failed)
            assertNull(target.dao().manga("backup-title"))
            assertNull(target.dao().shelf("backup-title"))
            assertNull(target.dao().chapter("backup-chapter"))
            assertNull(target.dao().progress("backup-chapter"))
            assertEquals("Untouched", target.dao().manga("canary")!!.title)
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun conflictingChapterOwnersCannotBeMerged() = runBlocking {
        val source = database()
        val target = database()
        try {
            seed(source)
            target.dao().putChapters(listOf(Chapter("backup-chapter", "another-title", "{}")))
            val snapshot = BackupCodec.decode(BackupRepository(source).export("0.7.4"))
            var failed = false
            try {
                BackupRepository(target).restore(snapshot, true)
            } catch (e: InvalidBackup) {
                failed = true
            }
            assertTrue(failed)
            assertNull(target.dao().manga("backup-title"))
            assertEquals("another-title", target.dao().chapter("backup-chapter")!!.mangaId)
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun existingOfflineOwnershipBlocksAConflictingRestore() = runBlocking {
        val source = database()
        val target = database()
        try {
            seed(source)
            target
                .dao()
                .putDownload(
                    Download(
                        "backup-chapter",
                        "another-title",
                        "Retained offline",
                        1,
                        1,
                        "Complete",
                    )
                )
            val snapshot = BackupCodec.decode(BackupRepository(source).export("0.7.4"))
            var failed = false
            try {
                BackupRepository(target).restore(snapshot, true)
            } catch (e: InvalidBackup) {
                failed = true
            }
            assertTrue(failed)
            assertNull(target.dao().manga("backup-title"))
            assertEquals("another-title", target.dao().download("backup-chapter")!!.mangaId)
        } finally {
            source.close()
            target.close()
        }
    }
}
