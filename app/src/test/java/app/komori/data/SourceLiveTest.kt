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
}
