package app.komori.data

import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class AtsumaruSourceTest {
    private fun title(adult: Boolean = false) = JSONObject().put("id", "book1").put("title", "Fixture")
        .put("isAdult", adult).put("medium", "Comic").put("type", "Manga").put("status", "Ongoing")
        .put("poster", "posters/cover.png").put("synopsis", "<p>Synopsis</p>")
    private fun page(adult: Boolean = false) = JSONObject().put("mangaPage", title(adult)
        .put("scanlators", JSONArray().put(JSONObject().put("id", "group1").put("name", "Source team"))))
    @Test fun normalAndAdultQueriesKeepNamespacesAndFiltersSeparate() = runBlocking {
        MockWebServer().use { server ->
            for (adult in listOf(false, true)) {
                val key = if (adult) "atsuadult" else "atsu"
                val source = AtsumaruSource(key, OkHttpClient(), server.url("/").toString().trimEnd('/'))
                server.enqueue(MockResponse().setBody(JSONObject().put("hits", JSONArray().put(JSONObject().put("document", title(adult))))
                    .put("found", 100).toString()))
                val result = source.browse("one & two", 2, BrowseFilter(sort = "Rating", tags = setOf("39"), category = "Manga", status = "completed"), null)
                val request = server.takeRequest().requestUrl!!
                assertEquals("/collections/manga/documents/search", request.encodedPath)
                assertEquals("one & two", request.queryParameter("q"))
                assertEquals("2", request.queryParameter("page"))
                assertEquals("mbRating:desc", request.queryParameter("sort_by"))
                val filters = request.queryParameter("filter_by")!!
                assertTrue(filters.contains("isAdult:=$adult")); assertTrue(filters.contains("medium:=Comic"))
                assertTrue(filters.contains("genreIds:=`39`")); assertTrue(filters.contains("type:=`Manga`")); assertTrue(filters.contains("status:=`Completed`"))
                assertEquals(!adult, filters.contains("Pornographic"))
                assertEquals(key, sourceKey(result.titles.single().id))
                assertEquals("https://cdn.atsu.moe/static/posters/cover.png", result.titles.single().cover)
                assertTrue(result.hasMore)
            }
        }
    }
    @Test fun fullChapterEndpointPreservesAlternatesNumbersGroupsAndManifestOrder() = runBlocking {
        MockWebServer().use { server ->
            val source = AtsumaruSource("atsu", OkHttpClient(), server.url("/").toString().trimEnd('/'))
            val id = sourceId("atsu", "/manga/book1")
            server.enqueue(MockResponse().setBody(page().toString()))
            val book = source.details(Manga(id, "Old", "", ""))
            assertEquals("Synopsis", book.description)
            assertEquals("/api/manga/page", server.takeRequest().requestUrl!!.encodedPath)
            val rows = JSONArray()
            for ((release, group) in listOf("chapter1" to "group1", "alternate1" to "group2")) rows.put(JSONObject()
                .put("id", release).put("scanlationMangaId", group).put("title", "Chapter 1.5").put("number", 1.5).put("pageCount", 2))
            server.enqueue(MockResponse().setBody(JSONObject().put("chapters", rows).toString()))
            val chapters = source.chapters(id)
            assertEquals("/api/manga/allChapters", server.takeRequest().requestUrl!!.encodedPath)
            assertEquals(2, chapters.size)
            assertEquals("1.5", chapters[0].release().number)
            assertEquals("Source team", chapters[0].release().groupNames)
            assertEquals(chapters[0].release().key, chapters[1].release().key)
            assertNotEquals(chapters[0].id, chapters[1].id)
            val pages = JSONArray().put(JSONObject().put("number", 1).put("image", "https://images.example/page.webp?signature=keep"))
                .put(JSONObject().put("number", 0).put("image", "/static/pages/book1/chapter1/0.webp"))
            server.enqueue(MockResponse().setBody(JSONObject().put("readChapter", JSONObject().put("id", "chapter1").put("pages", pages)).toString()))
            assertEquals(listOf("https://cdn.atsu.moe/static/pages/book1/chapter1/0.webp", "https://images.example/page.webp?signature=keep"), source.pages(chapters[0].id))
            assertEquals("chapter1", server.takeRequest().requestUrl!!.queryParameter("chapterId"))
        }
    }
    @Test fun wrongCatalogAndWrongChapterNeverBecomeReadableResults() = runBlocking {
        MockWebServer().use { server ->
            val source = AtsumaruSource("atsu", OkHttpClient(), server.url("/").toString().trimEnd('/'))
            server.enqueue(MockResponse().setBody(JSONObject().put("hits", JSONArray().put(JSONObject().put("document", title(true)))).put("found", 31).toString()))
            val filtered = source.browse("", 1, BrowseFilter(), null)
            assertTrue(filtered.titles.isEmpty()); assertTrue(filtered.hasMore); assertEquals(1, filtered.paginationIds.size)
            server.enqueue(MockResponse().setBody(JSONObject().put("readChapter", JSONObject().put("id", "wrong").put("pages", JSONArray())).toString()))
            try { source.pages(sourceId("atsu", "/read/book1/chapter1")); fail("Expected chapter ownership failure") } catch (_: ContentFailure) { }
            assertThrows(IllegalArgumentException::class.java) { source.imageUrl("http://untrusted.example/page.png") }
            assertEquals("https://cdn.atsu.moe/static/posters/test.webp?version=2", source.imageUrl("/static/posters/test.avif?version=2"))
        }
    }
    @Test fun defaultsAndManualOrderKeepAdultSourceOffAndConsentShared() {
        assertEquals(listOf("asura", "hive", "demonic", "weeb", "atsu"), enabledSourceOrder(Settings()))
        assertEquals(listOf("weeb", "atsu", "atsuadult"), sourceOrder("weeb,mangadex").filter { it in setOf("weeb", "atsu", "atsuadult") })
        val manual = sourceNames.keys.toList().reversed().joinToString(",")
        assertEquals(manual.split(','), sourceOrder(manual))
        assertTrue(SourceConsentPolicy.needsConfirmation("atsuadult", true, false))
        assertFalse(SourceConsentPolicy.needsConfirmation("atsu", true, false))
        assertFalse(SourceConsentPolicy.needsConfirmation("atsuadult", true, true))
        for (key in atsumaruSources) {
            assertEquals("https://atsu.moe/manga/book1", sourcePageUrl(sourceId(key, "/manga/book1")))
            assertEquals("https://atsu.moe/", Sources(OkHttpClient()).imageRequest("https://cdn.atsu.moe/static/page.webp", sourceId(key, "/read/book1/chapter1")).header("Referer"))
        }
    }
    @Test fun capturedPublicApiResponsesParseWithoutFetchingImages() = runBlocking {
        val directory = System.getenv("KOMORI_ATSU_CAPTURE")
        assumeTrue("Optional public API captures", directory != null)
        MockWebServer().use { server ->
            val source = AtsumaruSource("atsu", OkHttpClient(), server.url("/").toString().trimEnd('/'))
            fun enqueue(name: String) = server.enqueue(MockResponse().setBody(File(directory, "atsu-$name.json").readText()))
            enqueue("verified-search")
            assertTrue(source.browse("witch", 1, BrowseFilter(), null).titles.isNotEmpty())
            enqueue("detail-oldid")
            val id = sourceId("atsu", "/manga/MU6Hs")
            assertTrue(source.details(Manga(id, "", "", "")).creatorsJson != "[]")
            enqueue("chapters")
            assertEquals(152, source.chapters(id).size)
            enqueue("pages")
            assertEquals(65, source.pages(sourceId("atsu", "/read/MU6Hs/EKPq8qm5")).size)
        }
    }
}
