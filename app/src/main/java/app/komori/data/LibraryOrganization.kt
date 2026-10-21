package app.komori.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "library_categories")
data class LibraryCategory(@PrimaryKey val id: String, val name: String)

@Entity(tableName = "category_memberships", primaryKeys = ["mangaId", "categoryId"], indices = [Index("categoryId")])
data class CategoryMembership(val mangaId: String, val categoryId: String)

@Entity(tableName = "chapter_index", indices = [Index("mangaId"), Index("chapterKey")])
data class ChapterIndex(@PrimaryKey val releaseId: String, val mangaId: String, val chapterKey: String, val language: String, val active: Boolean = true)

data class LibraryCount(val mangaId: String, val total: Int, val unread: Int)

@Entity(tableName = "library_updates", primaryKeys = ["mangaId", "releaseId"], indices = [Index("detectedAt")])
data class LibraryUpdate(val mangaId: String, val releaseId: String, val label: String, val detectedAt: Long, val kind: String = "chapter", val seen: Boolean = false)

@Entity(tableName = "creator_snapshots")
data class CreatorSnapshot(@PrimaryKey val id: String, val titleIds: String, val checkedAt: Long)

@Entity(tableName = "migration_links", primaryKeys = ["fromId", "toId"])
data class MigrationLink(val fromId: String, val toId: String, val createdAt: Long)
