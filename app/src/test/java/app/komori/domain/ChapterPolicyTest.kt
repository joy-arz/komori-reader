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
}
