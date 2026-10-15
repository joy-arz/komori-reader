package app.komori.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in: source outages must not make ordinary builds nondeterministic. */
class SourceLiveTest {
    @Test
    fun demonicBrowseSearchChaptersAndPages() = runBlocking {
        assumeTrue(System.getenv("KOMORI_SOURCE_SMOKE") == "1")
        val source = WebSource("demonic", "https://demonicscans.org", MangaDex.client())
        val tags = source.tags()
        assertTrue(tags.any { it.name == "Action" })
        val page =
            source.browse(
                "",
                1,
                BrowseFilter("Views", setOf(tags.first { it.name == "Action" }.id)),
            )
        assertTrue(page.titles.isNotEmpty())
        val second =
            source.browse(
                "",
                2,
                BrowseFilter("Views", setOf(tags.first { it.name == "Action" }.id)),
            )
        assertNotEquals(page.titles.first().id, second.titles.first().id)
        val title =
            source.browse("Eleceed", 1, BrowseFilter()).titles.first { it.title == "Eleceed" }
        assertTrue(source.details(title).description.isNotBlank())
        val chapters = source.chapters(title.id)
        val first = chapters.first { it.release().number == "1" }
        assertTrue(chapters.size > 400)
        assertTrue(source.pages(first.id).isNotEmpty())
    }

    @Test
    fun zapBrowseSearchChaptersAndPages() = runBlocking {
        assumeTrue(System.getenv("KOMORI_SOURCE_SMOKE") == "1")
        gallerySmoke("zap", "https://hentaizap.com")
    }

    @Test
    fun imhentaiBrowseSearchChaptersAndPages() = runBlocking {
        assumeTrue(System.getenv("KOMORI_SOURCE_SMOKE") == "1")
        gallerySmoke("imhentai", "https://imhentai.xxx")
    }

    @Test
    fun nhentaiBrowseSearchChaptersAndPages() = runBlocking {
        assumeTrue(System.getenv("KOMORI_SOURCE_SMOKE") == "1")
        gallerySmoke("nhentai", "https://nhentai.net")
    }

    private suspend fun gallerySmoke(key: String, base: String) {
        val source = WebSource(key, base, MangaDex.client())
        val page = source.browse("", 1, BrowseFilter())
        assertTrue(page.titles.isNotEmpty())
        val second = source.browse("", 2, BrowseFilter())
        assertTrue(second.titles.isNotEmpty())
        assertNotEquals(page.titles.map { it.id }, second.titles.map { it.id })
        val title = page.titles.first()
        assertTrue(source.details(title).description.isNotBlank())
        val search = source.browse(title.title, 1, BrowseFilter())
        assertTrue(search.titles.any { it.id == title.id })
        val chapters = source.chapters(title.id)
        assertEquals(1, chapters.size)
        assertTrue(source.pages(chapters.single().id).isNotEmpty())
    }
}
