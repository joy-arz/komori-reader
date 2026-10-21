package app.komori.data

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class CoverLanguagesTest {
    @Test
    fun requestedCodesNormalizeSiteNamesAndIsoAliases() {
        val aliases =
            mapOf(
                "en" to listOf("English", "en", "gb"),
                "jp" to listOf("Japanese", "ja", "jp"),
                "sp" to listOf("Spanish", "es", "sp", "Spanyol"),
                "fr" to listOf("French", "fr"),
                "kr" to listOf("Korean", "ko", "kr"),
                "ch" to listOf("Chinese", "zh", "cn", "zh-Hant"),
                "de" to listOf("German", "de"),
                "ru" to listOf("Russian", "ru"),
            )
        aliases.forEach { (code, values) ->
            values.forEach { assertEquals(code, coverLanguage(it)?.code) }
        }
        assertNull(coverLanguage("unknown"))
        assertNull(coverLanguage("translated"))
    }

    @Test
    fun onlyExplicitLanguageMetadataProducesBadgesWithoutDuplicates() {
        val manga =
            Manga(
                "legacy",
                "Japanese English title",
                "",
                "",
                tagsJson =
                    """[
            {"id":"/tag/japanese/","name":"Japanese","type":"tag"},
            {"id":"/language/english/","name":"English","type":"language"},
            {"id":"en","name":"English","type":"language"},
            {"id":"/language/japanese/","name":"Japanese","type":"language"},
            {"id":"/language/unknown/","name":"Unknown","type":"language"}
        ]""",
            )
        assertEquals(listOf("en", "jp"), manga.coverLanguages().map { it.code })
        assertTrue(manga.copy(tagsJson = "[]").coverLanguages().isEmpty())
        assertTrue(manga.copy(tagsJson = "not-json").coverLanguages().isEmpty())
    }

    @Test
    fun zapImageOnlyLanguageLinkIsReadWithoutFetchingGalleryDetails() {
        val source = WebSource("zap", "https://example.com", MangaDex.client())
        val html =
            """
            <section class='hz-gallery-results'><article class='hz-gallery-card'>
            <a class='hz-gallery-card__flag' href='/language/japanese/'><img src='/flags/jp.png'></a>
            <a href='https://untrusted.example/language/english/'>English</a>
            <a class='hz-gallery-card__cover' href='/gallery/7/'><img src='/cover.jpg'></a>
            <h2 class='hz-gallery-card__title'><a href='/gallery/7/'>Fixture</a></h2>
            </article></section>
        """
        val manga = source.parseBrowse(Jsoup.parse(html, "https://example.com/")).single()
        assertEquals(listOf("jp"), manga.coverLanguages().map { it.code })
        assertEquals("/language/japanese/", manga.metadata().single().id)
    }

    @Test
    fun nhentaiLanguageClassesAreExplicitAndDoNotGuessFromTitles() {
        val source = WebSource("nhentai", "https://example.com", MangaDex.client())
        for ((classes, expected) in
            listOf(
                "lang-gb" to listOf("en"),
                "lang-jp" to listOf("jp"),
                "lang-cn" to listOf("ch"),
                "" to emptyList(),
            )) {
            val html =
                """<div class='gallery $classes'><a class='cover' href='/g/9/'>
                <img src='https://example.com/cover.jpg'><div class='caption'>English fixture</div>
                </a></div>"""
            assertEquals(
                expected,
                source
                    .parseBrowse(Jsoup.parse(html, "https://example.com/"))
                    .single()
                    .coverLanguages()
                    .map { it.code },
            )
        }
    }

    @Test
    fun imhentaiFlagNamesNormalizeButGenericImagesRemainUnknown() {
        val source = WebSource("imhentai", "https://example.com", MangaDex.client())
        for ((file, expected) in
            listOf("es.png" to listOf("sp"), "kr.png" to listOf("kr"), "flag.png" to emptyList())) {
            val html =
                """<div class='galleries'><div class='thumb'>
                <img class='thumb_flag' src='/flags/$file'>
                <div class='inner_thumb'><img src='/cover.jpg'></div>
                <h2 class='gallery_title'><a href='/gallery/8/'>Fixture</a></h2>
                </div></div>"""
            assertEquals(
                expected,
                source
                    .parseBrowse(Jsoup.parse(html, "https://example.com/"))
                    .single()
                    .coverLanguages()
                    .map { it.code },
            )
        }
    }
}
