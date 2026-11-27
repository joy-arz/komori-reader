package app.komori.ui

import app.komori.data.Download
import app.komori.data.LibraryCount
import app.komori.data.Manga
import app.komori.data.Shelf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFiltersTest {
    private val first = Manga("asura:first", "First", "", "")
    private val second = Manga("hive:second", "Second", "", "")
    private val third = Manga("mangadex:third", "Third", "", "")
    private val titles = listOf(first, second, third)
    private val shelf = listOf(
        Shelf(first.id, saved = true, liked = true, status = "Reading"),
        Shelf(second.id, saved = true, liked = false, status = "Completed"),
        Shelf(third.id, saved = true, liked = true, status = "Reading"),
    ).associateBy { it.mangaId }
    private val counts = listOf(
        LibraryCount(first.id, 10, 4),
        LibraryCount(second.id, 8, 0),
        LibraryCount(third.id, 12, 2),
    ).associateBy { it.mangaId }
    private val categories = mapOf(
        first.id to setOf("action", "favorites"),
        second.id to setOf("romance"),
        third.id to emptySet(),
    )

    private fun ids(
        filters: LibraryFilters,
        downloaded: Set<String> = setOf(first.id),
    ): List<String> = filterLibraryTitles(titles, shelf, counts, downloaded, categories, filters).map { it.id }

    @Test
    fun emptyFiltersKeepEveryTitle() {
        assertEquals(listOf(first.id, second.id, third.id), ids(LibraryFilters()))
    }

    @Test
    fun downloadFilterUsesCompletedDownloadRecords() {
        val downloads = listOf(
            Download("chapter-a", first.id, "Chapter 1", 4, 4, "Complete"),
            Download("chapter-b", second.id, "Chapter 1", 4, 0, "Complete"),
            Download("chapter-c", third.id, "Chapter 1", 4, 2, "Needs repair"),
        )
        assertEquals(setOf(first.id, third.id), downloadedMangaIds(downloads))
        assertEquals(listOf(second.id), ids(LibraryFilters(download = LibraryDownloadFilter.NOT_DOWNLOADED), setOf(first.id, third.id)))
        assertEquals(listOf(first.id, third.id), ids(LibraryFilters(download = LibraryDownloadFilter.DOWNLOADED), setOf(first.id, third.id)))
    }

    @Test
    fun groupsAreAndedWhileStatusesAndSourcesAreOred() {
        val filters = LibraryFilters(
            statuses = setOf("Reading", "Completed"),
            sources = setOf("asura", "hive"),
            favoritesOnly = true,
        )
        assertEquals(listOf(first.id), ids(filters))
        assertEquals(
            listOf(first.id, third.id),
            ids(filters.copy(sources = setOf("asura", "mangadex"))),
        )
    }

    @Test
    fun multipleLibrariesAreOredAndUncategorizedIsSupported() {
        assertEquals(
            listOf(first.id, second.id),
            ids(LibraryFilters(categories = setOf("action", "romance"))),
        )
        assertEquals(
            listOf(first.id, third.id),
            ids(LibraryFilters(categories = setOf("action", UNCATEGORIZED_LIBRARY))),
        )
    }

    @Test
    fun unreadAndFavoriteFiltersCanBeCombined() {
        assertEquals(
            listOf(first.id),
            ids(LibraryFilters(favoritesOnly = true, unreadOnly = true, download = LibraryDownloadFilter.DOWNLOADED)),
        )
    }

    @Test
    fun filtersCanBeSavedAndRestored() {
        val original = LibraryFilters(
            download = LibraryDownloadFilter.NOT_DOWNLOADED,
            statuses = setOf("Reading", "On hold"),
            categories = setOf("action", UNCATEGORIZED_LIBRARY),
            sources = setOf("asura", "hive"),
            favoritesOnly = true,
            unreadOnly = true,
        )
        val saved = with(LibraryFiltersSaver) {
            androidx.compose.runtime.saveable.SaverScope { true }.save(original)
        }
        val restored = saved?.let { LibraryFiltersSaver.restore(it) }
        assertEquals(original, restored)
    }

    @Test
    fun staleCategoryRemovalKeepsOtherFilters() {
        val filters = LibraryFilters(
            categories = setOf("deleted", "kept"),
            statuses = setOf("Reading"),
            unreadOnly = true,
        )
        val updated = filters.copy(categories = filters.categories - "deleted")
        assertTrue("deleted" !in updated.categories)
        assertEquals(setOf("kept"), updated.categories)
        assertEquals(setOf("Reading"), updated.statuses)
        assertTrue(updated.unreadOnly)
    }
}
