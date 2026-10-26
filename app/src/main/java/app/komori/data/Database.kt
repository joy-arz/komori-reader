package app.komori.data

import androidx.room.*
import app.komori.domain.Release
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "manga")
data class Manga(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val cover: String,
    val creatorsJson: String = "[]",
    val status: String = "",
    @ColumnInfo(defaultValue = "'[]'") val tagsJson: String = "[]",
)

@Entity(tableName = "shelf")
data class Shelf(
    @PrimaryKey val mangaId: String,
    val saved: Boolean = false,
    val liked: Boolean = false,
    val status: String = "Reading",
    val lastRelease: String? = null,
    val touched: Long = 0,
)

@Entity(tableName = "chapters", indices = [Index("mangaId")])
data class Chapter(@PrimaryKey val id: String, val mangaId: String, val json: String) {
    fun release(): Release = releaseFromJson(JSONObject(json))
}

@Entity(tableName = "chapter_feeds", primaryKeys = ["mangaId", "language"])
data class ChapterFeed(
    val mangaId: String,
    val language: String,
    val releaseIds: String,
    val fetchedAt: Long,
)

@Entity(tableName = "progress", indices = [Index("mangaId"), Index("chapterKey")])
data class Progress(
    @PrimaryKey val releaseId: String,
    val mangaId: String,
    val chapterKey: String,
    val page: Int = 0,
    val completed: Boolean = false,
    @ColumnInfo(defaultValue = "0") val scroll: Float = 0f,
)

@Entity(tableName = "downloads")
data class Download(
    @PrimaryKey val releaseId: String,
    val mangaId: String,
    val label: String,
    val total: Int,
    val done: Int = 0,
    val state: String = "Queued",
    val error: String = "",
    val dataSaver: Boolean = true,
    @ColumnInfo(defaultValue = "0") val queuePosition: Long = 0,
)

@Entity(tableName = "creators")
data class Creator(@PrimaryKey val id: String, val name: String, val role: String)

@Entity(tableName = "chapter_preferences")
data class TitlePreferences(
    @PrimaryKey val mangaId: String,
    val groupId: String = "",
    val strict: Boolean = false,
    @ColumnInfo(defaultValue = "''") val readerMode: String = "",
    @ColumnInfo(defaultValue = "''") val language: String = "",
)

data class BackupChapterOwner(val id: String, val mangaId: String)

@Dao
interface LibraryDao {
    @Query("""SELECT
        (SELECT COUNT(*) FROM manga WHERE id IN (SELECT mangaId FROM shelf UNION SELECT mangaId FROM progress UNION SELECT mangaId FROM chapter_preferences)) <= 10000 AND
        (SELECT COUNT(*) FROM shelf) <= 10000 AND
        (SELECT COUNT(*) FROM chapters WHERE id IN (SELECT releaseId FROM progress UNION SELECT lastRelease FROM shelf)) <= 50000 AND
        (SELECT COUNT(*) FROM progress) <= 50000 AND
        (SELECT COUNT(*) FROM creators) <= 10000 AND
        (SELECT COUNT(*) FROM chapter_preferences) <= 10000
    """)
    suspend fun backupCountsWithinLimits(): Boolean

    @Query("""SELECT
        COALESCE((SELECT SUM(length(CAST(id || title || description || cover || creatorsJson || status || tagsJson AS BLOB))) FROM manga WHERE id IN (SELECT mangaId FROM shelf UNION SELECT mangaId FROM progress UNION SELECT mangaId FROM chapter_preferences)), 0) +
        COALESCE((SELECT SUM(length(CAST(id || mangaId || json AS BLOB))) FROM chapters WHERE id IN (SELECT releaseId FROM progress UNION SELECT lastRelease FROM shelf)), 0) +
        COALESCE((SELECT SUM(length(CAST(releaseId || mangaId || chapterKey AS BLOB))) FROM progress), 0) +
        COALESCE((SELECT SUM(length(CAST(mangaId || status || COALESCE(lastRelease, '') AS BLOB))) FROM shelf), 0) +
        COALESCE((SELECT SUM(length(CAST(id || name || role AS BLOB))) FROM creators), 0) +
        COALESCE((SELECT SUM(length(CAST(mangaId || groupId || readerMode || language AS BLOB))) FROM chapter_preferences), 0) +
        COALESCE((SELECT SUM(length(CAST(id || name AS BLOB))) FROM library_categories), 0) +
        COALESCE((SELECT SUM(length(CAST(mangaId || categoryId AS BLOB))) FROM category_memberships), 0) +
        COALESCE((SELECT SUM(length(CAST(fromId || toId AS BLOB))) FROM migration_links), 0)
    """)
    suspend fun backupSizeEstimate(): Long

