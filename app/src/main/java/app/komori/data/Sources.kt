package app.komori.data

import java.io.IOException
import java.text.Normalizer
import java.util.Base64
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

val sourceNames =
    linkedMapOf(
        "demonic" to "DemonicScans",
        "weeb" to "WeebCentral",
        "comix" to "Comix",
        "mangadex" to "MangaDex",
    )

fun sourceKey(id: String) = id.substringBefore(':', "mangadex")

fun sourcePageUrl(id: String): String =
    when (sourceKey(id)) {
        "demonic" -> "https://demonicscans.org" + sourcePath(id)
        "weeb" -> "https://weebcentral.com" + sourcePath(id)
        else -> "https://mangadex.org/title/$id"
    }

fun sourceName(id: String) = sourceNames[sourceKey(id)] ?: "Unknown source"

fun sourceOrder(value: String) =
    (value.split(',').filter { it in sourceNames } + sourceNames.keys).distinct()

fun sourceId(source: String, path: String) =
    "$source:" +
        Base64.getUrlEncoder().withoutPadding().encodeToString(path.toByteArray(Charsets.UTF_8))

fun sourcePath(id: String): String =
    String(Base64.getUrlDecoder().decode(id.substringAfter(':')), Charsets.UTF_8)

fun normalizedTitle(title: String): String =
    Normalizer.normalize(title, Normalizer.Form.NFKC).lowercase(Locale.ROOT).filter {
        it.isLetterOrDigit()
    }

fun duplicateTitles(manga: Manga, library: List<Manga>, shelf: List<Shelf>): List<Manga> {
    val saved = shelf.filter { it.saved }.map { it.mangaId }.toSet()
    val name = normalizedTitle(manga.title)
    return library.filter {
        it.id != manga.id &&
            it.id in saved &&
            name.isNotEmpty() &&
            normalizedTitle(it.title) == name
    }
}

data class BrowseFilter(val sort: String = "", val tags: Set<String> = emptySet())

data class SourceTag(val id: String, val name: String)

data class BrowsePage(val titles: List<Manga>, val hasMore: Boolean)

/** Existing MangaDex UUIDs stay unchanged; other source IDs encode paths safely for local files. */
class Sources(val client: OkHttpClient) {
    private val dex = MangaDex(client)
    private val demonic = WebSource("demonic", "https://demonicscans.org", client)
    private val weeb = WebSource("weeb", "https://weebcentral.com", client)

    private fun web(key: String) =
        when (key) {
            "demonic" -> demonic
            "weeb" -> weeb
            else ->
                throw IOException(
                    "Comix is not available yet: its protected reader could not be verified. Choose another source."
                )
        }

    fun sorts(key: String): List<String> =
        when (key) {
            "demonic" -> listOf("Views", "Recently added", "Title")
            "weeb" ->
                listOf("Popularity", "Subscribers", "Latest Updates", "Recently Added", "Alphabet")
            "mangadex" -> listOf("Popularity", "Rating", "Latest updates", "Title")
            else -> emptyList()
        }

    suspend fun browse(
        key: String,
        query: String,
        page: Int,
        filter: BrowseFilter,
        creator: Creator?,
    ): BrowsePage =
        if (key == "mangadex")
            dex.browse(query, (page - 1) * 30, creator, filter).let {
                BrowsePage(it, it.size == 30)
            }
        else web(key).browse(query, page, filter)

    suspend fun tags(key: String): List<SourceTag> =
        if (key == "mangadex") dex.tags() else web(key).tags()

    suspend fun details(manga: Manga): Manga =
        if (sourceKey(manga.id) == "mangadex") manga else web(sourceKey(manga.id)).details(manga)

    suspend fun chapters(id: String, language: String) =
        if (sourceKey(id) == "mangadex") dex.chapters(id, language)
        else web(sourceKey(id)).chapters(id)

