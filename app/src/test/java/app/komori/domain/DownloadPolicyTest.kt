package app.komori.domain

import org.junit.Assert.*
import org.junit.Test

class DownloadPolicyTest {
    private fun chapter(n: Int) = Release("c$n", "title", "$n", null, "", "en", emptyList(), "", 10)

    @Test
    fun fastScrollCompletesCrossedChaptersButBackwardScrollDoesNot() {
        val chapters = (1..4).map(::chapter)
        assertEquals(
            listOf("c1", "c2", "c3"),
            ChapterPolicy.completedBetween(chapters.reversed(), "c1", "c4").map { it.id },
        )
        assertTrue(ChapterPolicy.completedBetween(chapters, "c4", "c2").isEmpty())
        assertTrue(ChapterPolicy.completedBetween(chapters, "missing", "c4").isEmpty())
    }

    @Test
    fun unreadSkipsReadCompletedAndQueuedButCanResumeFailed() {
        val chapters = (1..8).map(::chapter).reversed()
        val states =
            mapOf(
                "c2" to "Complete",
                "c3" to "Queued",
                "c4" to "Downloading",
                "c5" to "Retrying",
                "c6" to "Paused",
                "c7" to "Failed",
            )
        assertEquals(
            listOf("c6", "c7", "c8"),
            DownloadPolicy.select(chapters, setOf(chapter(1).key), states, DownloadChoice.UNREAD)
                .map { it.id },
        )
    }

    @Test
    fun nextCountAppliesAfterEligibilityAndAscendingOrder() {
        val chapters = (1..10).map(::chapter).reversed()
        assertEquals(
            listOf("c3", "c4", "c5", "c6", "c7"),
            DownloadPolicy.select(
                    chapters,
                    setOf(chapter(1).key),
                    mapOf("c2" to "Complete"),
                    DownloadChoice.NEXT_5,
                )
                .map { it.id },
        )
    }

    @Test
    fun allIncludesReadButNeverExternalUnavailableOrDuplicates() {
        val chapters =
            listOf(
                chapter(1),
                chapter(1),
                chapter(2).copy(unavailable = true),
                chapter(3).copy(externalUrl = "https://example.com"),
                chapter(4).copy(pages = 0),
                chapter(5).copy(pages = -1),
            )
        assertEquals(
            listOf("c1", "c5"),
            DownloadPolicy.select(chapters, setOf(chapter(1).key), emptyMap(), DownloadChoice.ALL)
                .map { it.id },
        )
    }
}
