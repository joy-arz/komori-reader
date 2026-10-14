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
}
