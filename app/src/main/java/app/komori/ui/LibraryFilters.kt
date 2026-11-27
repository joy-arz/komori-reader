package app.komori.ui

import androidx.compose.runtime.saveable.listSaver
import app.komori.data.LibraryCount
import app.komori.data.Manga
import app.komori.data.Shelf
import app.komori.data.sourceKey

internal enum class LibraryDownloadFilter {
    ALL,
    DOWNLOADED,
    NOT_DOWNLOADED;

    val label: String
        get() = when (this) {
            ALL -> "All download states"
            DOWNLOADED -> "Downloaded"
            NOT_DOWNLOADED -> "Not downloaded"
        }
}

/** Filters are ANDed across groups and ORed inside each multi-select group. */
internal data class LibraryFilters(
    val download: LibraryDownloadFilter = LibraryDownloadFilter.ALL,
    val statuses: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val sources: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    val unreadOnly: Boolean = false,
) {
    val isEmpty: Boolean
        get() = download == LibraryDownloadFilter.ALL && statuses.isEmpty() &&
            categories.isEmpty() && sources.isEmpty() && !favoritesOnly && !unreadOnly
}

internal const val UNCATEGORIZED_LIBRARY = "uncategorized"

internal fun downloadedMangaIds(downloads: List<app.komori.data.Download>): Set<String> =
    downloads.filter { it.done > 0 }.map { it.mangaId }.toSet()

internal fun filterLibraryTitles(
    titles: List<Manga>,
    shelfById: Map<String, Shelf>,
    counts: Map<String, LibraryCount>,
    downloadedIds: Set<String>,
    categoriesByTitle: Map<String, Set<String>>,
    filters: LibraryFilters,
): List<Manga> = titles.filter { title ->
    val shelf = shelfById[title.id]
    val categories = categoriesByTitle[title.id].orEmpty()
    val downloadMatches = when (filters.download) {
        LibraryDownloadFilter.ALL -> true
        LibraryDownloadFilter.DOWNLOADED -> title.id in downloadedIds
        LibraryDownloadFilter.NOT_DOWNLOADED -> title.id !in downloadedIds
    }
    downloadMatches &&
        (filters.statuses.isEmpty() || shelf?.status in filters.statuses) &&
        (filters.categories.isEmpty() || filters.categories.any { selected ->
            if (selected == UNCATEGORIZED_LIBRARY) categories.isEmpty() else selected in categories
        }) &&
        (filters.sources.isEmpty() || sourceKey(title.id) in filters.sources) &&
        (!filters.favoritesOnly || shelf?.liked == true) &&
        (!filters.unreadOnly || (counts[title.id]?.unread ?: 0) > 0)
}

private const val FILTER_SEPARATOR = "\u001F"

internal val LibraryFiltersSaver =
    listSaver<LibraryFilters, Any>(
        save = {
            listOf(
                it.download.name,
                it.statuses.joinToString(FILTER_SEPARATOR),
                it.categories.joinToString(FILTER_SEPARATOR),
                it.sources.joinToString(FILTER_SEPARATOR),
                it.favoritesOnly,
                it.unreadOnly,
            )
        },
        restore = {
            LibraryFilters(
                download = runCatching {
                    LibraryDownloadFilter.valueOf(it[0] as String)
                }.getOrDefault(LibraryDownloadFilter.ALL),
                statuses = (it[1] as String).split(FILTER_SEPARATOR).filter(String::isNotEmpty).toSet(),
                categories = (it[2] as String).split(FILTER_SEPARATOR).filter(String::isNotEmpty).toSet(),
                sources = (it[3] as String).split(FILTER_SEPARATOR).filter(String::isNotEmpty).toSet(),
                favoritesOnly = it[4] as Boolean,
                unreadOnly = it[5] as Boolean,
            )
        },
    )
