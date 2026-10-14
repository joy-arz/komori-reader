package app.komori.data

import androidx.room.*
import app.komori.domain.Release
import kotlinx.coroutines.flow.Flow
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

@Entity(tableName = "progress")
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
)

@Entity(tableName = "creators")
data class Creator(@PrimaryKey val id: String, val name: String, val role: String)

@Entity(tableName = "chapter_preferences")
data class TitlePreferences(
    @PrimaryKey val mangaId: String,
    val groupId: String = "",
    val strict: Boolean = false,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM manga ORDER BY title") fun observeManga(): Flow<List<Manga>>

    @Query("SELECT * FROM shelf ORDER BY touched DESC") fun observeShelf(): Flow<List<Shelf>>

    @Query("SELECT * FROM creators ORDER BY name") fun observeCreators(): Flow<List<Creator>>

    @Query("SELECT * FROM downloads") fun observeDownloads(): Flow<List<Download>>

    @Query("SELECT * FROM progress") fun observeProgress(): Flow<List<Progress>>

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

    @Upsert suspend fun putManga(manga: Manga)

    @Upsert suspend fun putShelf(shelf: Shelf)

    @Upsert suspend fun putChapters(chapters: List<Chapter>)

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
            Progress::class,
            Download::class,
            Creator::class,
            TitlePreferences::class,
        ],
    version = 2,
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
    )
}

val SCROLL_MIGRATION =
    object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE progress ADD COLUMN scroll REAL NOT NULL DEFAULT 0")
        }
    }