    @Query("SELECT * FROM manga WHERE id IN (SELECT mangaId FROM shelf UNION SELECT mangaId FROM progress UNION SELECT mangaId FROM chapter_preferences) ORDER BY id")
    suspend fun backupManga(): List<Manga>

    @Query("SELECT id FROM manga") suspend fun allMangaIds(): List<String>
    @Query("SELECT * FROM shelf ORDER BY mangaId") suspend fun backupShelf(): List<Shelf>
    @Query("SELECT * FROM creators ORDER BY id") suspend fun backupCreators(): List<Creator>
    @Query("SELECT * FROM progress ORDER BY releaseId") suspend fun backupProgress(): List<Progress>
    @Query("SELECT * FROM chapter_preferences ORDER BY mangaId") suspend fun backupPreferences(): List<TitlePreferences>
    @Query("SELECT * FROM chapters WHERE id IN (SELECT releaseId FROM progress UNION SELECT lastRelease FROM shelf) ORDER BY id")
    suspend fun backupChapters(): List<Chapter>
    @Query("SELECT id, mangaId FROM chapters") suspend fun chapterOwners(): List<BackupChapterOwner>
    @Query("SELECT releaseId AS id, mangaId FROM downloads") suspend fun downloadOwners(): List<BackupChapterOwner>

    @Query("SELECT * FROM manga WHERE id IN (SELECT mangaId FROM shelf UNION SELECT mangaId FROM downloads UNION SELECT mangaId FROM progress) ORDER BY title") fun observeManga(): Flow<List<Manga>>

    @Query("SELECT * FROM shelf ORDER BY touched DESC") fun observeShelf(): Flow<List<Shelf>>

    @Query("SELECT * FROM creators ORDER BY name") fun observeCreators(): Flow<List<Creator>>

    @Query("SELECT * FROM downloads ORDER BY queuePosition, releaseId") fun observeDownloads(): Flow<List<Download>>
    @Query("SELECT * FROM downloads WHERE state IN ('Queued','Downloading','Retrying','Waiting for storage') ORDER BY queuePosition, releaseId")
    suspend fun orderedQueue(): List<Download>
    @Query("SELECT * FROM downloads WHERE state IN ('Queued','Downloading','Retrying','Waiting for storage') ORDER BY CASE WHEN state = 'Downloading' THEN 0 ELSE 1 END, queuePosition, releaseId LIMIT 1")
    suspend fun queueHead(): Download?
    @Query("SELECT COALESCE(MAX(queuePosition), -1) FROM downloads WHERE state IN ('Queued','Downloading','Retrying','Waiting for storage')")
    suspend fun lastQueuePosition(): Long
    @Query("UPDATE downloads SET queuePosition = :position WHERE releaseId = :id") suspend fun queuePosition(id: String, position: Long)

    @Query("SELECT * FROM progress") fun observeProgress(): Flow<List<Progress>>
    @Query("SELECT * FROM progress WHERE mangaId = :id") fun observeProgressForManga(id: String): Flow<List<Progress>>
    @Query("SELECT * FROM progress WHERE chapterKey IN (:keys)") suspend fun progressForKeys(keys: List<String>): List<Progress>

    @Query("SELECT * FROM chapter_preferences")
    fun observeTitlePreferences(): Flow<List<TitlePreferences>>

    @Query("SELECT * FROM manga WHERE id = :id") suspend fun manga(id: String): Manga?

    @Query("SELECT * FROM shelf WHERE mangaId = :id") suspend fun shelf(id: String): Shelf?

    @Query("SELECT * FROM chapters WHERE mangaId = :id")
    suspend fun chapters(id: String): List<Chapter>

    @Query("SELECT * FROM chapters WHERE id = :id") suspend fun chapter(id: String): Chapter?

    @Query("SELECT * FROM progress WHERE releaseId = :id")
    suspend fun progress(id: String): Progress?

    @Query("SELECT * FROM downloads WHERE releaseId = :id")
    suspend fun download(id: String): Download?

    @Query("SELECT * FROM chapter_preferences WHERE mangaId = :id")
    suspend fun titlePreferences(id: String): TitlePreferences?

