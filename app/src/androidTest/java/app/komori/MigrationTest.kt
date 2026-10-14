package app.komori

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.data.LibraryDatabase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            LibraryDatabase::class.java,
        )

    @Test
    fun pageProgressSurvivesScrollUpgrade() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL(
                "INSERT INTO progress (releaseId, mangaId, chapterKey, page, completed) VALUES ('release', 'manga', 'key', 12, 1)"
            )
            close()
        }
        helper
            .runMigrationsAndValidate("migration-test", 2, true, app.komori.data.SCROLL_MIGRATION)
            .use { db ->
                db.query("SELECT page, completed, scroll FROM progress WHERE releaseId = 'release'")
                    .use {
                        assertTrue(it.moveToFirst())
                        assertEquals(12, it.getInt(0))
                        assertEquals(1, it.getInt(1))
                        assertEquals(0f, it.getFloat(2), 0f)
                    }
            }
    }

    @Test
    fun scanlationAndScrollSurviveReaderModeUpgrade() {
        helper.createDatabase("reader-mode-test", 2).apply {
            execSQL(
                "INSERT INTO chapter_preferences (mangaId, groupId, strict) VALUES ('manga', 'chosen-group', 1)"
            )
            execSQL(
                "INSERT INTO progress (releaseId, mangaId, chapterKey, page, completed, scroll) VALUES ('release', 'manga', 'key', 8, 0, 0.625)"
            )
            close()
        }
        helper
            .runMigrationsAndValidate(
                "reader-mode-test",
                3,
                true,
                app.komori.data.READER_MODE_MIGRATION,
            )
            .use { db ->
                db.query(
                        "SELECT groupId, strict, readerMode FROM chapter_preferences WHERE mangaId = 'manga'"
                    )
                    .use {
                        assertTrue(it.moveToFirst())
                        assertEquals("chosen-group", it.getString(0))
                        assertEquals(1, it.getInt(1))
                        assertEquals("", it.getString(2))
                    }
                db.query("SELECT page, scroll FROM progress WHERE releaseId = 'release'").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(8, it.getInt(0))
                    assertEquals(0.625f, it.getFloat(1), 0f)
                }
                db.execSQL(
                    "UPDATE chapter_preferences SET readerMode = 'rtl' WHERE mangaId = 'manga'"
                )
            }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db =
            androidx.room.Room.databaseBuilder(
                    context,
                    LibraryDatabase::class.java,
                    "reader-mode-test",
                )
                .build()
        try {
            val pref = kotlinx.coroutines.runBlocking { db.dao().titlePreferences("manga") }
            assertEquals("rtl", pref?.readerMode)
            assertEquals("chosen-group", pref?.groupId)
            assertEquals(true, pref?.strict)
        } finally {
            db.close()
        }
    }

    @Test
    fun originalAlphaCanUpgradeDirectlyToLatestSchema() {
        helper.createDatabase("full-upgrade-test", 1).close()
        helper
            .runMigrationsAndValidate(
                "full-upgrade-test",
                3,
                true,
                app.komori.data.SCROLL_MIGRATION,
                app.komori.data.READER_MODE_MIGRATION,
            )
            .close()
    }
}
