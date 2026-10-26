package app.komori.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class SourceAccessTest {
    @Test fun rawAndTranslatedSourcesChooseTheirOwnLanguage() {
        assertEquals("ko", sourceReadingLanguage(sourceId("manhwa18cc", "/webtoon/fixture-raw"), "en"))
        assertEquals("ko", sourceReadingLanguage(sourceId("manhwa18", "/manga/fixture-raw"), "en"))
        assertEquals("en", sourceReadingLanguage(sourceId("manhwa18", "/manga/fixture"), "ja"))
        assertEquals("*", sourceReadingLanguage(sourceId("manhwa18cc", "/webtoon/fixture-raw"), "en", "*"))
        assertEquals("ja", sourceReadingLanguage("manga-uuid", "ja"))
    }

    @Test fun liveEncryptedDnsMangaDexAndNewSourcesReachRealPages() = runBlocking {
        assumeTrue(System.getenv("KOMORI_NEW_SOURCE_SMOKE") == "1")
        val client = MangaDex.client().newBuilder().callTimeout(25, TimeUnit.SECONDS)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", chain.request().header("User-Agent") ?: "Mozilla/5.0 Komori source check").build()) }.build()
        fun json(url: String) = client.newCall(Request.Builder().url(url).header("User-Agent", "Komori/${app.komori.BuildConfig.VERSION_NAME} (Android reader)").build()).execute().use {
            assertTrue("Live HTTP ${it.code} at $url", it.isSuccessful)
            JSONObject(it.body!!.string())
        }
        val rows = json("https://api.mangadex.org/chapter?limit=25&translatedLanguage[]=en&contentRating[]=safe&includeFuturePublishAt=0&includeExternalUrl=0&order[createdAt]=desc")
            .getJSONArray("data").objects()
        val id = rows.first { it.getJSONObject("attributes").optInt("pages") > 0 }.getString("id")
        val url = MangaDex(client).pages(id, true).first()
        client.newCall(Sources(client, client).imageRequest(url, id)).execute().use {
            assertTrue("Live image HTTP ${it.code}", it.isSuccessful)
            assertTrue(it.header("Content-Type").orEmpty().startsWith("image/"))
            assertTrue(it.body!!.source().request(16))
        }
        for ((key, origin) in listOf("manhwa18" to "https://manhwa18.com", "manhwa18cc" to "https://manhwa18.cc")) {
            val source = ManhwaSource(key, origin, client)
            val first = source.browse("", 1, BrowseFilter(language = "en"))
            assertTrue("Empty catalog at $origin", first.titles.isNotEmpty())
            val book = first.titles.first()
            val detail = source.details(book)
            assertTrue(detail.title.isNotBlank())
            assertTrue(detail.cover.startsWith("https://"))
            val chapter = source.chapters(book.id).last()
            val imageUrl = source.pages(chapter.id).first()
            client.newCall(Sources(client, client).imageRequest(imageUrl, chapter.id)).execute().use {
                assertTrue("Live $key image HTTP ${it.code}", it.isSuccessful)
                assertTrue(it.header("Content-Type").orEmpty().startsWith("image/"))
                assertTrue(it.body!!.source().request(16))
            }
            assertTrue(source.tags().isNotEmpty())
            println("PASS $key catalog/detail/chapter/pages/genres")
        }
        println("PASS MangaDex API, at-home manifest and actual image with encrypted DNS")
        client.dispatcher.executorService.shutdown()
    }
}
