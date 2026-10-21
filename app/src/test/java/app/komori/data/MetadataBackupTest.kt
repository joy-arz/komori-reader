package app.komori.data

import java.io.ByteArrayInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MetadataBackupTest {
    private fun fixture(): MetadataBackup {
        val chapter =
            Chapter(
                "chapter-1",
                "title-1",
                JSONObject()
                    .put("id", "chapter-1")
                    .put(
                        "attributes",
                        JSONObject()
                            .put("chapter", "1")
                            .put("volume", JSONObject.NULL)
                            .put("title", "Opening")
                            .put("translatedLanguage", "en")
                            .put("pages", 10),
                    )
                    .put(
                        "relationships",
                        JSONArray().put(JSONObject().put("id", "title-1").put("type", "manga")),
                    )
                    .toString(),
            )
        return MetadataBackup(
            1_792_560_000_000,
            "0.7.4",
            listOf(
                Manga(
                    "title-1",
                    "🦇 日本語",
                    "Line one\nLine two",
                    "https://images.example/1.jpg",
                    """[{"id":"creator-1","name":"Creator","role":"artist"}]""",
                    "ongoing",
                    """[{"id":"/tag/adventure/","name":"Adventure"}]""",
                )
            ),
            listOf(Shelf("title-1", true, true, "Reading", "chapter-1", 123)),
            listOf(chapter),
            listOf(Progress("chapter-1", "title-1", chapter.release().key, 4, false, .625f)),
            listOf(Creator("creator-1", "Creator", "artist")),
            listOf(TitlePreferences("title-1", "group-1", true, "webtoon")),
        )
    }

    private fun json(): JSONObject =
        JSONObject(String(BackupCodec.encode(fixture()), Charsets.UTF_8))

    private fun rejected(change: (JSONObject) -> Unit) {
        val root = json().also(change)
        assertThrows(InvalidBackup::class.java) {
            BackupCodec.decode(root.toString().toByteArray())
        }
    }

    @Test
    fun roundTripKeepsUserMetadataAndCanonicalChapterIdentity() {
        val initial = fixture()
        val bytes = BackupCodec.encode(initial)
        val restored = BackupCodec.read(ByteArrayInputStream(bytes))
        assertEquals(initial.shelf, restored.shelf)
        assertEquals(initial.progress, restored.progress)
        assertEquals(initial.preferences, restored.preferences)
        assertEquals(initial.creators, restored.creators)
        assertEquals(initial.chapters.single().release(), restored.chapters.single().release())
        assertEquals(initial.manga.single().title, restored.manga.single().title)
        assertEquals("tag", restored.manga.single().metadata().single().type)
        assertArrayEquals(bytes, BackupCodec.encode(restored))
        val keys = JSONObject(String(bytes)).keys().asSequence().toSet()
        assertEquals(
            setOf(
                "format",
                "version",
                "createdAt",
                "appVersion",
                "manga",
                "shelf",
                "chapters",
                "progress",
                "creators",
                "preferences", "categories", "memberships", "links",
            ),
            keys,
        )
    }

    @Test
    fun unsafeFilesTypesAndVersionsAreRejected() {
        rejected { it.put("version", 3) }
        rejected { it.put("cookies", JSONObject()) }
        rejected { it.put("downloads", JSONArray()) }
        rejected { it.remove("shelf") }
        rejected { it.getJSONArray("shelf").getJSONObject(0).put("saved", "true") }
        rejected { it.getJSONArray("progress").getJSONObject(0).put("page", 4.5) }
        rejected { it.getJSONArray("progress").getJSONObject(0).put("scroll", 1.01) }
        rejected { it.getJSONArray("shelf").getJSONObject(0).put("touched", -1) }
        rejected { it.getJSONArray("manga").getJSONObject(0).put("cover", "javascript:alert(1)") }
        rejected {
            it.getJSONArray("manga")
                .getJSONObject(0)
                .put("cover", "https://user:password@example.com/image")
        }
        rejected { it.getJSONArray("manga").getJSONObject(0).put("id", "../../escape") }
        rejected { it.getJSONArray("preferences").getJSONObject(0).put("readerMode", "unknown") }
    }

    @Test
    fun duplicatesAndBrokenRelationshipsAreRejected() {
        rejected { it.getJSONArray("manga").put(it.getJSONArray("manga").getJSONObject(0)) }
        rejected { it.getJSONArray("progress").getJSONObject(0).put("mangaId", "other-title") }
        rejected { it.getJSONArray("progress").getJSONObject(0).put("chapterKey", "other|en|?|1") }
        rejected { it.getJSONArray("chapters").getJSONObject(0).put("number", "1e10000000") }
    }

    @Test
    fun missingChapterCannotHideCrossTitleLastReadReferences() {
        rejected { root ->
            root.put("chapters", JSONArray())
            root
                .getJSONArray("manga")
                .put(
                    JSONObject(root.getJSONArray("manga").getJSONObject(0).toString())
                        .put("id", "other-title")
                )
            root
                .getJSONArray("progress")
                .getJSONObject(0)
                .put("mangaId", "other-title")
                .put("chapterKey", "other-title|en|?|1")
        }
    }

    @Test
    fun strictJsonRejectsLeniencyTruncationAndUnicodeErrors() {
        for (bad in
            listOf(
                "{format:'backup'}",
                "{\"a\":1,\"a\":2}",
                "{\"a\":01}",
                "{\"a\":true,}",
                "{\"a\":[1,]}",
                "{\"a\":NaN}",
                "{} trailing",
                "{\"a\":\"\\uD800\"}",
                "{\"a\":\"raw\nline\"}",
                "{\"a\":[",
            )) {
            assertThrows(bad, InvalidBackup::class.java) { StrictJson(bad).document() }
        }
        assertThrows(InvalidBackup::class.java) {
            BackupCodec.decode(byteArrayOf(0xC3.toByte(), 0x28))
        }
        assertThrows(InvalidBackup::class.java) {
            BackupCodec.decode(ByteArray(MAX_BACKUP_BYTES + 1))
        }
        assertThrows(InvalidBackup::class.java) {
            StrictJson("{\"a\":" + "[".repeat(30) + "0" + "]".repeat(30) + "}").document()
        }
    }

    @Test
    fun sourceIdsRoundTripButEncodedUnsafePathsAreRejected() {
        val initial = fixture()
        val id = sourceId("nhentai", "/g/12345/")
        val manga = initial.manga.single().copy(id = id)
        val backup =
            initial.copy(
                manga = listOf(manga),
                shelf = emptyList(),
                chapters = emptyList(),
                progress = emptyList(),
                preferences = emptyList(),
            )
        assertEquals(id, BackupCodec.decode(BackupCodec.encode(backup)).manga.single().id)
        for (path in listOf("//evil.example/", "/../other/", "@evil.example", "/foo\\bar")) {
            assertThrows(InvalidBackup::class.java) {
                BackupCodec.encode(
                    backup.copy(manga = listOf(manga.copy(id = sourceId("nhentai", path))))
                )
            }
        }
    }

    @Test
    fun unavailableChapterRecordsDoNotEraseRecoverableProgress() {
        val backup = fixture().copy(chapters = emptyList())
        assertEquals(backup.progress, BackupCodec.decode(BackupCodec.encode(backup)).progress)
    }

    @Test
    fun sourceRenumberingKeepsHistoricalLogicalKeysRecoverable() {
        val root = json()
        root.getJSONArray("chapters").getJSONObject(0).put("number", "2")
        val restored = BackupCodec.decode(root.toString().toByteArray())
        assertEquals("2", restored.chapters.single().release().number)
        assertEquals(fixture().progress, restored.progress)
    }

    @Test
    fun escapedOutputHitsTheByteLimitWithoutBuildingAnUnboundedString() {
        val initial = fixture()
        val description = "\u0001".repeat(262_144)
        val rows =
            (1..12).map { initial.manga.single().copy(id = "title-$it", description = description) }
        val backup =
            initial.copy(
                manga = rows,
                shelf = emptyList(),
                chapters = emptyList(),
                progress = emptyList(),
                preferences = emptyList(),
            )
        val error = assertThrows(InvalidBackup::class.java) { BackupCodec.encode(backup) }
        assertTrue(error.message.orEmpty().contains("16 MiB"))
    }
    @Test fun earlierVersionOneBackupsStillRestoreWithDefaultOrganizationAndLanguage() {
        val old = json().put("version", 1)
        old.remove("categories"); old.remove("memberships"); old.remove("links")
        old.getJSONArray("preferences").objects().forEach { it.remove("language") }
        val decoded = BackupCodec.decode(old.toString().toByteArray())
        assertEquals(fixture().preferences, decoded.preferences)
        assertTrue(decoded.categories.isEmpty())
        assertTrue(decoded.memberships.isEmpty())
    }

    @Test fun organizationAndPerTitleLanguageRoundTripWithOwnershipValidation() {
        val initial = fixture().copy(
            categories = listOf(LibraryCategory("cat-1", "Favorites")),
            memberships = listOf(CategoryMembership("title-1", "cat-1")),
            preferences = listOf(TitlePreferences("title-1", language = "ja")),
        )
        val restored = BackupCodec.decode(BackupCodec.encode(initial))
        assertEquals(initial.categories, restored.categories)
        assertEquals(initial.memberships, restored.memberships)
        assertEquals(initial.preferences, restored.preferences)
        assertThrows(InvalidBackup::class.java) {
            BackupCodec.encode(initial.copy(memberships = listOf(CategoryMembership("missing-title", "cat-1"))))
        }
    }

}