    suspend fun languages(id: String) =
        if (sourceKey(id) == "mangadex") dex.languages(id) else listOf("en")

    suspend fun pages(id: String, saver: Boolean) =
        if (sourceKey(id) == "mangadex") dex.pages(id, saver) else web(sourceKey(id)).pages(id)

    fun imageRequest(url: String, id: String): Request =
        Request.Builder()
            .url(url)
            .apply {
                when (sourceKey(id)) {
                    "demonic" -> header("Referer", "https://demonicscans.org/")
                    "weeb" -> header("Referer", "https://weebcentral.com/")
                }
            }
            .build()
}

class WebSource(
    private val key: String,
    private val base: String,
    private val client: OkHttpClient,
) {
    private suspend fun document(path: String, form: FormBody? = null): Document =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(base + path)
                    .header("Referer", "$base/")
                    .apply { if (form != null) post(form) }
                    .build()
            client.newCall(request).execute().use {
                if (!it.isSuccessful)
                    throw IOException(
                        "${sourceNames[key]} returned HTTP ${it.code}. The source may be unavailable or require browser verification."
                    )
                val html = it.body?.string() ?: throw IOException("Empty source response")
                val doc = Jsoup.parse(html, base + path)
                if (
                    doc.title().contains("Just a moment", true) ||
                        doc.selectFirst("#cf-error-details") != null
                )
                    throw IOException(
                        "${sourceNames[key]} requires browser verification. Reading and downloads are unavailable until the source allows access."
                    )
                doc
            }
        }

    suspend fun tags(): List<SourceTag> {
        if (key == "weeb")
            return listOf(
                    "Action",
                    "Adventure",
                    "Comedy",
                    "Drama",
                    "Fantasy",
                    "Historical",
                    "Horror",
                    "Isekai",
                    "Martial Arts",
                    "Mecha",
                    "Mystery",
                    "Romance",
                    "School Life",
                    "Sci-fi",
                    "Slice of Life",
                    "Sports",
                    "Supernatural",
                )
                .map { SourceTag(it, it) }
        return document("/advanced.php").select("input[name=\"genres[]\"]").map {
            SourceTag(it.attr("value"), it.parent()!!.text())
        }
    }

    suspend fun browse(query: String, page: Int, filter: BrowseFilter): BrowsePage {
        val doc =
            if (key == "demonic") {
                if (query.isNotBlank()) {
                    if (filter.tags.isNotEmpty())
                        throw IOException(
                            "DemonicScans supports title search or genre browsing separately. Clear the title to browse tags."
                        )
                    if (page > 1) return BrowsePage(emptyList(), false)
                    document("/search.php?manga=" + java.net.URLEncoder.encode(query, "UTF-8"))
                } else {
                    val form =
                        FormBody.Builder()
                            .add("status", "all")
                            .add("submit", "Search")
                            .add(
                                "orderby",
                                when (filter.sort) {
                                    "Recently added" -> "ID DESC"
                                    "Title" -> "NAME ASC"
                                    else -> "VIEWS DESC"
                                },
                            )
                    filter.tags.forEach { form.add("genres[]", it) }
                    document("/advanced.php?list=$page", form.build())
                }
            } else {
                val url =
                    "$base/search/data"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("text", query)
                        .addQueryParameter("limit", "30")
                        .addQueryParameter("offset", ((page - 1) * 30).toString())
                        .addQueryParameter("display_mode", "Full Display")
                        .addQueryParameter("sort", filter.sort.ifEmpty { "Popularity" })
                        .addQueryParameter(
                            "order",
                            if (filter.sort == "Alphabet") "Ascending" else "Descending",
                        )
                        .addQueryParameter("adult", "False")
                filter.tags.forEach { url.addQueryParameter("included_tag", it) }
                document(url.build().toString().removePrefix(base))
            }
        val titles = parseBrowse(doc)
        return BrowsePage(
            titles,
            if (key == "demonic")
                query.isBlank() &&
                    doc.select("a[href*=list=]").any { it.text().trim().equals("Next", true) }
            else doc.selectFirst("button") != null,
        )
    }

    fun parseBrowse(doc: Document): List<Manga> =
        doc.select(
                if (key == "demonic") "a[href^=/manga/]:has(img)"
                else
                    "article section a[href*=/series/]:has(img), article section a[href*=/series/]:has(source)"
            )
            .mapNotNull { a ->
                val img = a.selectFirst("img")
                val title =
                    if (key == "demonic")
                        a.attr("title")
                            .ifBlank { img?.attr("alt").orEmpty() }
                            .ifBlank { a.selectFirst(".seach-right > div")?.text().orEmpty() }
                    else
                        a.selectFirst("div:not([class]):last-child")?.text().orEmpty().ifBlank {
                            img?.attr("alt").orEmpty()
                        }
                if (title.isBlank()) null
                else
                    Manga(
                        sourceId(key, a.attr("abs:href").removePrefix(base)),
                        title,
                        "",
                        img?.absUrl("src").orEmpty().ifBlank {
                            a.selectFirst("source")?.absUrl("srcset").orEmpty().substringBefore(' ')
                        },
                    )
            }
            .distinctBy { it.id }

    suspend fun details(manga: Manga): Manga {
        val d = document(sourcePath(manga.id))
        val description =
            if (key == "demonic") d.selectFirst(".genres-list ~ .white-font")?.text()
            else d.selectFirst("li:has(strong:contains(Description)) > p")?.text()
        return manga.copy(description = description ?: manga.description)
    }

    suspend fun chapters(id: String): List<Chapter> {
        val path = sourcePath(id)
        val d =
            document(
                if (key == "demonic") path
                else
                    "/series/${path.substringAfter("/series/").substringBefore('/')}/full-chapter-list"
            )
        val links =
            d.select(if (key == "demonic") "a.chplinks" else "div[x-data] > a[href*=/chapters/]")
        if (links.isEmpty())
            throw IOException(
                "No chapter links returned by ${sourceNames[key]}. The source may have changed; your saved list is retained."
            )
        return links
            .map { a ->
                val url = a.absUrl("href")
                val title =
                    if (key == "demonic") a.text()
                    else a.selectFirst("span.flex > span")?.text() ?: a.text()
                val number =
                    if (key == "demonic") url.toHttpUrl().queryParameter("chapter")
                    else
                        Regex("(?i)(?:chapter|ch\\.)\\s*(\\d+(?:\\.\\d+)?)")
                            .find(title)
                            ?.groupValues
                            ?.get(1)
                chapter(sourceId(key, url.removePrefix(base)), id, title, number)
            }
            .distinctBy { it.id }
    }

    suspend fun pages(id: String): List<String> {
        val path = sourcePath(id)
        val d =
            document(
                if (key == "demonic") path
                else "$path/images?is_prev=False&reading_style=long_strip"
            )
        return parsePages(d).ifEmpty {
            throw IOException(
                "No reader images returned by ${sourceNames[key]}. Please retry later."
            )
        }
    }

    fun parsePages(d: Document): List<String> =
        d.select(if (key == "demonic") "img.imgholder[alt]" else "section[x-data~=scroll] > img")
            .map { it.absUrl("src") }
            .filter { it.startsWith("https://") }
            .distinct()

    private fun chapter(id: String, manga: String, title: String, number: String?): Chapter {
        val attributes =
            JSONObject()
                .put("title", title)
                .put("chapter", number)
                .put("translatedLanguage", "en")
                .put("pages", -1)
        val relationships = JSONArray().put(JSONObject().put("type", "manga").put("id", manga))
        return Chapter(
            id,
            manga,
            JSONObject()
                .put("id", id)
                .put("attributes", attributes)
                .put("relationships", relationships)
                .toString(),
        )
    }
}
