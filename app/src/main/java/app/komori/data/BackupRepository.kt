package app.komori.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BackupPreview(
    val backup: MetadataBackup,
    val matchingTitles: Int,
    val matchingShelf: Int,
    val matchingProgress: Int,
    val matchingCreators: Int,
    val matchingPreferences: Int,
    val reviewToken: Long = System.nanoTime(),
) {
    val saved
        get() = backup.shelf.count { it.saved }

    val liked
        get() = backup.shelf.count { it.liked }

    val unavailableSources
        get() =
            (backup.manga.map { sourceKey(it.id) } + backup.creators.map { sourceKey(it.id) })
                .filter { it !in sourceNames && it != "local" }
                .distinct()
                .size
}

/**
 * Both export snapshots and metadata merges observe one Room transaction. Files/jobs are untouched.
 */
class BackupRepository(private val database: LibraryDatabase) {
    private val dao = database.dao()

    suspend fun export(appVersion: String): ByteArray =
        withContext(Dispatchers.IO) {
            val snapshot =
                database.withTransaction {
                    if (!dao.backupCountsWithinLimits() || !dao.backupOrganizationWithinLimits())
                        throw InvalidBackup(
                            "Library metadata exceeds the supported backup entry limits."
                        )
                    if (dao.backupSizeEstimate() > MAX_BACKUP_BYTES)
                        throw InvalidBackup("Library metadata exceeds the 16 MiB backup limit.")
                    MetadataBackup(
                        System.currentTimeMillis(),
                        appVersion,
                        dao.backupManga().map { if (sourceKey(it.id) == "local") it.copy(cover = "") else it },
                        dao.backupShelf(),
                        dao.backupChapters(),
                        dao.backupProgress(),
                        dao.backupCreators(),
                        dao.backupPreferences(),
                        dao.backupCategories(), dao.backupMemberships(), dao.backupLinks(),
                    )
                }
            BackupCodec.encode(snapshot)
        }

    suspend fun preview(backup: MetadataBackup): BackupPreview =
        database.withTransaction {
            checkOwners(backup)
            val titles = dao.allMangaIds().toSet()
            val shelves = dao.backupShelf().map { it.mangaId }.toSet()
            val progress = dao.backupProgress().map { it.releaseId }.toSet()
            val creators = dao.backupCreators().map { it.id }.toSet()
            val preferences = dao.backupPreferences().map { it.mangaId }.toSet()
            BackupPreview(
                backup,
                backup.manga.count { it.id in titles },
                backup.shelf.count { it.mangaId in shelves },
                backup.progress.count { it.releaseId in progress },
                backup.creators.count { it.id in creators },
                backup.preferences.count { it.mangaId in preferences },
            )
        }

    suspend fun restore(backup: MetadataBackup, useBackupValues: Boolean) =
        withContext(Dispatchers.IO) {
            // Do not rely on a prior preview or a URI that could have changed since it was opened.
            val checked = BackupCodec.decode(BackupCodec.encode(backup))
            database.withTransaction {
                checkOwners(checked)
                val titles = dao.allMangaIds().toSet()
                val shelves = dao.backupShelf().map { it.mangaId }.toSet()
                val chapters = dao.chapterOwners().map { it.id }.toSet()
                val positions = dao.backupProgress().map { it.releaseId }.toSet()
                val creators = dao.backupCreators().map { it.id }.toSet()
                val preferences = dao.backupPreferences().map { it.mangaId }.toSet()
                checked.manga
                    .filter { useBackupValues || it.id !in titles }
                    .forEach { dao.putManga(it) }
                checked.shelf
                    .filter { useBackupValues || it.mangaId !in shelves }
                    .forEach { dao.putShelf(it) }
                dao.putChapters(checked.chapters.filter { useBackupValues || it.id !in chapters })
                checked.progress
                    .filter { useBackupValues || it.releaseId !in positions }
                    .forEach { dao.putProgress(it) }
                checked.creators
                    .filter { useBackupValues || it.id !in creators }
                    .forEach { dao.putCreator(it) }
                val categoryIds = dao.backupCategories().map { it.id }.toSet()
                checked.categories.filter { useBackupValues || it.id !in categoryIds }.forEach { dao.putCategory(it) }
                checked.memberships.forEach { dao.putMembership(it) }
                val links = dao.backupLinks().map { it.fromId to it.toId }.toSet()
                checked.links.filter { useBackupValues || (it.fromId to it.toId) !in links }.forEach { dao.putLink(it) }
                checked.preferences
                    .filter { useBackupValues || it.mangaId !in preferences }
                    .forEach { dao.putTitlePreferences(it) }
            }
        }

    private suspend fun checkOwners(backup: MetadataBackup) {
        val chapters = dao.chapterOwners().associate { it.id to it.mangaId }
        val positions = dao.backupProgress().associate { it.releaseId to it.mangaId }
        val downloads = dao.downloadOwners().associate { it.id to it.mangaId }
        val shelves = dao.backupShelf().filter { it.lastRelease != null }.groupBy { it.lastRelease }
        for ((id, owner) in
            backup.chapters.map { it.id to it.mangaId } +
                backup.progress.map { it.releaseId to it.mangaId } +
                backup.shelf.mapNotNull { row -> row.lastRelease?.let { it to row.mangaId } }) {
            if (
                chapters[id]?.let { it != owner } == true ||
                    positions[id]?.let { it != owner } == true ||
                    downloads[id]?.let { it != owner } == true ||
                    shelves[id].orEmpty().any { it.mangaId != owner }
            )
                throw InvalidBackup(
                    "A chapter identifier already belongs to another title on this device. Nothing was restored."
                )
        }
    }
}
