package app.komori.data

import org.junit.Assert.*
import org.junit.Test

class PageContentIdentityTest {
    @Test fun nhentaiMediaPathsSurviveDeliveryRotationButContentOrderCountAndExtensionChange() {
        val first = listOf("https://i.nhentai.net/galleries/123/1.jpg", "https://i.nhentai.net/galleries/123/2.png")
        val rotated = first.map { it.replace("i.nhentai.net", "i3.nhentai.net") }
        assertEquals(PageContentIdentity.manifest("nhentai", first), PageContentIdentity.manifest("nhentai", rotated))
        assertTrue(PageContentIdentity.legacyCompatible("nhentai", first.joinToString("\n"), rotated))
        for (changed in listOf(rotated.reversed(), rotated.take(1), rotated.map { it.replace("123", "124") }, rotated.map { it.replace(".jpg", ".webp") }))
            assertNotEquals(PageContentIdentity.manifest("nhentai", first), PageContentIdentity.manifest("nhentai", changed))
    }
    @Test fun parsedNhentaiFixtureDemonstratesTheOldHostSensitiveIdentity() {
        val source = WebSource("nhentai", "https://nhentai.net", okhttp3.OkHttpClient())
        val fixture = checkNotNull(javaClass.getResource("/sources/nhentai-reader.html")).readText()
            .replace("images.example.com", "i.nhentai.net").replace("2.webp.webp", "2.webp")
        val first = source.parsePages(org.jsoup.Jsoup.parse(fixture, "https://nhentai.net/g/9/1/"))
        val rotated = source.parsePages(org.jsoup.Jsoup.parse(fixture.replace("i.nhentai.net", "i3.nhentai.net"), "https://nhentai.net/g/9/1/"))
        assertNotEquals(first.joinToString("\n"), rotated.joinToString("\n"))
        assertEquals(PageContentIdentity.manifest("nhentai", first), PageContentIdentity.manifest("nhentai", rotated))
    }
    @Test fun otherSourcesSignedQueriesAndUnrecognizedHostsRemainExact() {
        for (source in listOf("nhentai", "zap", "demonic", "weeb", "manhwa18", "manhwa18cc")) {
            val value = "https://i.nhentai.net/galleries/123/1.jpg?token=first"
            assertNotEquals(PageContentIdentity.page(source, value), PageContentIdentity.page(source, value.replace("first", "second")))
        }
        assertNotEquals(PageContentIdentity.page("zap", "https://i.nhentai.net/galleries/123/1.jpg"), PageContentIdentity.page("zap", "https://i3.nhentai.net/galleries/123/1.jpg"))
        assertEquals("https://evil.invalid/galleries/123/1.jpg", PageContentIdentity.page("nhentai", "https://evil.invalid/galleries/123/1.jpg"))
    }
    @Test fun mangadexQualityIsExplicitAndUnqualifiedLegacyPagesAreNotTrusted() {
        val urls = listOf("https://cdn.example/data/hash/1.jpg")
        assertFalse(PageContentIdentity.legacyCompatible("mangadex", "hash/1.jpg", urls))
        assertFalse(PageContentIdentity.legacyCompatible("mangadex", "hash/1.jpg", urls.map { it.replace("/data/", "/data-saver/") }))
        assertTrue(PageContentIdentity.legacyCompatible("mangadex", "/data/hash/1.jpg", urls))
        assertNotEquals(PageContentIdentity.manifest("mangadex", urls), PageContentIdentity.manifest("mangadex", urls.map { it.replace("/data/", "/data-saver/") }))
        assertFalse(PageContentIdentity.legacyCompatible("mangadex", "old/1.jpg", urls))
    }
}
