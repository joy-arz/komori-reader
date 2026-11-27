package app.komori.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedImageNamesTest {
    private fun draft(title: String = "A/B: C?", chapter: String = "Chapter 1", page: Int = 4) =
        SavedImages.Draft(
            mangaId = "asura:/gallery/42/",
            releaseId = "asura:/chapter/42/",
            mangaTitle = title,
            chapterLabel = chapter,
            page = page,
        )

    @Test
    fun namesKeepUsefulIdentityAndRemoveUnsafeCharacters() {
        val name = savedImageFileName(draft(), "jpg", "12345678-abcd")

        assertTrue(name.startsWith("Komori_A_B_C_Chapter_1_p5_"))
        assertTrue(name.endsWith("_12345678.jpg"))
        assertTrue("Reserved path characters must not survive", name.none { it in "\\/:*?\"<>|" })
    }

    @Test
    fun repeatedSavesOfTheSamePageGetDifferentNames() {
        val first = savedImageFileName(draft(), "png", "aaaaaaaa-first")
        val second = savedImageFileName(draft(), "png", "bbbbbbbb-second")

        assertNotEquals(first, second)
        assertTrue(first.endsWith("_aaaaaaaa.png"))
        assertTrue(second.endsWith("_bbbbbbbb.png"))
    }

    @Test
    fun emptyMetadataStillProducesABoundedName() {
        val name = savedImageFileName(draft(title = "", chapter = "", page = 0), "webp", "id")

        assertTrue(name.startsWith("Komori_Komori_p1_"))
        assertTrue(name.endsWith("_id.webp"))
        assertTrue("Names must stay below common filesystem limits", name.length <= 128)
        assertEquals(name, name.replace(Regex("_+"), "_"))
    }

    @Test
    fun longMultibyteMetadataStaysWithinFilesystemByteLimits() {
        val name =
            savedImageFileName(
                draft(
                    title = "漫画".repeat(60),
                    chapter = "章节".repeat(40),
                ),
                "jpg",
                "abcdefgh",
            )

        assertTrue(
            "The unique suffix must survive descriptive truncation",
            name.endsWith("_abcdefgh.jpg"),
        )
        assertTrue(
            "Android filenames are limited to 255 bytes",
            name.toByteArray(Charsets.UTF_8).size < 240,
        )
    }
}