    @Query("""SELECT (SELECT COUNT(*) FROM library_categories) <= 1000 AND
        (SELECT COUNT(*) FROM category_memberships) <= 50000 AND (SELECT COUNT(*) FROM migration_links) <= 10000""")
    suspend fun backupOrganizationWithinLimits(): Boolean
    @Query("SELECT * FROM library_categories ORDER BY name COLLATE NOCASE") fun observeCategories(): Flow<List<LibraryCategory>>
    @Query("SELECT * FROM category_memberships") fun observeMemberships(): Flow<List<CategoryMembership>>
    @Upsert suspend fun putCategory(category: LibraryCategory)
    @Upsert suspend fun putMembership(membership: CategoryMembership)
    @Query("DELETE FROM category_memberships WHERE mangaId = :id AND categoryId = :category") suspend fun removeMembership(id: String, category: String)
    @Query("DELETE FROM category_memberships WHERE categoryId = :id") suspend fun removeCategoryMemberships(id: String)
    @Query("DELETE FROM library_categories WHERE id = :id") suspend fun removeCategoryRow(id: String)
    @Transaction suspend fun deleteCategory(id: String) { removeCategoryMemberships(id); removeCategoryRow(id) }
    @Query("SELECT * FROM library_categories ORDER BY id") suspend fun backupCategories(): List<LibraryCategory>
    @Query("SELECT * FROM category_memberships ORDER BY mangaId, categoryId") suspend fun backupMemberships(): List<CategoryMembership>
    @Query("SELECT * FROM migration_links ORDER BY fromId, toId") suspend fun backupLinks(): List<MigrationLink>
    @Query("SELECT * FROM migration_links") fun observeMigrationLinks(): Flow<List<MigrationLink>>
    @Upsert suspend fun putLink(link: MigrationLink)
    @Query("""SELECT c.mangaId, c.language, COUNT(DISTINCT c.chapterKey) AS total,
        COUNT(DISTINCT CASE WHEN p.chapterKey IS NULL THEN c.chapterKey ELSE NULL END) AS unread
        FROM chapter_index c LEFT JOIN (SELECT DISTINCT chapterKey FROM progress WHERE completed = 1) p ON p.chapterKey = c.chapterKey
        WHERE c.active = 1 GROUP BY c.mangaId, c.language""")
    fun observeLibraryLanguageCounts(): Flow<List<LibraryLanguageCount>>

    fun observeLibraryCounts(language: String): Flow<List<LibraryCount>> =
        combine(observeLibraryLanguageCounts(), observeTitlePreferences()) { rows, preferences ->
            val overrides = preferences.associate { it.mangaId to it.language }
            rows.filter {
                val effective = sourceReadingLanguage(it.mangaId, language, overrides[it.mangaId].orEmpty())
                effective == "*" || effective == it.language
            }.groupBy { it.mangaId }.map { (id, counts) -> LibraryCount(id, counts.sumOf { it.total }, counts.sumOf { it.unread }) }
        }
    @Query("SELECT * FROM library_updates ORDER BY detectedAt DESC, mangaId, releaseId") fun observeLibraryUpdates(): Flow<List<LibraryUpdate>>
    @Upsert suspend fun putLibraryUpdates(rows: List<LibraryUpdate>)
    @Query("UPDATE library_updates SET seen = 1 WHERE mangaId = :id") suspend fun markUpdatesSeen(id: String)
    @Query("UPDATE library_updates SET seen = 1") suspend fun markAllUpdatesSeen()
    @Query("DELETE FROM library_updates WHERE detectedAt < :before OR rowid NOT IN (SELECT rowid FROM library_updates ORDER BY detectedAt DESC LIMIT 10000)") suspend fun trimUpdates(before: Long)
    @Query("SELECT * FROM creator_snapshots WHERE id = :id") suspend fun creatorSnapshot(id: String): CreatorSnapshot?
    @Upsert suspend fun putCreatorSnapshot(snapshot: CreatorSnapshot)

    @Upsert suspend fun putManga(manga: Manga)

    @Upsert suspend fun putShelf(shelf: Shelf)

    @Upsert suspend fun putChapterRows(chapters: List<Chapter>)
    @Upsert suspend fun putChapterIndex(rows: List<ChapterIndex>)
    @Transaction suspend fun putChapters(chapters: List<Chapter>) {
        putChapterRows(chapters)
        putChapterIndex(chapters.map { row -> row.release().let { ChapterIndex(row.id, row.mangaId, it.key, it.language, it.readable) } })
    }
    @Query("UPDATE chapter_index SET active = 0 WHERE mangaId = :id AND (:language = '*' OR language = :language)")
    suspend fun retireChapterIndex(id: String, language: String)

    @Query("SELECT * FROM chapter_feeds WHERE mangaId = :id AND language = :language")
    suspend fun feed(id: String, language: String): ChapterFeed?

    @Upsert suspend fun putFeed(feed: ChapterFeed)

    @Transaction
    suspend fun saveFeed(id: String, language: String, chapters: List<Chapter>) {
        retireChapterIndex(id, language)
        putChapters(chapters)
        putFeed(
            ChapterFeed(
                id,
                language,
                JSONArray(chapters.map { it.id }).toString(),
                System.currentTimeMillis(),
            )
        )
    }

    @Upsert suspend fun putProgress(progress: Progress)

    @Upsert suspend fun putDownload(download: Download)

