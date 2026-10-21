package app.komori.domain

import org.junit.Assert.*
import org.junit.Test

class ReaderContinuityTest {
    private fun chapter(id: String, number: String?, language: String = "en", readable: Boolean = true) =
        Release(id, "book", number, null, "", language, emptyList(), "Group", if (readable) 10 else 0)

    @Test fun warnsOnlyForKnownForwardNumericGapsWithinTheSameTranslation() {
        assertNotNull(ReaderContinuity.gap(chapter("a", "1"), chapter("b", "4")))
        assertNull(ReaderContinuity.gap(chapter("a", "1"), chapter("b", "2")))
        assertNull(ReaderContinuity.gap(chapter("a", "1"), chapter("b", "1.5")))
        assertNull(ReaderContinuity.gap(chapter("a", null), chapter("b", "4")))
        assertNull(ReaderContinuity.gap(chapter("a", "1"), chapter("b", "4", "ja")))
        assertNull(ReaderContinuity.gap(chapter("a", "4"), chapter("b", "1")))
    }

    @Test fun previousSkipsUnavailableAndSameLogicalReleasesAndRejectsMissingCurrent() {
        val first = chapter("first", "1")
        val unavailable = chapter("unavailable", "2", readable = false)
        val duplicate = chapter("duplicate", "3")
        val current = chapter("current", "3")
        val rows = listOf(first, unavailable, duplicate, current)
        assertEquals(first, ReaderContinuity.previous(rows, current))
        assertNull(ReaderContinuity.previous(rows, chapter("missing", "10")))
        assertNull(ReaderContinuity.previous(rows, first))
    }
}
