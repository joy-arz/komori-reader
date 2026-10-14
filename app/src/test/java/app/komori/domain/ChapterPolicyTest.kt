package app.komori.domain

import org.junit.Assert.*
import org.junit.Test

class ChapterPolicyTest {
    private fun chapter(
        id: String,
        number: String? = "1",
        group: String = "a",
        volume: String? = "1",
        language: String = "en",
    ) = Release(id, "manga", number, volume, "", language, listOf(group), group, 20)

    @Test
    fun `normalizes padded numbers but keeps decimal extras`() {
        val releases = listOf(chapter("a", "01"), chapter("b", "1.0"), chapter("c", "1.5"))
        assertEquals(
            listOf("a", "c"),
            ChapterPolicy.select(releases, ChapterPreferences()).map { it.id },
        )
    }

    @Test
    fun `never collapses unnumbered chapters or different volumes`() {
        val releases =
            listOf(
                chapter("a", null),
                chapter("b", null),
                chapter("c", "1", volume = "1"),
                chapter("d", "1", volume = "2"),
            )
        assertEquals(4, ChapterPolicy.select(releases, ChapterPreferences()).size)
    }

    @Test
    fun `preferred group wins and fallback fills gaps`() {
        val releases =
            listOf(
                chapter("a", group = "a"),
                chapter("b", group = "b"),
                chapter("c", "2", group = "a"),
            )
        assertEquals(
            listOf("b", "c"),
            ChapterPolicy.select(releases, ChapterPreferences(preferredGroup = "b")).map { it.id },
        )
        assertEquals(
            listOf("b"),
            ChapterPolicy.select(releases, ChapterPreferences(preferredGroup = "b", strict = true))
                .map { it.id },
        )
    }

    @Test
    fun `current release stays stable after changing preference`() {
        val releases = listOf(chapter("a", group = "a"), chapter("b", group = "b"))
        assertEquals(
            "a",
            ChapterPolicy.select(releases, ChapterPreferences(preferredGroup = "b"), "a")
                .single()
                .id,
        )
    }

    @Test
    fun `languages never get mixed`() {
        assertEquals(
            listOf("b"),
            ChapterPolicy.select(
                    listOf(chapter("a"), chapter("b", language = "id")),
                    ChapterPreferences(language = "id"),
                )
                .map { it.id },
        )
    }

    @Test
    fun `all releases mode retains duplicates`() {
        assertEquals(
            2,
            ChapterPolicy.select(
                    listOf(chapter("a"), chapter("b")),
                    ChapterPreferences(smart = false),
                )
                .size,
        )
    }

    @Test
    fun `chapter order is numeric`() {
        val result =
            ChapterPolicy.select(
                listOf(chapter("a", "10"), chapter("b", "2"), chapter("c", "2.5")),
                ChapterPreferences(),
            )
        assertEquals(listOf("2", "2.5", "10"), result.map { it.number })
    }

    @Test
    fun `resume prefers unfinished current chapter`() {
        val releases = listOf(chapter("a"), chapter("b", "2"))
        assertEquals("b", ChapterPolicy.start(releases, "b", emptySet(), false)?.id)
    }

    @Test
    fun `completed release advances to next chapter`() {
        val releases = listOf(chapter("a"), chapter("b", "2"))
        assertEquals("b", ChapterPolicy.start(releases, "a", setOf(releases[0].key), false)?.id)
    }

    @Test
    fun `caught up only restarts when enabled`() {
        val releases = listOf(chapter("a"))
        assertNull(ChapterPolicy.start(releases, "a", setOf(releases[0].key), false))
        assertEquals("a", ChapterPolicy.start(releases, "a", setOf(releases[0].key), true)?.id)
    }

    @Test
    fun `new unread chapter takes precedence over reread`() {
        val releases = listOf(chapter("a"), chapter("b", "2"))
        assertEquals("b", ChapterPolicy.start(releases, "a", setOf(releases[0].key), true)?.id)
    }

    @Test
    fun `empty feed has no reading target`() {
        assertNull(ChapterPolicy.start(emptyList(), null, emptySet(), true))
    }

    @Test
    fun `first reading ignores missing volume and descending input`() {
        val releases =
            listOf(
                chapter("late", "290", volume = null),
                chapter("first", "1"),
                chapter("extra", null),
            )
        assertEquals("first", ChapterPolicy.select(releases, ChapterPreferences()).first().id)
        assertEquals("first", ChapterPolicy.start(releases, null, emptySet(), false)?.id)
    }

    @Test
    fun `current scanlation supplies later chapters without explicit preference`() {
        val releases =
            listOf(chapter("current", "1", "b"), chapter("a", "2", "a"), chapter("b", "2", "b"))
        assertEquals(
            listOf("current", "b"),
            ChapterPolicy.select(releases, ChapterPreferences(), "current").map { it.id },
        )
    }

    @Test
    fun `unavailable preferred release cannot hide readable alternative`() {
        val releases =
            listOf(chapter("a", group = "a").copy(unavailable = true), chapter("b", group = "b"))
        assertEquals(
            "b",
            ChapterPolicy.select(releases, ChapterPreferences(preferredGroup = "a")).single().id,
        )
        assertNull(ChapterPolicy.start(listOf(releases.first()), null, emptySet(), true))
    }

    @Test
    fun `all languages retains translations and ambiguous extras`() {
        val releases =
            listOf(
                chapter("a", "extra"),
                chapter("b", "extra"),
                chapter("c", language = "id"),
                chapter("d"),
            )
        assertEquals(4, ChapterPolicy.select(releases, ChapterPreferences(language = "*")).size)
    }

    @Test
    fun `unavailable filter is independent of smart deduplication`() {
        val releases = listOf(chapter("a").copy(unavailable = true), chapter("b", "2"))
        assertEquals(2, ChapterPolicy.select(releases, ChapterPreferences(smart = false)).size)
        assertEquals(
            listOf("b"),
            ChapterPolicy.select(
                    releases,
                    ChapterPreferences(smart = false, showUnavailable = false),
                )
                .map { it.id },
        )
    }

    @Test
    fun `downloads survive empty refreshed feeds and unavailable metadata`() {
        val saved = chapter("saved", "1")
        val stale = chapter("stale", "2")
        val offline =
            retainDownloadedChapters(emptyList(), listOf(saved, stale), mapOf("saved" to 20))
        assertEquals(listOf("saved"), offline.map { it.id })
        assertTrue(offline.single().readable)
        val removed =
            retainDownloadedChapters(
                listOf(saved.copy(unavailable = true, pages = 0)),
                listOf(saved),
                mapOf("saved" to 20),
            )
        assertEquals(20, removed.single().pages)
        assertTrue(
            ChapterPolicy.select(removed, ChapterPreferences(showUnavailable = false))
                .single()
                .readable
        )
        assertEquals("saved", ChapterPolicy.start(removed, null, emptySet(), false)?.id)
    }
}