    @Upsert suspend fun putCreator(creator: Creator)

    @Upsert suspend fun putTitlePreferences(preferences: TitlePreferences)

    @Query("DELETE FROM creators WHERE id = :id") suspend fun removeCreator(id: String)

    @Query("DELETE FROM downloads WHERE releaseId = :id") suspend fun removeDownload(id: String)

    @Query("DELETE FROM progress WHERE mangaId = :id") suspend fun resetProgress(id: String)
}

@Database(
    entities =
        [
            Manga::class,
            Shelf::class,
            Chapter::class,
            ChapterFeed::class,
            Progress::class,
            Download::class,
            Creator::class,
            TitlePreferences::class,
            LibraryCategory::class,
            CategoryMembership::class,
            ChapterIndex::class,
            LibraryUpdate::class,
            CreatorSnapshot::class,
            MigrationLink::class,
        ],
    version = 6,
    exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao
}

fun JSONObject.nullable(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

fun releaseFromJson(o: JSONObject): Release {
    val a = o.getJSONObject("attributes")
    val groups =
        o.getJSONArray("relationships").objects().filter {
            it.optString("type") == "scanlation_group"
        }
    return Release(
        o.getString("id"),
        o.getJSONArray("relationships")
            .objects()
            .first { it.optString("type") == "manga" }
            .getString("id"),
        a.nullable("chapter"),
        a.nullable("volume"),
        a.optString("title"),
        a.optString("translatedLanguage"),
        groups.map { it.getString("id") },
        groups
            .joinToString { it.optJSONObject("attributes")?.optString("name") ?: "Unknown group" }
            .ifEmpty { "Independent release" },
        a.optInt("pages"),
        a.nullable("externalUrl"),
        a.optBoolean("isUnavailable", false),
    )
}

val SCROLL_MIGRATION =
    object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE progress ADD COLUMN scroll REAL NOT NULL DEFAULT 0")
        }
    }

val READER_MODE_MIGRATION =
    object : androidx.room.migration.Migration(2, 3) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE chapter_preferences ADD COLUMN readerMode TEXT NOT NULL DEFAULT ''"
            )
        }
    }

val FEED_MIGRATION =
    object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS chapter_feeds (mangaId TEXT NOT NULL, language TEXT NOT NULL, releaseIds TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(mangaId, language))"
            )
        }
    }

val METADATA_MIGRATION =
    object : androidx.room.migration.Migration(4, 5) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE manga ADD COLUMN tagsJson TEXT NOT NULL DEFAULT '[]'")
        }
    }


val ORGANIZATION_MIGRATION = object : androidx.room.migration.Migration(5, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chapter_preferences ADD COLUMN language TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE downloads ADD COLUMN queuePosition INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE downloads SET queuePosition = rowid")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_progress_mangaId ON progress(mangaId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_progress_chapterKey ON progress(chapterKey)")
        db.execSQL("CREATE TABLE IF NOT EXISTS library_categories (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS category_memberships (mangaId TEXT NOT NULL, categoryId TEXT NOT NULL, PRIMARY KEY(mangaId, categoryId))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_category_memberships_categoryId ON category_memberships(categoryId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS chapter_index (releaseId TEXT NOT NULL PRIMARY KEY, mangaId TEXT NOT NULL, chapterKey TEXT NOT NULL, language TEXT NOT NULL, active INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chapter_index_mangaId ON chapter_index(mangaId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chapter_index_chapterKey ON chapter_index(chapterKey)")
        db.execSQL("CREATE TABLE IF NOT EXISTS library_updates (mangaId TEXT NOT NULL, releaseId TEXT NOT NULL, label TEXT NOT NULL, detectedAt INTEGER NOT NULL, kind TEXT NOT NULL, seen INTEGER NOT NULL, PRIMARY KEY(mangaId, releaseId))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_library_updates_detectedAt ON library_updates(detectedAt)")
        db.execSQL("CREATE TABLE IF NOT EXISTS creator_snapshots (id TEXT NOT NULL PRIMARY KEY, titleIds TEXT NOT NULL, checkedAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS migration_links (fromId TEXT NOT NULL, toId TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(fromId, toId))")
        db.query("SELECT id, mangaId, json FROM chapters").use { rows ->
            while (rows.moveToNext()) {
                val release = try { releaseFromJson(JSONObject(rows.getString(2))) }
                    catch (_: org.json.JSONException) { continue }
                    catch (_: NoSuchElementException) { continue }
                db.execSQL("INSERT OR REPLACE INTO chapter_index(releaseId,mangaId,chapterKey,language,active) VALUES(?,?,?,?,?)",
                    arrayOf(rows.getString(0), rows.getString(1), release.key, release.language, if (release.readable) 1 else 0))
            }
        }
    }
}
